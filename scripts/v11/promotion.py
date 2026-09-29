"""Pinned V10 -> V11 plan/migrate/serve/rollback entry point. Original writes require an explicit flag."""
import argparse, json, os, shutil, subprocess, sys, time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cutover'))
import cutover as c

def new_json(path,value):
    with Path(path).open('x',encoding='utf-8') as f:json.dump(value,f,ensure_ascii=False,indent=2)
def original(db):return '/.local-data/' in db.resolve().as_posix().lower()
def pinned(path,digest):
    path=Path(path).resolve(strict=True);c.require(c.digest(path)==digest.lower(),'pinned artifact differs: '+str(path));return path
def environment():
    env=c.clean_env()
    for key in env:
        c.require(not key.startswith('SPRING_') or key in ('SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD'),'unreviewed Spring override: '+key)
        c.require(not key.startswith('AICA_V11_'),'promotion flags must come from this command only')
    return env
def tool(java,rc,mode,source,output,plan_hash=None,authorize=False):
    env=environment()
    if plan_hash:env.update(AICA_V11_PROMOTION_ENABLED='true',AICA_V11_APPROVAL_SHA256=plan_hash)
    if authorize:env['AICA_V11_AUTHORIZE_ORIGINAL']='true'
    c.run_cmd([java,'-Dfile.encoding=UTF-8','-Dloader.main=egovframework.backoffice.mvp.operations.V11PromotionTool','-cp',rc,'org.springframework.boot.loader.launch.PropertiesLauncher',mode,source,output],str(output)+'.log',env)
    return c.load(output)
def bundle(manifest_path):
    manifest=c.load(manifest_path);backup=Path(manifest_path).resolve().parent/'backup'
    for relative,digest in manifest['files'].items():
        file=(backup/relative).resolve();c.require(file.is_relative_to(backup.resolve()),'unsafe backup entry');pinned(file,digest)
    return backup,manifest
def command(a):
    java=Path(a.java).resolve(strict=True);rc=pinned(a.rc,a.rc_sha256);environment()
    if a.command=='plan':
        db=Path(a.db).resolve(strict=True);c.exclusive(db);tool(java,rc,'plan',db,Path(a.output).resolve());return
    if a.command=='migrate':
        plan=pinned(a.plan,a.approved_plan_sha);p=c.load(plan);db=Path(p['databasePath']).resolve();c.exclusive(db)
        c.require(not original(db) or a.authorize_original,'original promotion has not been authorized')
        backup_manifest=pinned(a.backup_manifest,a.backup_manifest_sha256);backup,b=bundle(backup_manifest)
        c.require(c.digest(db)==c.digest(backup/'v10.mv.db')==b['sourceDatabaseSha256']==p['before']['sha256'],'backup is not the exact approved target baseline')
        root=Path(a.run_dir).resolve();root.mkdir(parents=True,exist_ok=False)
        shutil.copy2(plan,root/'approved-plan.json');shutil.copy2(backup_manifest,root/'backup-manifest.json')
        receipt=tool(java,rc,'migrate',plan,root/'runtime-receipt.json',a.approved_plan_sha,a.authorize_original)
        c.require(receipt['status']=='MIGRATED_V11' and receipt['migrationsExecuted']==1 and receipt['legacyDataPreserved'],'incomplete migration receipt')
        info=dict(kind='V10_TO_V11',target=str(db),rc=str(rc),rcSha256=c.digest(rc),planSha256=c.digest(plan),receiptSha256=c.digest(root/'runtime-receipt.json'),backupManifest=str(backup_manifest),backupManifestSha256=c.digest(backup_manifest),toolSha256=c.digest(__file__),supportTools={p.name:c.digest(p) for p in c.HERE.glob('*') if p.suffix in ('.py','.java')})
        new_json(root/'input.json',info);new_json(root/'result.json',dict(success=True,rollback=False,inputSha256=c.digest(root/'input.json'),original=original(db),migrationsExecuted=1,writingReopened=False));print('PASS V11 migration and receipt:',root);return
    root=Path(a.run_dir).resolve();info=c.load(root/'input.json');result=c.load(root/'result.json');db=Path(info['target']).resolve()
    c.require(result['success'] and result['inputSha256']==c.digest(root/'input.json'),'incomplete/tampered promotion result')
    c.require(info['rcSha256']==c.digest(rc) and info['toolSha256']==c.digest(__file__),'runtime/tool changed since promotion')
    c.require(info['supportTools']=={p.name:c.digest(p) for p in c.HERE.glob('*') if p.suffix in ('.py','.java')},'support tools changed since promotion')
    pinned(root/'runtime-receipt.json',info['receiptSha256']);pinned(root/'approved-plan.json',info['planSha256']);c.exclusive(db)
    if a.command=='serve':
        c.require(not (root/'rollback-result.json').exists(),'promotion has been rolled back')
        evidence=root/('serve-'+str(time.time_ns()));evidence.mkdir();agent=c.prepare_agent(java,evidence/'shutdown-tool')
        receipt=c.load(root/'runtime-receipt.json');c.require(receipt['databasePath']==str(db) and receipt['jarSha256']==c.digest(rc),'receipt target mismatch')
        server=c.start(java,rc,db,a.port,evidence,'normal-runtime',root/'runtime-receipt.json',agent=agent)
        print('V11 ready; validate-only; PID',server.pid,flush=True)
        try:c.require(server.wait()==0,'server did not close cleanly')
        except KeyboardInterrupt:c.stop(java,agent,server,evidence,'normal-runtime',db)
        return
    c.require(a.command=='rollback','unknown command')
    c.require(not original(db) or a.authorize_original,'original rollback has not been authorized')
    backup_manifest=pinned(info['backupManifest'],info['backupManifestSha256']);backup,b=bundle(backup_manifest)
    evidence=root/('rollback-'+str(time.time_ns()));evidence.mkdir()
    # Verify backup before touching target; preserve failed state for recovery of post-release writes.
    checked=c.readonly(java,backup/'v10-rc.jar',backup/'v10.mv.db',evidence/'backup-verified.json')
    plan=c.load(root/'approved-plan.json')
    for key in ('sha256','history','columns','fingerprints'):c.require(checked[key]==plan['before'][key],'rollback baseline differs: '+key)
    shutil.copy2(db,evidence/'preserved-v11.mv.db');shutil.copy2(rc,evidence/'preserved-v11.jar');shutil.copy2(root/'runtime-receipt.json',evidence/'preserved-v11-receipt.json')
    c.require(db.name.endswith('.mv.db') and db.is_absolute(),'invalid exact rollback path');shutil.copy2(backup/'v10.mv.db',db)
    restored=c.readonly(java,backup/'v10-rc.jar',db,evidence/'restored-v10-cold.json')
    for key in ('sha256','history','columns','fingerprints'):c.require(restored[key]==checked[key],'restoration differs: '+key)
    new_json(root/'rollback-result.json',dict(success=True,databasePath=str(db),restoredDatabaseSha256=restored['sha256'],runtime=str(backup/'v10-rc.jar'),runtimeSha256=c.digest(backup/'v10-rc.jar'),uiAssets=str(backup/'ui-assets'),v10RunDir=str(backup/'run'),writingReopened=False,requiresV10Startup=True))
    print('PASS V10 DB restored; activate the verified V10 JAR/UI/config bundle before reopening writes')
def main():
    p=argparse.ArgumentParser();sub=p.add_subparsers(dest='command',required=True)
    for mode in ('plan','migrate','serve','rollback'):
        cmd=sub.add_parser(mode)
        for name in ('java','rc','rc-sha256'):cmd.add_argument('--'+name,required=True)
        if mode=='plan':
            cmd.add_argument('--db',required=True);cmd.add_argument('--output',required=True)
        else:cmd.add_argument('--run-dir',required=True)
        if mode=='migrate':
            for name in ('plan','approved-plan-sha','backup-manifest','backup-manifest-sha256'):cmd.add_argument('--'+name,required=True)
        if mode in ('migrate','rollback'):cmd.add_argument('--authorize-original',action='store_true')
        if mode=='serve':cmd.add_argument('--port',type=int,default=8095)
    command(p.parse_args())
if __name__=='__main__':main()
