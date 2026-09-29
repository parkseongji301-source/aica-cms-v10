"""Capture a latest clean V10 bundle using the approved V10 server; never migrate it."""
import argparse, json, os, shutil, subprocess, sys, time, urllib.request, zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cutover'))
import cutover as c

def files(root): return {p.relative_to(root).as_posix():c.digest(p) for p in sorted(root.rglob('*')) if p.is_file()}
def ready(port):
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/login',timeout=1) as r:return r.status==200
    except Exception:return False
def main():
    p=argparse.ArgumentParser();p.add_argument('--config',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--workspace',type=Path,required=True);a=p.parse_args()
    cfg=c.load(a.config);root=a.output.resolve();root.mkdir(parents=True,exist_ok=False)
    run=Path(cfg['runDir']);info=c.load(run/'input.json');db=Path(info['target']).resolve();jar=Path(info['rc']);java=Path(cfg['java']);agent=run/'shutdown-tool/graceful-stop.jar'
    c.require(c.digest(jar)==info['rcSha256'],'approved V10 JAR changed')
    c.require(not ready(cfg['port']),'running V10 must be explicitly stopped before this closed-state capture')
    c.exclusive(db)
    protected={str(path):c.digest(path) for path in [jar,a.config,a.config.with_name('start-ui.ps1'),run/'input.json',run/'result.json',run/'runtime-receipt.json',run/'relocation-plan.json',agent]}
    assets=files(Path(cfg['assetRoot']))
    before=c.readonly(java,jar,db,root/'before-normal-cycle.json')
    c.require(before['history'][-1]['version']=='10','source is not V10')
    env=c.clean_env();env['SPRING_WEB_RESOURCES_STATIC_LOCATIONS']=Path(cfg['assetRoot']).resolve().as_uri()+'/,classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/'
    with (root/'approved-serve.log').open('x',encoding='utf-8') as log:
        wrapper=subprocess.Popen([cfg['python'],'-B',cfg['tool'],'serve','--java',str(java),'--run-dir',str(run),'--port',str(cfg['port'])],env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=c.NO_WINDOW)
    runtime=None
    try:
        for _ in range(120):
            c.require(wrapper.poll() is None,'approved V10 serve stopped; inspect capture log')
            if ready(cfg['port']):break
            time.sleep(.5)
        else:raise RuntimeError('V10 readiness timed out')
        evidence=sorted(run.glob('serve-*'))[-1];runtime=c.load(evidence/'normal-runtime-process.json')
        c.require(Path(runtime['database']).resolve()==db and runtime['jarSha256']==info['rcSha256'],'started runtime target mismatch')
        # No login/business operation: GET login and existing public APIs only.
        for route in ['/login','/api/public/v1/menus','/api/public/v1/posts']:
            with urllib.request.urlopen(f"http://127.0.0.1:{cfg['port']}"+route,timeout=10) as response:c.require(response.status==200,'read-only V10 check failed')
    finally:
        if runtime and wrapper.poll() is None:
            c.run_cmd([java,'--add-modules','jdk.attach','-cp',agent,'GracefulStop',str(runtime['pid']),agent],root/'approved-stop.log')
            c.require(wrapper.wait(timeout=60)==0,'approved V10 wrapper did not exit cleanly')
    c.require(runtime is not None,'no confirmed runtime');c.exclusive(db);c.require(not ready(cfg['port']),'port still bound')
    log=(evidence/'normal-runtime.log').read_text(encoding='utf-8')
    c.require('Shutdown completed' in log,'missing Hikari normal shutdown evidence')
    after=c.readonly(java,jar,db,root/'source-cold.json');after['jarSha256']=c.digest(jar);c.save(root/'source-cold-with-runtime.json',after)
    for key in ('columns','history','fingerprints'):c.require(before[key]==after[key],'normal V10 cycle changed business/schema data: '+key)
    backup=root/'backup';backup.mkdir();shutil.copy2(db,backup/'v10.mv.db');shutil.copy2(jar,backup/'v10-rc.jar')
    shutil.copytree(run,backup/'run');shutil.copytree(Path(cfg['tool']).parent,backup/'tools');shutil.copytree(Path(cfg['assetRoot']),backup/'ui-assets')
    shutil.copy2(a.config,backup/'ui-launch.json');shutil.copy2(a.config.with_name('start-ui.ps1'),backup/'start-ui.ps1')
    shutil.copy2(info['v3'],backup/'v3-runtime.jar') # Historical relocation tool dependency, never a rollback target.
    workspace=a.workspace.resolve();tracked=subprocess.check_output(['git','ls-files','-z'],cwd=workspace).split(b'\0');untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z'],cwd=workspace).split(b'\0')
    with zipfile.ZipFile(backup/'source.zip','x',zipfile.ZIP_DEFLATED) as z:
        for raw in sorted(set(tracked+untracked)):
            if not raw:continue
            name=raw.decode('utf-8');path=(workspace/name).resolve();c.require(path.is_relative_to(workspace),'source escaped workspace')
            if path.is_file():z.write(path,name)
    (backup/'git-status.txt').write_bytes(subprocess.check_output(['git','status','--short'],cwd=workspace));(backup/'git-diff.patch').write_bytes(subprocess.check_output(['git','diff','--binary'],cwd=workspace))
    for path,digest in protected.items():c.require(c.digest(path)==digest,'protected V10 artifact changed')
    c.require(files(Path(cfg['assetRoot']))==assets,'operating UI changed');c.require(c.digest(backup/'v10.mv.db')==after['sha256'],'cold copy byte mismatch')
    c.save(root/'backup-manifest.json',dict(files=files(backup),databasePath=str(db),sourceDatabaseSha256=after['sha256'],sourceJarSha256=c.digest(jar),protected=protected,uiFiles=assets))
    c.save(root/'capture-result.json',dict(success=True,normalExit=True,sourceDataUnchanged=True,sourceSchema='10',backupDatabaseSha256=after['sha256'],operatingArtifactsUnchanged=True,operatingPort=cfg['port'],operatingServerLeftStopped=True,backup=str(backup)))
    print('PASS latest normal-shutdown V10 backup; original schema/data/artifacts unchanged')
if __name__=='__main__':main()
