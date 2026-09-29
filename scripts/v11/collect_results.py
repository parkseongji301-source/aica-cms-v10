"""Assemble acceptance only from independently retained evidence, without any DB write."""
import argparse,json,sys,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cutover'))
import cutover as c
def main():
 p=argparse.ArgumentParser()
 for name in ('workspace','capture','root','release'):p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();root=a.root.resolve();capture=a.capture.resolve();release=a.release.resolve();workspace=a.workspace.resolve()
 rc=release/'runtime/v11-rc.jar';audit=c.load(release/'migration-audit/result.json');source=c.load(capture/'source-cold.json');captured=c.load(capture/'capture-result.json');manifest=c.load(capture/'backup-manifest.json')
 c.require(captured['success'] and captured['sourceDataUnchanged'] and audit['success'] and audit['legacyMigrationBytesIdentical'] and audit['candidateJarSha256']==c.digest(rc),'capture/migration audit failed')
 receipt=c.load(root/'promotion/runtime-receipt.json');base=c.load(root/'migrated-cold.json')
 c.require(receipt['status']=='MIGRATED_V11' and receipt['jarSha256']==c.digest(rc) and receipt['migrationsExecuted']==1 and receipt['businessWrites']==0 and receipt['baselineCreated']==0 and receipt['legacyDataPreserved'],'invalid receipt')
 c.require(base['history']==receipt['migrations']==audit['migrations'] and base['history'][:10]==source['history'],'history differs')
 for table,value in source['fingerprints'].items():c.require(base['fingerprints'][table]==value,'legacy migration data changed')
 c.require(set(base['columns'])-set(source['columns'])=={'POST_TRASH'} and base['fingerprints']['POST_TRASH']['rows']==0,'V11 scope differs')
 for number in (1,2):
  cold=c.load(root/f'schema-cycle-{number}-cold.json')
  for key in ('history','columns','fingerprints'):c.require(cold[key]==base[key],'schema restart differs')
 for label in ('schema-cycle-1','schema-cycle-2','api-workflow','workflow-restart'):
  closed=c.load(root/(label+'-closed.json'));c.require(closed['exitCode']==0 and closed['exclusiveFileAccess'],'abnormal close')
  c.require('Shutdown completed' in (root/(label+'.log')).read_text(encoding='utf-8'),'missing normal close')
 api=c.load(root/'api-workflow.json');browser=c.load(root/'browser-result.json');c.require(api['success'] and browser['success'],'functional checks incomplete')
 api_cold=c.load(root/'api-workflow-cold.json');c.require(api_cold['fingerprints']['POSTS']['rows']==source['fingerprints']['POSTS']['rows']+1 and api_cold['fingerprints']['POST_TRASH']['rows']==0,'API purge row count differs')
 original=c.load(root/'original-rows.json');final=c.load(root/'final-rows.json')
 for table,rows in original.items():
  for digest,count in rows.items():c.require(final[table].get(digest,0)>=count,'original row changed')
 before=c.load(root/'ui-cold.json');after=c.load(root/'workflow-restart-cold.json');c.require(before['fingerprints']==after['fingerprints'],'workflow restart changed data')
 rollback=c.load(root/'rollback-validation.json');c.require(rollback['success'] and rollback['temporaryFixtureRemoved'] and rollback['uiFilesAuthenticatedAndByteIdentical']>0,'rollback incomplete')
 restored=c.load(root/'rollback-final-cold.json')
 for key in ('history','columns','fingerprints'):c.require(restored[key]==source[key],'V10 restoration differs')
 for path,digest in manifest['protected'].items():c.require(c.digest(path)==digest,'operating artifact changed: '+path)
 cfg=c.load(workspace/'.cache/company-v10/ui-launch.json');assets=Path(cfg['assetRoot']);c.require({p.relative_to(assets).as_posix():c.digest(p) for p in assets.rglob('*') if p.is_file()}==manifest['uiFiles'],'operating UI differs')
 runtime=c.load(sorted(Path(cfg['runDir']).glob('serve-*'))[-1]/'normal-runtime-process.json');c.require(runtime['jarSha256']==manifest['sourceJarSha256'] and runtime['database']==manifest['databasePath'],'8095 runtime identity differs')
 with urllib.request.urlopen(f"http://127.0.0.1:{cfg['port']}/login") as response:c.require(response.status==200,'8095 unavailable')
 tests=[]
 for path in (workspace/'target/surefire-reports').glob('TEST-*.xml'):
  node=ET.parse(path).getroot();tests.append({key:int(node.get(key,'0')) for key in ('tests','failures','errors','skipped')})
 counts={key:sum(t[key] for t in tests) for key in ('tests','failures','errors','skipped')};c.require(counts['failures']==counts['errors']==0 and counts['tests']==174,'Java release tests differ')
 front=(workspace/'.cache/v11-preflight/frontend-test.log').read_text(encoding='utf-8');c.require('pass 48' in front and 'fail 0' in front,'frontend verification failed')
 result=dict(success=True,actualV11Promotion=False,operatingVersion='10',operatingPort=8095,operatingPid=runtime['pid'],operatingArtifactsUnchanged=True,operatingDatabaseBusinessWrites=False,approvedV10NormalCycleOnly=True,migrationsExecutedOnCopy=1,legacyTablesPreserved=len(source['fingerprints']),legacyRecordsPreserved=True,schemaOnlyCycles=2,apiWorkflow=True,reactWorkflow=True,gracefulColdRestart=True,rollbackFullV10Bundle=True,rollbackVersion='10',rollbackUiFilesVerified=rollback['uiFilesAuthenticatedAndByteIdentical'],rcSha256=c.digest(rc),sourceDatabaseSha256=source['sha256'],javaTests=counts,frontendTestsPassed=48,sourceCounts={k:v['rows'] for k,v in source['fingerprints'].items()},evidenceRoot=str(root),backup=str(capture/'backup'),requiresFreshBackupAtActualPromotion=True)
 c.save(root/'result.json',result);print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
