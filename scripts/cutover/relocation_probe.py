"""Disposable Windows relocation acceptance/negative checks. Never targets a live DB.

Uses an already cold V10 reference and approved RC; only malformed negative fixtures
have their Flyway history edited. Normal A/B approvals/serve cycles perform GETs only.
"""
import argparse
import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

import cutover as c
import relocation as rel
from http_client import Client


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', required=True, help='pinned relocate-plan arguments in JSON')
    parser.add_argument('--source-db', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--port', type=int, default=18095)
    a = parser.parse_args()
    root = Path(a.output).resolve(); c.require(not root.exists(), 'fresh test directory required')
    c.require('/.local-data/' not in root.as_posix().lower(), 'isolated test directory required')
    config = c.load(a.config); java = Path(config['java']); rc = Path(config['rc'])
    source = Path(a.source_db).resolve(); c.exclusive(source)
    reference = c.load(config['source_inspection']); c.require(c.digest(source) == reference['sha256'], 'source DB mismatch')
    root.mkdir(parents=True); results = []; tool = c.HERE/'cutover.py'
    env = rel.safe_env(); c.require(os.environ.get('AICA_SMOKE_USER') and os.environ.get('AICA_SMOKE_PASSWORD'), 'read-only login credentials required')

    def call(label, args, success=True, runenv=None):
        with (root/(label+'.log')).open('w', encoding='utf-8') as log:
            proc = subprocess.run([str(x) for x in args], stdout=log, stderr=subprocess.STDOUT,
                                  env=runenv or env, timeout=150, creationflags=c.NO_WINDOW)
        c.require((proc.returncode == 0) == success, label+' unexpected exit; see '+str(root/(label+'.log')))
        return root/(label+'.log')

    def plan_args(db, out, overrides=None):
        options = config | (overrides or {})
        args = [sys.executable, '-B', tool, 'relocate-plan']
        for key, value in options.items(): args += ['--'+key.replace('_','-'), value]
        return args + ['--db', db, '--output', out]

    def reject(label, args, db, runenv=None):
        before = c.digest(db)
        call(label, args, False, runenv)
        c.exclusive(db); c.require(c.digest(db) == before, label+' changed DB')
        results.append(dict(case=label, rejected=True, bytesUnchanged=True))

    def approve_at(name):
        folder = root/name; folder.mkdir(); db = folder/'candidate.mv.db'; shutil.copy2(source, db)
        plan = folder/'plan.json'; run = folder/'run'
        call(name+'-plan', plan_args(db, plan))
        call(name+'-approve', [sys.executable, '-B', tool, 'relocate-approve', '--java', java, '--plan', plan,
                              '--approved-plan-sha', c.digest(plan), '--run-dir', run, '--authorize-relocation'])
        c.require(c.digest(db) == reference['sha256'], name+' approval wrote DB')
        results.append(dict(case=name+'-approval', bytesUnchanged=True, migration=0, businessWrites=False))
        return db, run, plan

    db_a, run_a, plan_a = approve_at('path-A')
    db_b, run_b, plan_b = approve_at('different-path-B')

    def serve_cycles(db, run, label):
        previous = None
        for cycle in (1, 2):
            proc = subprocess.Popen([str(x) for x in [sys.executable, '-B', tool, 'serve', '--java', java, '--run-dir', run, '--port', a.port]],
                                    env=env, stdout=(root/f'{label}-serve-{cycle}.log').open('w',encoding='utf-8'), stderr=subprocess.STDOUT, creationflags=c.NO_WINDOW)
            try:
                for _ in range(100):
                    c.require(proc.poll() is None, 'serve failed: '+label)
                    try:
                        with urllib.request.urlopen(f'http://127.0.0.1:{a.port}/login', timeout=1) as response:
                            if response.status == 200 and (run/'activation.json').exists(): break
                    except Exception: pass
                    time.sleep(.2)
                else: raise RuntimeError('server timeout')
                client = Client(a.port)
                for path in ['/admin-next?view=manage','/admin-next?view=structure']:
                    c.require('/login' not in client.req(path).get('url',''), 'login redirect')
                proof = dict(pages=[client.req('/api/admin/next/pages/'+str(id)) for id in (1,65)],
                             posts=client.req('/api/admin/next/posts'), versions=client.req('/api/admin/next/pages/65/versions'),
                             public=[client.public('/menus'),client.public('/pages/1'),client.public('/pages/65'),client.public('/posts')])
                if previous is not None: c.require(previous == proof, 'read state changed after restart')
                previous = proof
                evidence = sorted(run.glob('serve-*'))[-1]
                metadata = c.load(evidence/'normal-runtime-process.json')
                c.require(not metadata['cutoverFlag'] and metadata['jarSha256'] == config['rc_sha256'], 'wrong runtime mode/hash')
                log = (evidence/'normal-runtime.log').read_text('utf-8')
                c.require('migration=validate-only' in log and 'AUTO_COMPACT_FILL_RATE=0' in log, 'runtime guard log missing')
            finally:
                candidates = sorted(run.glob('serve-*/normal-runtime-process.json'))
                if candidates:
                    metadata = c.load(candidates[-1]); agent = run/'shutdown-tool/graceful-stop.jar'
                    call(f'{label}-stop-{cycle}', [java,'--add-modules','jdk.attach','-cp',agent,'GracefulStop',metadata['pid'],agent])
                proc.wait(timeout=60)
            c.require(proc.returncode == 0, 'serve wrapper failed')
            c.exclusive(db)
            final_log = candidates[-1].parent/'normal-runtime.log'
            c.require('Shutdown completed' in final_log.read_text('utf-8'), 'Hikari shutdown absent')
            cold = c.readonly(java,rc,db,root/f'{label}-cold-{cycle}.json')
            for key in ('history','columns','fingerprints'):c.require(cold[key] == reference[key], label+' changed '+key)
            results.append(dict(case=f'{label}-serve-{cycle}', login=True, bothViews=True, publicGET=True,
                                validateOnly=True, migrations=0, hikariShutdown=True, lockReleased=True, fingerprintsUnchanged=True))
        return proof

    serve_cycles(db_a, run_a, 'A'); serve_cycles(db_b, run_b, 'B')

    # These invoke the frozen RC directly to exercise its unchanged guards, without a copy-validation bypass.
    def runtime(db, receipt, options=()):
        return [java, '-Dfile.encoding=UTF-8','-jar',rc,'--spring.profiles.active=dev','--server.port=0',
                '--backoffice.bootstrap.enabled=false','--spring.datasource.url='+c.jdbc(db), '--AICA_RUNTIME_RECEIPT='+str(receipt), *options]
    reject('A-receipt-on-B',runtime(db_b,run_a/'runtime-receipt.json'),db_b)
    changed = c.load(run_a/'runtime-receipt.json'); changed['jarSha256'] = '0'*64
    bad_receipt = root/'bad-rc-receipt.json'; c.save(bad_receipt,changed)
    reject('runtime-wrong-rc-hash',runtime(db_a,bad_receipt),db_a)
    reject('missing-workaround',runtime(db_a,run_a/'runtime-receipt.json',['--spring.datasource.url='+c.jdbc(db_a).replace(';AUTO_COMPACT_FILL_RATE=0','')]),db_a)
    reject('web-cutover-flag',runtime(db_a,run_a/'runtime-receipt.json',['--AICA_CUTOVER_ENABLED=true']),db_a)

    # Plan/approval negative cases are deliberately new copies, never the source or original.
    negative = root/'negative'; negative.mkdir(); clean = negative/'candidate.mv.db'; shutil.copy2(source,clean)
    reject('unapproved-rc',plan_args(clean,negative/'bad-rc-plan.json',{'rc_sha256':'0'*64}),clean)
    for key in ('inspection','receipt','releaseManifest'):
        original = config[{'inspection':'source_inspection','receipt':'source_receipt','releaseManifest':'release_manifest'}[key]]
        data = copy.deepcopy(c.load(original))
        if key == 'inspection': data['fingerprints']['POSTS']['sha256'] = '0'*64
        elif key == 'receipt': data['migrations'][0]['checksum'] = 0
        else: data['v10-rc.jar'] = '0'*64
        file = negative/(key+'.json'); c.save(file,data)
        field = {'inspection':'source_inspection','receipt':'source_receipt','releaseManifest':'release_manifest'}[key]
        reject('bad-source-'+key,plan_args(clean,negative/(key+'-plan.json'),{field:str(file),field+'_sha256':c.digest(file)}),clean)
    flag_env = env.copy(); flag_env['AICA_CUTOVER_ENABLED']='true'
    reject('approval-cutover-flag',plan_args(clean,negative/'flag-plan.json'),clean,flag_env)
    reject('spent-approval',[sys.executable,'-B',tool,'relocate-approve','--java',java,'--plan',plan_a,
                           '--approved-plan-sha',c.digest(plan_a),'--run-dir',negative/'spent-run','--authorize-relocation'],clean)
    no_auth_plan = negative/'no-auth.json'; c.save(no_auth_plan,c.load(plan_b))
    reject('missing-relocation-authorization',[sys.executable,'-B',tool,'relocate-approve','--java',java,'--plan',no_auth_plan,
                           '--approved-plan-sha',c.digest(no_auth_plan),'--run-dir',negative/'no-auth-run'],clean)
    reject('relocation-v3-rollback',[sys.executable,'-B',tool,'rollback','--java',java,'--run-dir',run_b],db_b)
    v3_plan = negative/'v3-plan.json'
    c.save(v3_plan, {'format':1,'databasePath':str(clean),'jarSha256':config['rc_sha256']})
    reject('v3-plan-as-relocation',[sys.executable,'-B',tool,'relocate-approve','--java',java,'--plan',v3_plan,
                                  '--approved-plan-sha',c.digest(v3_plan),'--run-dir',negative/'v3-plan-run','--authorize-relocation'],clean)
    expired=c.load(plan_b);expired['expiresAt']='2000-01-01T00:00:00+00:00'
    expired_plan=negative/'expired.json';c.save(expired_plan,expired)
    reject('expired-relocation',[sys.executable,'-B',tool,'relocate-approve','--java',java,'--plan',expired_plan,
                                '--approved-plan-sha',c.digest(expired_plan),'--run-dir',negative/'expired-run','--authorize-relocation'],clean)

    # Bind approval bytes before first serve, but permit ordinary DB evolution after activation.
    db_c,run_c,plan_c=approve_at('first-use-C')
    changed_before=c.load(run_c/'approval-inspection.json'); c.require(changed_before['sha256']==c.digest(db_c),'approval hash mismatch')
    # Replace only this disposable candidate with a different valid V10 cold file.
    c.exclusive(db_c);shutil.copy2(db_a,db_c)
    if c.digest(db_c)!=changed_before['sha256']:
        reject('changed-before-first-serve',[sys.executable,'-B',tool,'serve','--java',java,'--run-dir',run_c,'--port',a.port],db_c)
    else:
        raise RuntimeError('test requires a changed physical file after normal A startup')

    # Negative-only history mutation, using the same approved H2 library and workaround.
    helper = run_a/'inspection-tool/relocation-inspect.jar'
    h2 = run_a/'inspection-tool/rc-libs/h2-2.3.232.jar'
    for name, sql in [('v9','DELETE FROM "flyway_schema_history" WHERE "version"=\'10\''),
                      ('checksum','UPDATE "flyway_schema_history" SET "checksum"=0 WHERE "version"=\'10\'')]:
        db = negative/(name+'.mv.db'); shutil.copy2(source,db)
        call(name+'-fixture',[java,'-cp',h2,'org.h2.tools.Shell','-url',c.jdbc(db),'-user',os.environ.get('AICA_DB_USER','sa'),'-sql',sql])
        reject(name+'-plan',plan_args(db,negative/(name+'-plan.json')),db)
        out = negative/(name+'-actual-inspection.json')
        before=c.digest(db)
        try: rel.inspect_v10(java,rc,db,out,helper)
        except RuntimeError: pass
        else: raise RuntimeError(name+' inspector unexpectedly accepted')
        c.require(c.digest(db)==before,'rejected inspector changed fixture')
        results.append(dict(case=name+'-actual-validator',rejected=True,bytesUnchanged=True))
    future_rc=negative/'future-migration.jar';shutil.copy2(rc,future_rc)
    with zipfile.ZipFile(future_rc,'a') as archive:archive.writestr('BOOT-INF/classes/db/migration/h2/V11__negative_probe.sql','-- must never execute\nSELECT 1;')
    before=c.digest(clean)
    try:rel.inspect_v10(java,future_rc,clean,negative/'pending.json',helper)
    except RuntimeError:pass
    else:raise RuntimeError('pending migration accepted')
    c.require(c.digest(clean)==before,'pending inspection changed DB')
    results.append(dict(case='pending-migration',rejected=True,bytesUnchanged=True))
    c.require(c.digest(source)==reference['sha256'],'source changed')
    c.save(root/'results.json',dict(success=True,cases=results,approvedRcSha256=c.digest(rc),sourceUnchanged=True,
                                   actualWebsiteE2E=False,normalBusinessWrites=False))
    print('PASS relocation acceptance/negative cases:',len(results),flush=True)


if __name__ == '__main__':main()
