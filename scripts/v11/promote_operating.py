"""Approved operating promotion using frozen tools; automatically restore V10 on failure."""
import argparse,ctypes,json,os,shutil,socket,subprocess,sys,time,urllib.request
from pathlib import Path

RC_SHA='5be79fd5ce32e348e6ddf0f3b5acd5f32bebd0441802ac3356d64deca800906e'
RELEASE_SHA='6168e5b9ed150787667724d91355c8e3231217c1e43dc4b7d52617b69ea354f9'
def main():
 p=argparse.ArgumentParser()
 for name in ('workspace','root','release','powershell'):p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();w=a.workspace.resolve();root=a.root.resolve();release=a.release.resolve();pwsh=a.powershell.resolve()
 sys.path.insert(0,str(release/'tools/cutover'));import cutover as c
 sys.path.insert(0,str(release/'tools/v11'));import promotion as promotion
 cfg=c.load(w/'.cache/company-v10/ui-launch.json');oldrun=Path(cfg['runDir']);oldinfo=c.load(oldrun/'input.json');db=Path(oldinfo['target']).resolve();java=Path(cfg['java']);rc=release/'runtime/v11-rc.jar';tool=release/'tools/v11/promotion.py'
 capture=root/'capture';backup=capture/'backup';manifest=c.load(capture/'backup-manifest.json');source=c.load(capture/'source-cold.json');run=root/'run';plan=root/'operating-plan.json';active=None
 c.require(root.is_relative_to(w/'.cache/company-v11') and db==Path(manifest['databasePath']).resolve() and cfg['port']==8095,'wrong operating paths')
 # A completed/partially executed attempt must never turn a repeated command into a rollback.
 c.require(not (root/'acceptance.json').exists() and not run.exists() and not plan.exists(),'operating attempt already exists; inspect its evidence instead of rerunning')
 def command(mode,extra,label):c.run_cmd([cfg['python'],'-X','utf8','-B',tool,mode,'--java',java,'--rc',rc,'--rc-sha256',RC_SHA,*extra],root/(label+'.log'),timeout=300)
 def check_cold(label,jar=rc,expected=None):
  value=c.readonly(java,jar,db,root/(label+'.json'))
  if expected:
   for key in ('history','columns','fingerprints'):c.require(value[key]==expected[key],'unexpected database change: '+key)
  return value
 def probe():
  codes={}
  for route in ('/login','/api/public/v1/menus','/api/public/v1/posts'):
   with urllib.request.urlopen('http://127.0.0.1:8095'+route,timeout=5) as response:c.require(response.status==200,'operating HTTP failed');codes[route]=response.status
  return codes
 def wait_ready(label,wrapper=None,version11=True):
  nonlocal active
  target=run if version11 else oldrun;expected_jar=RC_SHA if version11 else oldinfo['rcSha256']
  for _ in range(180):
   if wrapper:c.require(wrapper.poll() is None,'runtime wrapper exited: '+label)
   dirs=sorted(target.glob('serve-*'))
   if dirs and (dirs[-1]/'normal-runtime-process.json').exists():
    evidence=dirs[-1];r=c.load(evidence/'normal-runtime-process.json')
    if Path(r['database']).resolve()==db and r['jarSha256']==expected_jar:
     active=dict(evidence=evidence,runtime=r,wrapper=wrapper,version11=version11,label=label)
     try:
      codes=probe();c.save(root/(label+'-ready.json'),dict(pid=r['pid'],jarSha256=expected_jar,database=str(db),port=8095,http=codes));return
     except Exception:pass
   time.sleep(.5)
  raise RuntimeError('operating startup timed out: '+label)
 def start_cycle(label):
  c.exclusive(db)
  with socket.socket() as s:s.bind(('127.0.0.1',8095))
  with (root/(label+'-serve.log')).open('x',encoding='utf-8') as log:wrapper=subprocess.Popen([cfg['python'],'-X','utf8','-B',tool,'serve','--java',java,'--rc',rc,'--rc-sha256',RC_SHA,'--run-dir',run,'--port','8095'],env=promotion.environment(),stdout=log,stderr=subprocess.STDOUT,creationflags=c.NO_WINDOW)
  wait_ready(label,wrapper)
 def stop_active(strict=True):
  nonlocal active
  if not active:c.exclusive(db);return
  current=active;r=current['runtime'];e=current['evidence'];agent=e/'shutdown-tool/graceful-stop.jar' if current['version11'] else oldrun/'shutdown-tool/graceful-stop.jar'
  kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.OpenProcess.restype=ctypes.c_void_p;kernel.OpenProcess.argtypes=[ctypes.c_uint32,ctypes.c_int,ctypes.c_uint32];kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint32];kernel.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_uint32)];kernel.CloseHandle.argtypes=[ctypes.c_void_p]
  handle=kernel.OpenProcess(0x100000|0x1000,False,r['pid']);code=None
  if handle:
   try:
    c.run_cmd([java,'--add-modules','jdk.attach','-cp',agent,'GracefulStop',str(r['pid']),agent],root/(current['label']+'-stop.log'))
    c.require(kernel.WaitForSingleObject(handle,60000)==0,'runtime failed to close');exit_code=ctypes.c_uint32();c.require(kernel.GetExitCodeProcess(handle,ctypes.byref(exit_code)),'exit code unavailable');code=exit_code.value
   finally:kernel.CloseHandle(handle)
  if current['wrapper']:current['wrapper'].wait(timeout=60)
  c.exclusive(db);active=None
  if strict:c.require(code==0 and 'Shutdown completed' in (e/'normal-runtime.log').read_text(encoding='utf-8'),'abnormal shutdown')
  c.save(root/(current['label']+'-closed.json'),dict(pid=r['pid'],exitCode=code,exclusiveFileAccess=True,normalShutdown=code==0))
 def detached(script,label):
  c.run_cmd([pwsh,'-NoProfile','-File',w/'scripts/v11/start_hidden.ps1','-ShellPath',pwsh,'-ScriptPath',script,'-WorkingDirectory',w,'-OutputPrefix',root/label],root/(label+'-launcher.log'))
 def rollback(error):
  c.save(root/'failure.json',dict(status='STOP',error=str(error)))
  stop_active(False);c.exclusive(db)
  # Check every backup file before replacing the one explicitly authorized database file.
  promotion.bundle(capture/'backup-manifest.json');c.require(c.digest(backup/'v10.mv.db')==source['sha256'],'rollback backup differs')
  if (run/'input.json').exists() and (run/'runtime-receipt.json').exists():command('rollback',['--run-dir',run,'--authorize-original'],'automatic-rollback')
  else:
   preserved=root/'failed-migration';preserved.mkdir();shutil.copy2(db,preserved/'database.mv.db');shutil.copy2(rc,preserved/'v11-rc.jar')
   c.require(db==Path(manifest['databasePath']).resolve(),'rollback target differs');shutil.copy2(backup/'v10.mv.db',db)
  check_cold('automatic-rollback-cold',Path(oldinfo['rc']),source)
  for path,digest in manifest['protected'].items():c.require(c.digest(path)==digest,'V10 activation artifact differs')
  detached(w/'.cache/company-v10/start-ui.ps1','rollback-v10');wait_ready('rollback-v10',version11=False)
  c.save(root/'acceptance.json',dict(status='FAIL',actualV11Promotion=False,rolledBackToV10=True,error=str(error),operatingPid=active['runtime']['pid'],backup=str(backup)))
  print('FAIL V11 promotion; PASS automatic V10 rollback',flush=True)
 try:
  c.require(c.digest(release/'release-manifest.json')==RELEASE_SHA,'release manifest differs')
  rm=c.load(release/'release-manifest.json')
  for name,digest in rm['files'].items():c.require(c.digest(release/name)==digest,'release artifact differs: '+name)
  promotion.bundle(capture/'backup-manifest.json');c.require(c.digest(db)==source['sha256'],'target changed after latest backup')
  latest=root/'rehearsal-final-check';api=c.load(latest/'api-workflow.json');c.require(api['success'],'latest-copy functional check incomplete')
  browser=c.load(latest/'browser-result.json');c.require(browser['success'],'React check incomplete')
  command('plan',['--db',db,'--output',plan],'operating-plan')
  planned=c.load(plan);c.require(planned['before']['sha256']==source['sha256'] and planned['jarSha256']==RC_SHA,'plan target differs')
  c.save(root/'approval.json',dict(kind='V10_TO_V11',userAuthorized=True,rollbackOnFailure=True,planSha256=c.digest(plan),backupManifestSha256=c.digest(capture/'backup-manifest.json'),jarSha256=RC_SHA,databasePath=str(db),scope=['React create/edit/publish/republish','trash/restore/permanent delete','V11 post_trash']))
  command('migrate',['--plan',plan,'--approved-plan-sha',c.digest(plan),'--backup-manifest',capture/'backup-manifest.json','--backup-manifest-sha256',c.digest(capture/'backup-manifest.json'),'--run-dir',run,'--authorize-original'],'operating-migrate')
  receipt=c.load(run/'runtime-receipt.json');c.require(receipt['status']=='MIGRATED_V11' and receipt['migrationsExecuted']==1 and receipt['legacyDataPreserved'],'invalid operating receipt')
  migrated=check_cold('operating-migrated-cold')
  c.require(migrated['history']==receipt['migrations'] and migrated['history'][:10]==source['history'],'migration history differs')
  for table,value in source['fingerprints'].items():c.require(migrated['fingerprints'][table]==value,'migration changed existing table: '+table)
  c.require(migrated['fingerprints']['POST_TRASH']['rows']==0 and set(migrated['columns'])-set(source['columns'])=={'POST_TRASH'},'unexpected migration scope')
  print('PASS operating V11-only migration, new receipt, all legacy data preserved',flush=True)
  for cycle in (1,2):
   label='operating-cycle-'+str(cycle);start_cycle(label);stop_active();check_cold(label+'-cold',expected=migrated)
   print('PASS 8095 normal startup/shutdown/cold cycle',cycle,flush=True)
  promotion.bundle(capture/'backup-manifest.json')
  launch=dict(java=str(java),python=cfg['python'],tool=str(tool),runDir=str(run),rc=str(rc),rcSha256=RC_SHA,port=8095,staticAssets='classpath: embedded in pinned V11 JAR')
  promotion.new_json(w/'.cache/company-v11/ui-launch.json',launch)
  detached(w/'.cache/company-v11/start-ui.ps1','v11-final');wait_ready('v11-final')
  for path,digest in manifest['protected'].items():c.require(c.digest(path)==digest,'preserved V10 artifact differs')
  c.save(root/'acceptance.json',dict(status='PASS',actualV11Promotion=True,version='11',port=8095,operatingPid=active['runtime']['pid'],migrationsExecuted=1,receipt=str(run/'runtime-receipt.json'),receiptSha256=c.digest(run/'runtime-receipt.json'),jarSha256=RC_SHA,legacyTablesPreserved=len(source['fingerprints']),businessWritesPerformed=False,normalShutdownRestartCycles=2,finalStartup=True,reactCheck='PASS_LATEST_COPY_SAME_JAR',trashRestorePurgeCheck='PASS_LATEST_COPY_SAME_JAR',rollbackBundlePreserved=True,backup=str(backup),backupManifestSha256=c.digest(capture/'backup-manifest.json'),uxBaseline='V11-RC1-20260929'))
  c.save(w/'.cache/company-v11/operation.json',dict(root=str(root),status='PASS',version='11',port=8095,launchConfig=str(w/'.cache/company-v11/ui-launch.json'),acceptance=str(root/'acceptance.json')))
  print('PASS actual 8095 V11 promotion; final V11 running; full V10 rollback bundle preserved',flush=True)
 except Exception as error:
  try:rollback(error)
  except Exception as recovery:
   c.save(root/'rollback-failure.json',dict(status='STOP',promotionError=str(error),rollbackError=str(recovery)));raise
  raise SystemExit(1)
if __name__=='__main__':main()
