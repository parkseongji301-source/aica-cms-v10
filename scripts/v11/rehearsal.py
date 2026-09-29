"""Run only on a fresh isolated copy of the latest cold V10 backup. Never uses 8095."""
import argparse, json, os, shutil, subprocess, sys, time, urllib.request
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cutover'))
import cutover as c
from http_client import Client

USER='v11-rehearsal@example.test'
PASSWORD='V11-Rehearsal-Only-2026!'
def main():
 p=argparse.ArgumentParser()
 p.add_argument('mode',choices=['prepare','serve-ui','finish'])
 for name in ('java','rc','promotion','capture','root'):p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--port',type=int,default=8098);a=p.parse_args()
 root=a.root.resolve();java=a.java.resolve();rc=a.rc.resolve();capture=a.capture.resolve();backup=capture/'backup';promotion=a.promotion.resolve()
 c.require(a.port not in (8095,8096,8097) and 'rehearsal' in root.name and '/.local-data/' not in root.as_posix(),'isolated copy and port required')
 db=root/'fixture.mv.db';run=root/'promotion';agent=root/'shutdown-tool/graceful-stop.jar'
 common=['--java',java,'--rc',rc,'--rc-sha256',c.digest(rc)]
 def promote(mode,extra,label):c.run_cmd([sys.executable,'-B',promotion,mode,*common,*extra],root/(label+'.log'),timeout=None if mode=='serve' else 300)
 def inspect(label):return c.readonly(java,rc,db,root/(label+'.json'))
 def helper(mode,label=None):
  cp=os.pathsep.join([str(root/'helper'),str(root/'runtime/classes'),str(root/'runtime/lib/*')])
  c.run_cmd([java,'-Dfile.encoding=UTF-8','-cp',cp,'FixtureSupport',mode,root,db,*([root/(label+'.json')] if label else [])],root/((label or mode)+'.log'))
 def start(label):return c.start(java,rc,db,a.port,root,label,run/'runtime-receipt.json',agent=agent)
 def stop(server,label):
  c.stop(java,agent,server,root,label,db)
  c.require('Shutdown completed' in (root/(label+'.log')).read_text(encoding='utf-8'),'missing normal shutdown')
 def public_reads():
  return {route:urllib.request.urlopen(f'http://127.0.0.1:{a.port}'+route,timeout=10).read().decode() for route in ['/login','/api/public/v1/menus','/api/public/v1/posts']}
 if a.mode=='prepare':
  root.mkdir(parents=True,exist_ok=False);shutil.copy2(backup/'v10.mv.db',db)
  sys.path.insert(0,str(Path(__file__).parent));from audit_migrations import extract
  extract(rc,root/'runtime');(root/'helper').mkdir()
  cp=os.pathsep.join([str(root/'runtime/classes'),str(root/'runtime/lib/*')])
  c.run_cmd([java.parent/'javac.exe','-encoding','UTF-8','-cp',cp,'-d',root/'helper',Path(__file__).with_name('FixtureSupport.java')],root/'helper-compile.log')
  helper('rows','original-rows')
  promote('plan',['--db',db,'--output',root/'plan.json'],'plan')
  c.require(c.digest(db)==c.load(capture/'backup-manifest.json')['sourceDatabaseSha256'],'plan changed baseline bytes')
  promote('migrate',['--plan',root/'plan.json','--approved-plan-sha',c.digest(root/'plan.json'),'--backup-manifest',capture/'backup-manifest.json','--backup-manifest-sha256',c.digest(capture/'backup-manifest.json'),'--run-dir',run],'migration')
  c.prepare_agent(java,agent.parent);baseline=inspect('migrated-cold')
  c.require([x['version'] for x in baseline['history']]==[str(n) for n in range(1,12)],'not exactly V1..V11')
  old=c.load(capture/'source-cold.json')
  for table,value in old['fingerprints'].items():c.require(baseline['fingerprints'][table]==value,'migration changed '+table)
  for cycle in (1,2):
   label='schema-cycle-'+str(cycle);server=start(label)
   try:public_reads()
   finally:stop(server,label)
   cold=inspect(label+'-cold');c.require(cold['history']==baseline['history'] and cold['columns']==baseline['columns'] and cold['fingerprints']==baseline['fingerprints'],'read-only cycle changed data')
  print('PASS V11-only migration, full legacy fingerprints, normal receipt startup, shutdown/cold/restart',flush=True)
  helper('account');server=start('api-workflow')
  try:
   client=Client(a.port,USER,PASSWORD);c.require(client.bootstrap['user']['role']=='SUPER_ADMIN','fixture login failed')
   boundary='V11RehearsalBoundary';payload=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="v11-proof.txt"\r\nContent-Type: text/plain\r\n\r\nV11 attachment preservation proof\r\n--{boundary}--\r\n').encode()
   request=urllib.request.Request(client.base+'/api/admin/next/media',data=payload,headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-CSRF-TOKEN':client.csrf})
   with client.opener.open(request) as response:media=json.load(response)
   file=media['id'];rich=json.dumps({'ops':[{'insert':'V11 initial body','attributes':{'bold':True}},{'insert':'\n'},{'insert':{'aicaFile':{'id':file,'label':'proof'}}},{'insert':'\n'}]})
   body=dict(title='V11 API rehearsal',content='',richContent=rich,categoryId=client.bootstrap['categories'][0]['id'],mediaIds=[file],classification={'typeCode':'RESTAURANT','cohortIds':[],'topicIds':[]},restaurant={'address':'V11 isolated rehearsal address'},saveIntent='MANUAL_DRAFT')
   draft=client.req('/api/admin/next/posts',body,'POST',expect=201);id=draft['id'];path='/api/admin/next/posts/'+str(id)
   client.public('/posts/'+str(id),404);published=client.req(path+'/publish',draft,'POST');first=client.public('/posts/'+str(id))
   edit=dict(published,title='V11 edited draft',saveIntent='MANUAL_DRAFT');edited=client.req(path,edit,'PUT');c.require(client.public('/posts/'+str(id))==first,'draft leaked publicly')
   republished=client.req(path+'/publish',edited,'POST');c.require(client.public('/posts/'+str(id))['title']=='V11 edited draft','republish failed')
   versions=client.req(path+'/versions');client.req(path+'/trash',{'revision':republished['revision']-1,'confirmed':True},'POST',expect=409)
   client.req(path+'/trash',{'revision':republished['revision'],'confirmed':True},'POST');client.public('/posts/'+str(id),404)
   row=next(x for x in client.req('/api/admin/next/posts/trash')['items'] if x['id']==id)
   restored=client.req(path+'/restore',{'revision':row['revision'],'confirmed':True},'POST')
   for key in ('id','title','content','richContent','categoryId','mediaIds','classification','restaurant','authorId','createdAt'):c.require(restored[key]==republished[key],'restore differs: '+key)
   c.require(restored['status']=='DRAFT' and client.req(path+'/versions')==versions,'restore history/state differs');client.public('/posts/'+str(id),404)
   republished=client.req(path+'/publish',restored,'POST');client.public('/posts/'+str(id))
   client.req(path+'/trash',{'revision':republished['revision'],'confirmed':True},'POST')
   row=next(x for x in client.req('/api/admin/next/posts/trash')['items'] if x['id']==id)
   client.req(path+'/trash',{'revision':row['revision'],'confirmed':True},'DELETE');client.req(path,expect=404);client.req(path+'/restore',{'revision':row['revision'],'confirmed':True},'POST',expect=404)
   c.require(not any(x['id']==id for x in client.req('/api/admin/next/posts/trash')['items']),'purged post remains in trash')
   keep=client.req('/api/admin/next/posts',dict(body,title='V11 restart persistence'),'POST',expect=201);keep=client.req('/api/admin/next/posts/'+str(keep['id'])+'/publish',keep,'POST')
   proof=dict(success=True,purgedPostId=id,retainedPostId=keep['id'],mediaId=file,draftPublicIsolation=True,restoredLatestDraftAndHistory=True,staleRevisionRejected=True,expected=keep,versions=client.req('/api/admin/next/posts/'+str(keep['id'])+'/versions'),public=client.public('/posts/'+str(keep['id'])))
   c.save(root/'api-workflow.json',proof)
  finally:stop(server,'api-workflow')
  inspect('api-workflow-cold');print('PASS API publication/trash/restore/republish/permanent delete',flush=True)
  return
 if a.mode=='serve-ui':
  promote('serve',['--run-dir',run,'--port',str(a.port)],'official-ui-serve');return
 # Finish after browser checks, with the official UI server gracefully closed.
 c.exclusive(db);c.require(c.load(root/'browser-result.json')['success'],'browser workflow evidence required')
 before=inspect('ui-cold');helper('rows','final-rows');original=c.load(root/'original-rows.json');final=c.load(root/'final-rows.json')
 for table,rows in original.items():
  for h,count in rows.items():c.require(final[table].get(h,0)>=count,'original record changed in '+table)
 server=start('workflow-restart')
 try:
  client=Client(a.port,USER,PASSWORD);proof=c.load(root/'api-workflow.json');id=proof['retainedPostId'];path='/api/admin/next/posts/'+str(id)
  c.require(client.req(path)==proof['expected'] and client.req(path+'/versions')==proof['versions'] and client.public('/posts/'+str(id))==proof['public'],'saved publication/version lost at restart')
  c.require(any(x['id']==proof['mediaId'] for x in client.req('/api/admin/next/media')),'preserved attachment unavailable')
 finally:stop(server,'workflow-restart')
 after=inspect('workflow-restart-cold');c.require(before['fingerprints']==after['fingerprints'],'restart changed data')
 promote('rollback',['--run-dir',run],'rollback');c.require(c.digest(db)==c.digest(backup/'v10.mv.db'),'rollback DB not byte exact')
 oldrc=backup/'v10-rc.jar';v3=backup/'v3-runtime.jar';oldtool=backup/'tools/cutover.py';plan=root/'rollback-v10-plan.json';v10run=root/'rollback-v10-run'
 manifest=root/'rollback-runtime-hashes.json';c.save(manifest,{'v10-rc.jar':c.digest(oldrc),'v3-runtime.jar':c.digest(v3)})
 refs=[]
 for key,file in [('source-inspection',capture/'source-cold-with-runtime.json'),('source-receipt',backup/'run/runtime-receipt.json'),('release-manifest',manifest)]:refs.extend(['--'+key,file,'--'+key+'-sha256',c.digest(file)])
 c.run_cmd([sys.executable,'-B',oldtool,'relocate-plan','--java',java,'--rc',oldrc,'--rc-sha256',c.digest(oldrc),'--v3-runtime',v3,'--v3-sha256',c.digest(v3),'--db',db,*refs,'--output',plan],root/'rollback-v10-plan.log')
 c.run_cmd([sys.executable,'-B',oldtool,'relocate-approve','--java',java,'--plan',plan,'--approved-plan-sha',c.digest(plan),'--run-dir',v10run,'--authorize-relocation'],root/'rollback-v10-approval.log')
 c.run_cmd([sys.executable,'-B',Path(__file__).with_name('rollback_validate.py'),'--java',java,'--root',root,'--capture',capture,'--port',str(a.port)],root/'rollback-validation.log',timeout=300)
 source=c.load(capture/'source-cold.json')
 c.save(root/'result.json',dict(success=True,originalTouched=False,schemaOnlyCycles=2,migrationsExecuted=1,legacyRecordsPreserved=True,apiWorkflow=True,reactWorkflow=True,gracefulColdRestart=True,rollbackFullV10Bundle=True,rollbackVersion='10',rcSha256=c.digest(rc),sourceDatabaseSha256=source['sha256']))
 print('PASS all original records, restart, exact V10 DB restore, frozen V10 JAR/UI/config startup and final cold inspection',flush=True)
if __name__=='__main__':main()
