"""V10 cutover and rollback entry point. Rehearsal and execution use identical guards.

No default DB or credentials. Commands require explicit paths and a pinned approval hash.
All fixture writes stay in a separate smoke fork; the target only receives baseline and
same-value MANUAL_DRAFT saves (new version rows, no published content changes).
"""
import argparse, ctypes, hashlib, json, os, shutil, subprocess, sys, time, urllib.request, zipfile
from pathlib import Path
from http_client import Client

HERE = Path(__file__).resolve().parent
NO_WINDOW = getattr(subprocess, 'CREATE_NO_WINDOW', 0)

def require(ok, message):
    if not ok: raise RuntimeError('STOP: ' + message)

def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()

def save(path, data):
    Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')

def load(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def exclusive(path):
    """No H2 connection: check Windows sharing/lock release before copying/restoring."""
    require(os.name=='nt','this runbook is validated for Windows only')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.CreateFileW.restype=ctypes.c_void_p
    kernel.CreateFileW.argtypes=[ctypes.c_wchar_p,ctypes.c_uint32,ctypes.c_uint32,ctypes.c_void_p,ctypes.c_uint32,ctypes.c_uint32,ctypes.c_void_p]
    handle=kernel.CreateFileW(str(Path(path).resolve()),0x80000000,0,None,3,0,None)
    require(handle!=ctypes.c_void_p(-1).value,'database has an open handle, or cannot be exclusively read: '+str(path))
    kernel.CloseHandle.argtypes=[ctypes.c_void_p];kernel.CloseHandle(handle)

def clean_env():
    e=os.environ.copy()
    for key in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','LOADER_PATH','LOADER_HOME','LOADER_MAIN']:
        require(not e.get(key),'unreviewed JVM/loader environment override: '+key)
    e.pop('AICA_CUTOVER_ENABLED',None);e.pop('AICA_CUTOVER_APPROVAL_SHA256',None)
    return e

def run_cmd(args, log, env=None, timeout=180):
    with Path(log).open('w',encoding='utf-8') as out:
        result=subprocess.run([str(x) for x in args],env=env or clean_env(),stdout=out,stderr=subprocess.STDOUT,timeout=timeout,creationflags=NO_WINDOW)
    require(result.returncode==0,'command failed; inspect '+str(log))

def tool(java, jar, mode, source, output, approval=None):
    env=clean_env()
    if approval:
        env.update(AICA_CUTOVER_ENABLED='true',AICA_CUTOVER_APPROVAL_SHA256=approval)
    run_cmd([java,'-Dfile.encoding=UTF-8','-Dloader.main=egovframework.backoffice.mvp.operations.CutoverTool','-cp',jar,
             'org.springframework.boot.loader.launch.PropertiesLauncher',mode,source,output],str(output)+'.log',env)
    return load(output)

def jdbc(db,v10=True):
    db=Path(db).resolve();require(db.name.endswith('.mv.db') and ';' not in str(db),'invalid DB filename')
    # The preserved V3 JAR uses the same explicit URL that passed 5C-1B rollback.
    return 'jdbc:h2:file:'+db.as_posix()[:-6]+';IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0'

def prepare_agent(java, output):
    output.mkdir();jdk=java.parent
    run_cmd([jdk/'javac.exe','--add-modules','jdk.attach','-d',output,HERE/'GracefulStop.java'],output/'compile.log')
    manifest=output/'agent.mf';manifest.write_text('Manifest-Version: 1.0\nAgent-Class: GracefulStop\nMain-Class: GracefulStop\n\n',encoding='ascii')
    run_cmd([jdk/'jar.exe','--create','--file',output/'graceful-stop.jar','--manifest',manifest,'-C',output,'GracefulStop.class'],output/'jar.log')
    return output/'graceful-stop.jar'

def start(java,jar,db,port,evidence,label,receipt=None,v10=True,copy=False,agent=None):
    env=clean_env()
    env['SPRING_DATASOURCE_USERNAME']=env.get('AICA_DB_USER','sa')
    env['SPRING_DATASOURCE_PASSWORD']=env.get('AICA_DB_PASSWORD','')
    args=[str(java),'-Dfile.encoding=UTF-8','-jar',str(jar),'--spring.profiles.active=dev','--server.address=127.0.0.1',
          f'--server.port={port}',f'--spring.datasource.url={jdbc(db,v10)}','--backoffice.bootstrap.enabled=false']
    if receipt: args.append('--AICA_RUNTIME_RECEIPT='+str(receipt))
    if copy: args.append('--backoffice.classification-migration.copy-validation=true')
    output=(evidence/(label+'.log')).open('w',encoding='utf-8')
    process=subprocess.Popen(args,env=env,stdout=output,stderr=subprocess.STDOUT,creationflags=NO_WINDOW)
    output.close()
    save(evidence/(label+'-process.json'),dict(pid=process.pid,profile='dev',database=str(db),jdbcUrl=jdbc(db,v10),jar=str(jar),jarSha256=digest(jar),cutoverFlag=False))
    for _ in range(180):
        require(process.poll() is None,'server stopped during startup: '+label)
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/login',timeout=1) as response:
                if response.status==200:return process
        except Exception: pass
        time.sleep(.5)
    if agent: stop(java,agent,process,evidence,label+'-timeout',db)
    raise RuntimeError('STOP: server readiness timeout; PID '+str(process.pid))

def stop(java, agent, process, evidence, label, db):
    require(process.poll() is None,'server exited unexpectedly before graceful stop')
    run_cmd([java,'--add-modules','jdk.attach','-cp',agent,'GracefulStop',str(process.pid),agent],evidence/(label+'-stop.log'))
    process.wait(timeout=60);require(process.returncode==0,'server did not exit cleanly')
    exclusive(db)
    save(evidence/(label+'-closed.json'),dict(pid=process.pid,exitCode=process.returncode,exclusiveFileAccess=True,sha256=digest(db),size=db.stat().st_size,mtimeNs=db.stat().st_mtime_ns))

def readonly(java,rc,db,path):
    exclusive(db);return tool(java,rc,'inspect',db,path)

def read_smoke(port):
    c=Client(port)
    require(c.bootstrap['user']['role']=='SUPER_ADMIN','cutover operator must be SUPER_ADMIN')
    for path in ['/admin','/admin/posts','/admin/pages','/admin-next?view=manage','/admin-next?view=structure']:
        require('/login' not in c.req(path).get('url',''),'login/read failure')
    for id in [1,65]: c.req(f'/api/admin/next/pages/{id}')
    return c

def rollback(java,rc,v3,db,backup,evidence,agent,port):
    exclusive(db);require(digest(backup/'v3-runtime.jar')==digest(v3),'rollback JAR mismatch')
    expected=load(backup/'v3-inspection.json')
    verified=readonly(java,rc,backup/'v3.mv.db',evidence/'rollback-backup-verified.json')
    require(verified['sha256']==expected['sha256'] and verified['fingerprints']==expected['fingerprints']
            and verified['history']==expected['history'] and verified['history'][-1]['version']=='3',
            'V3 backup verification failed; target has not been replaced')
    failed=evidence.parent/'v10-preserved';failed.mkdir()
    shutil.copy2(db,failed/db.name);shutil.copy2(rc,failed/'v10-rc.jar')
    save(failed/'hashes.json',{'db':digest(db),'rc':digest(rc)})
    # Only the explicitly approved target is replaced, never a computed directory tree.
    require(db.is_absolute() and db.suffix=='.db','invalid rollback target')
    shutil.copy2(backup/'v3.mv.db',db)
    active=evidence.parent/'active-rollback';active.mkdir();shutil.copy2(backup/'v3-runtime.jar',active/'v3-runtime.jar')
    before=readonly(java,rc,db,evidence/'rollback-before.json')
    require(before['sha256']==expected['sha256'] and before['fingerprints']==expected['fingerprints'],'rollback backup mismatch')
    server=start(java,active/'v3-runtime.jar',db,port,evidence,'rollback-v3',v10=False,agent=agent)
    try: read_smoke(port)
    finally: stop(java,agent,server,evidence,'rollback-v3',db)
    after=readonly(java,rc,db,evidence/'rollback-after.json')
    require(before['history']==after['history'] and before['fingerprints']==after['fingerprints'],'V3 rollback data changed')
    save(evidence/'rollback-result.json',dict(success=True,v3RuntimeSha256=digest(active/'v3-runtime.jar'),allTablesPreserved=True,version=3))

def execute(a):
    java=Path(a.java).resolve();rc=Path(a.rc).resolve();plan=Path(a.plan).resolve();v3=Path(a.v3_runtime).resolve()
    require(digest(plan)==a.approved_plan_sha.lower(),'approval file checksum differs')
    p=load(plan);db=Path(p['databasePath']).resolve()
    require(digest(rc)==p['jarSha256'],'RC checksum differs')
    require(digest(v3)==a.v3_sha256.lower(),'V3 rollback runtime checksum differs')
    is_original='/.local-data/' in db.as_posix().lower()
    require(not is_original or a.authorize_original_cutover,'original cutover requires separate explicit authorization')
    require(not (is_original and a.rollback_rehearsal),'rehearsal rollback cannot target the original')
    require(a.port!=a.smoke_port and a.restarts>=4,'two distinct ports and at least four cycles required')
    require('AICA_SMOKE_USER' in os.environ and 'AICA_SMOKE_PASSWORD' in os.environ,'operator credentials must be supplied in environment')
    root=Path(a.run_dir).resolve();require(not root.exists(),'run directory must be new');root.mkdir(parents=True)
    evidence=root/'evidence';backup=root/'backup';evidence.mkdir();backup.mkdir()
    save(root/'input.json',dict(target=str(db),plan=str(plan),planSha256=digest(plan),rc=str(rc),rcSha256=digest(rc),v3=str(v3),v3Sha256=digest(v3),dictionaryMode='DEFER',original=is_original))
    exclusive(db);require(digest(db)==p['database']['sha256'],'target changed after approval')
    shutil.copy2(db,backup/'v3.mv.db');shutil.copy2(v3,backup/'v3-runtime.jar')
    readonly(java,rc,backup/'v3.mv.db',backup/'v3-inspection.json')
    verified_backup=load(backup/'v3-inspection.json')
    require(verified_backup['fingerprints']==p['database']['fingerprints'] and verified_backup['sha256']==p['database']['sha256'],'backup fingerprint/hash differs')
    print('PASS fresh V3 DB + matching runtime backup',flush=True)
    last=root/'last-copy.mv.db';shutil.copy2(backup/'v3.mv.db',last)
    tool(java,rc,'plan',last,root/'last-copy-plan.json')
    tool(java,rc,'migrate',root/'last-copy-plan.json',root/'last-copy-receipt.json',digest(root/'last-copy-plan.json'))
    readonly(java,rc,last,evidence/'last-copy-cold.json')
    print('PASS identical backup last-copy guarded V3 -> V10',flush=True)
    # Recheck source immediately before the exact same migration path on the approved target.
    exclusive(db);require(digest(db)==p['database']['sha256'],'target changed during final copy verification')
    receipt=root/'runtime-receipt.json';tool(java,rc,'migrate',plan,receipt,a.approved_plan_sha.lower())
    after=readonly(java,rc,db,evidence/'target-migrated-cold.json');require(after['history'][-1]['version']=='10','V10 missing')
    require(after['fingerprints']['TOPICS']['rows']==0 and after['fingerprints']['COHORTS']['rows']==0,'unapproved dictionary seed')
    print('PASS target guarded V3 -> V10; dictionary DEFER',flush=True)
    agent=prepare_agent(java,root/'shutdown-tool');expected=None;public=None
    for cycle in range(1,a.restarts+2):
        server=start(java,rc,db,a.port,evidence,f'cycle-{cycle}',receipt,agent=agent)
        try:
            c=read_smoke(a.port)
            if cycle==1:
                first=c.req('/api/admin/next/version-baseline',{'confirmed':True},'POST')
                repeat=c.req('/api/admin/next/version-baseline',{'confirmed':True},'POST');require(all(n==0 for n in repeat.values()),'baseline not idempotent')
                save(evidence/'baseline.json',dict(first=first,repeat=repeat));public=c.public('/pages/65')
            current=c.req('/api/admin/next/pages/65');versions=c.req('/api/admin/next/pages/65/versions')
            if expected: require(current==expected['page'] and versions==expected['versions'],'latest save/history lost after restart')
            require(c.public('/pages/65')==public,'public snapshot changed')
            if cycle<=a.restarts:
                body={k:current[k] for k in ['revision','title','sections']};body['saveIntent']='MANUAL_DRAFT'
                current=c.req('/api/admin/next/pages/65',body,'PUT')
                next_versions=c.req('/api/admin/next/pages/65/versions');require(next_versions['total']==versions['total']+1,'manual save did not persist a new version')
                expected=dict(page=current,versions=next_versions)
            save(evidence/f'cycle-{cycle}-api.json',dict(page=current,versions=c.req('/api/admin/next/pages/65/versions'),public=c.public('/pages/65')))
        finally: stop(java,agent,server,evidence,f'cycle-{cycle}',db)
        cold=readonly(java,rc,db,evidence/f'cycle-{cycle}-cold.json');require(cold['history'][-1]['version']=='10','cold version is not V10')
        print(f'PASS cycle {cycle}: same file, V10, latest version retained, lock released',flush=True)
    # Full write/publish/role smoke is always isolated from the target, even at actual cutover.
    smoke=root/'smoke';smoke.mkdir();smoke_db=smoke/'fixture.mv.db';shutil.copy2(db,smoke_db)
    smoke_env=clean_env();smoke_env.update(AICA_ISOLATED_SMOKE='true',AICA_SMOKE_PORT=str(a.smoke_port))
    with zipfile.ZipFile(rc) as archive:
        libraries=[n for n in archive.namelist() if n.startswith('BOOT-INF/lib/h2-') and n.endswith('.jar')]
        require(libraries==['BOOT-INF/lib/h2-2.3.232.jar'],'unapproved H2 dependency')
        h2=smoke/'h2-2.3.232.jar';h2.write_bytes(archive.read(libraries[0]))
    run_cmd([java.parent/'javac.exe','-encoding','UTF-8','-cp',h2,'-d',smoke,HERE/'SmokeDictionary.java'],evidence/'smoke-dictionary-compile.log')
    run_cmd([java,'-Dfile.encoding=UTF-8','-cp',str(smoke)+os.pathsep+str(h2),'SmokeDictionary',smoke_db,HERE/'smoke-dictionary.sql'],evidence/'smoke-dictionary.log',smoke_env)
    server=start(java,rc,smoke_db,a.smoke_port,evidence,'smoke',copy=True,agent=agent)
    try:
        run_cmd([sys.executable,'-X','utf8',HERE/'cms_smoke.py',evidence/'cms-smoke.json'],evidence/'cms-smoke.log',smoke_env)
        run_cmd([sys.executable,'-X','utf8',HERE/'roles_smoke.py',evidence/'roles-smoke.json'],evidence/'roles-smoke.log',smoke_env)
        c=read_smoke(a.smoke_port);proof=load(evidence/'cms-smoke.json')
        # Roles smoke legitimately updates page 65; capture the final state for restart equality.
        proof['restartExpected']={path:c.req(path) for path in proof['restartExpected']}
        proof['restartPublic']={path:c.public(path) for path in proof['restartPublic']};save(evidence/'smoke-final-state.json',proof)
    finally: stop(java,agent,server,evidence,'smoke',smoke_db)
    readonly(java,rc,smoke_db,evidence/'smoke-cold.json')
    server=start(java,rc,smoke_db,a.smoke_port,evidence,'smoke-restart',copy=True,agent=agent)
    try:
        c=read_smoke(a.smoke_port)
        for path,expected_value in proof['restartExpected'].items(): require(c.req(path)==expected_value,'smoke restart data mismatch: '+path)
        for path,expected_value in proof['restartPublic'].items(): require(c.public(path)==expected_value,'smoke public restart mismatch: '+path)
    finally: stop(java,agent,server,evidence,'smoke-restart',smoke_db)
    print('PASS complete CMS/role/publication smoke and restart',flush=True)
    if a.rollback_rehearsal:
        rollback(java,rc,v3,db,backup,evidence,agent,a.port)
        print('PASS V10 preserved -> V3 DB + V3 runtime restored -> login/read/fingerprints',flush=True)
    save(root/'result.json',dict(success=True,cycles=a.restarts+1,writes=a.restarts,original=is_original,rollback=a.rollback_rehearsal,
                               dictionaryMode='DEFER',writingReopened=False,receipt=str(receipt),actualWebsiteE2E=False))
    print('PASS final rehearsal/cutover checks. Writing remains CLOSED; operator resumes explicitly.',flush=True)

def operate(a):
    root=Path(a.run_dir).resolve();info=load(root/'input.json');java=Path(a.java).resolve()
    db=Path(info['target']);rc=Path(info['rc']);v3=Path(info['v3'])
    require(digest(rc)==info['rcSha256'] and digest(v3)==info['v3Sha256'],'runtime checksum mismatch')
    evidence=root/(a.command+'-'+str(time.time_ns()));evidence.mkdir()
    agent=root/'shutdown-tool/graceful-stop.jar'
    require(agent.exists(),'verified shutdown tool missing')
    if a.command=='rollback':
        require(not info['original'] or a.authorize_original_rollback,'original rollback requires explicit authorization')
        rollback(java,rc,v3,db,root/'backup',evidence,agent,a.port)
    else:
        result=load(root/'result.json');require(result['success'] and not result['rollback'],'successful V10 cutover required')
        exclusive(db)
        server=start(java,rc,db,a.port,evidence,'normal-runtime',root/'runtime-receipt.json',agent=agent)
        print(f'V10 normal runtime ready on 127.0.0.1:{a.port}; validate-only; PID {server.pid}. Ctrl+C gracefully stops.',flush=True)
        try:
            server.wait()
            require(server.returncode==0,'normal runtime exited with failure')
        except KeyboardInterrupt: stop(java,agent,server,evidence,'normal-runtime',db)

def main():
    p=argparse.ArgumentParser();s=p.add_subparsers(dest='command',required=True)
    a=s.add_parser('plan');a.add_argument('--java',required=True);a.add_argument('--rc',required=True);a.add_argument('--db',required=True);a.add_argument('--output',required=True)
    a=s.add_parser('run')
    for arg in ['java','rc','plan','approved-plan-sha','v3-runtime','v3-sha256','run-dir']: a.add_argument('--'+arg,required=True)
    a.add_argument('--port',type=int,default=8095);a.add_argument('--smoke-port',type=int,default=8096);a.add_argument('--restarts',type=int,default=4)
    a.add_argument('--authorize-original-cutover',action='store_true');a.add_argument('--rollback-rehearsal',action='store_true')
    for command in ['serve','rollback']:
        a=s.add_parser(command);a.add_argument('--java',required=True);a.add_argument('--run-dir',required=True);a.add_argument('--port',type=int,default=8095)
        if command=='rollback': a.add_argument('--authorize-original-rollback',action='store_true')
    a=p.parse_args()
    if a.command=='plan':
        exclusive(a.db);tool(Path(a.java).resolve(),Path(a.rc).resolve(),'plan',Path(a.db).resolve(),Path(a.output).resolve())
        print('Plan SHA-256:',digest(a.output),flush=True)
    elif a.command=='run': execute(a)
    else: operate(a)

if __name__=='__main__': main()
