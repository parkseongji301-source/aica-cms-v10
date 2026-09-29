"""Authenticate UI checks on a disposable restored V10 copy, then restore it again."""
import argparse,json,os,shutil,subprocess,sys,time,urllib.request
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cutover'))
import cutover as c
from http_client import Client
def main():
 p=argparse.ArgumentParser()
 for name in ('java','root','capture'):p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--port',type=int,required=True);a=p.parse_args()
 root=a.root.resolve();capture=a.capture.resolve();backup=capture/'backup';db=root/'fixture.mv.db';java=a.java.resolve();run=root/'rollback-v10-run';oldrc=backup/'v10-rc.jar';tool=backup/'tools/cutover.py'
 c.require(a.port not in (8095,8096,8097) and 'rehearsal' in root.name and db.is_relative_to(root) and '/.local-data/' not in db.as_posix(),'isolated rollback required')
 c.require(c.load(root/'promotion/rollback-result.json')['success'],'prior V10 restoration required')
 source=c.load(capture/'source-cold.json')
 def cold(label):
  value=c.readonly(java,oldrc,db,root/(label+'.json'))
  for key in ('history','columns','fingerprints'):c.require(value[key]==source[key],'V10 baseline differs: '+key)
  return value
 def cycle(label,authenticated=False):
  env=c.clean_env();env['SPRING_WEB_RESOURCES_STATIC_LOCATIONS']=(backup/'ui-assets').as_uri()+'/,classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/'
  with (root/(label+'.log')).open('x',encoding='utf-8') as log:wrapper=subprocess.Popen([sys.executable,'-B',tool,'serve','--java',java,'--run-dir',run,'--port',str(a.port)],env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=c.NO_WINDOW)
  runtime=None;checked=[]
  try:
   for _ in range(120):
    c.require(wrapper.poll() is None,'V10 serve failed')
    try:
     with urllib.request.urlopen(f'http://127.0.0.1:{a.port}/login',timeout=1) as response:
      if response.status==200:break
    except Exception:pass
    time.sleep(.5)
   else:raise RuntimeError('V10 startup timeout')
   evidence=sorted(run.glob('serve-*'))[-1];runtime=c.load(evidence/'normal-runtime-process.json');c.require(runtime['jarSha256']==c.digest(oldrc) and Path(runtime['database']).resolve()==db,'wrong rollback runtime')
   for route in ['/api/public/v1/menus','/api/public/v1/posts']:
    with urllib.request.urlopen(f'http://127.0.0.1:{a.port}'+route) as response:c.require(response.status==200,'V10 public API failed')
   if authenticated:
    client=Client(a.port,'v11-rehearsal@example.test','V11-Rehearsal-Only-2026!')
    c.require(client.bootstrap['user']['role']=='SUPER_ADMIN','V10 fixture login failed')
    for asset in (backup/'ui-assets/next-app').rglob('*'):
     if asset.is_file():
      route='/next-app/'+asset.relative_to(backup/'ui-assets/next-app').as_posix()
      with client.opener.open(client.base+route) as response:
       c.require('/login' not in response.url and response.read()==asset.read_bytes(),'served V10 UI differs: '+route)
      checked.append(route)
    c.require('/login' not in client.req('/admin-next').get('url',''),'V10 React route login loop')
  finally:
   if runtime and wrapper.poll() is None:
    agent=run/'shutdown-tool/graceful-stop.jar';c.run_cmd([java,'--add-modules','jdk.attach','-cp',agent,'GracefulStop',str(runtime['pid']),agent],root/(label+'-stop.log'));c.require(wrapper.wait(60)==0,'V10 normal exit failed')
    c.require('Shutdown completed' in (evidence/'normal-runtime.log').read_text(encoding='utf-8'),'missing V10 Hikari shutdown')
  c.exclusive(db);return checked
 cold('rollback-restored-before-ui')
 # Only the disposable copy receives a helper account. Reset it from the exact backup in finally.
 c.exclusive(db);cp=os.pathsep.join([str(root/'helper'),str(root/'runtime/classes'),str(root/'runtime/lib/*')])
 try:
  c.run_cmd([java,'-Dfile.encoding=UTF-8','-cp',cp,'FixtureSupport','account',root,db],root/'rollback-ui-fixture.log')
  checked=cycle('rollback-authenticated-ui',True)
 finally:
  c.exclusive(db);shutil.copy2(backup/'v10.mv.db',db)
 restored=cold('rollback-exact-final');c.require(restored['sha256']==source['sha256'],'final restore not byte exact')
 cycle('rollback-clean-restart');cold('rollback-final-cold')
 c.save(root/'rollback-validation.json',dict(success=True,version='10',exactBackupRestored=True,allOriginalTablesPreserved=True,temporaryFixtureRemoved=True,uiFilesAuthenticatedAndByteIdentical=len(checked),runtimeSha256=c.digest(oldrc),normalShutdown=True,restart=True))
 print('PASS V10 exact bundle, authenticated UI assets, fixture removed, clean restart and cold preservation',flush=True)
if __name__=='__main__':main()
