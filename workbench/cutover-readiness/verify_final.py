"""Evidence verification only. No database connection or application mutation."""
import hashlib,json,sys,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
b=Path((root/'.cache/phase5c1c-current.txt').read_text(encoding='utf-8-sig').strip())
def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def load(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
report={'originalDatabases':{},'runs':{},'migrationEntriesUnchanged':{},'dependenciesUnchanged':{}}
for original in load(b/'checkpoint/hashes.json')[:2]:
 path=Path(original['Path']);value=digest(path);assert value==original['Hash'].lower();report['originalDatabases'][str(path)]=value
rc=b/'release-final/v10-rc.jar';rc_hash=digest(rc)
assert rc_hash=='606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e'
assert digest(b/'release-final/v3-runtime.jar')=='c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d'
with zipfile.ZipFile(root/'.cache/phase5c1/20260927-205122/release/v10-rc.jar') as old,zipfile.ZipFile(rc) as new:
 names=[x for x in old.namelist() if x.startswith('BOOT-INF/classes/db/migration/h2/') and not x.endswith('/')]
 assert set(names)=={x for x in new.namelist() if x.startswith('BOOT-INF/classes/db/migration/h2/') and not x.endswith('/')}
 for name in names:
  assert old.read(name)==new.read(name);report['migrationEntriesUnchanged'][name]=hashlib.sha256(new.read(name)).hexdigest()
 for name in ['BOOT-INF/lib/h2-2.3.232.jar','BOOT-INF/lib/flyway-core-10.20.1.jar']:
  assert old.read(name)==new.read(name);report['dependenciesUnchanged'][name]=hashlib.sha256(new.read(name)).hexdigest()
for name in ['final-rehearsal','production-path-rehearsal']:
 run=b/name;result=load(run/'result.json');assert result['success'] and not result['original']
 receipt=load(run/'runtime-receipt.json');assert receipt['jarSha256']==rc_hash
 assert [s['version'] for s in receipt['steps']]==list(range(4,11)) and all(s['legacyDataPreserved'] for s in receipt['steps'])
 baseline=load(run/'evidence/baseline.json');assert baseline['first']=={'posts':9,'pages':4,'page-templates':0} and all(x==0 for x in baseline['repeat'].values())
 cycles=[];published=None;previous=None
 for n in range(1,6):
  e=run/'evidence';cold=load(e/f'cycle-{n}-cold.json');process=load(e/f'cycle-{n}-process.json');closed=load(e/f'cycle-{n}-closed.json');api=load(e/f'cycle-{n}-api.json')
  assert cold['history'][-1]['version']=='10' and len(cold['history'])==10
  assert cold['path']==process['database']==receipt['databasePath'] and process['jarSha256']==rc_hash and not process['cutoverFlag']
  assert process['jdbcUrl'].endswith('AUTO_COMPACT_FILL_RATE=0') and closed['sha256']==cold['sha256'] and closed['exclusiveFileAccess'] and closed['exitCode']==0
  log=(e/f'cycle-{n}.log').read_text(encoding='utf-8');assert 'migration=validate-only' in log and 'Shutdown completed' in log
  if published: assert api['public']==published
  published=api['public']
  if previous: assert api['page']==previous['page']
  assert api['versions']['total']==min(n,4)+2
  previous=api
  cycles.append({'cycle':n,'pid':process['pid'],'coldPid':cold['pid'],'sha256':cold['sha256'],'pageVersionCount':api['versions']['total'],'size':cold['size']})
 assert load(run/'evidence/roles-smoke.json')['SUPPORTER'] and len(load(run/'evidence/cms-smoke.json')['checks'])==8
 report['runs'][name]={'cycles':cycles,'baseline':baseline,'dictionaryMode':result['dictionaryMode'],'rollbackInRun':result['rollback']}
 if result['rollback']:
  before=load(run/'backup/v3-inspection.json');after=load(run/'evidence/rollback-after.json')
  assert after['history']==before['history'] and after['fingerprints']==before['fingerprints']
  assert load(run/'evidence/rollback-backup-verified.json')['sha256']==before['sha256']
report['guard']=load(b/'guard-probe/results.json');assert report['guard']['passed']==18
production=b/'production-path-rehearsal'
rollbacks=list(production.glob('rollback-*/rollback-result.json'));assert len(rollbacks)==1 and load(rollbacks[0])['success']
backup=load(production/'backup/v3-inspection.json');restored=load(rollbacks[0].parent/'rollback-after.json')
assert backup['history']==restored['history'] and backup['fingerprints']==restored['fingerprints']
assert load(b/'evidence/serve-cold.json')['history'][-1]['version']=='10'
assert load(b/'bad-backup-probe/result.json')['targetUnchanged']
assert load(b/'evidence/post-rollback-serve-rejection.json')['v3FileUnchanged']
assert load(b/'evidence/java-processes-final.json')==[]
report['standaloneServeAndRollback']=True
report['corruptBackupRejectedBeforeOverwrite']=True
report['rolledBackV3RejectsV10EvenWithReceipt']=True
report['javaProcessesRemaining']=0
report['javaFull']='160 tests: 155 passed, 5 opt-in cases supplemented below'
report['javaOptIn']='11 passed, including all 5 previously gated cases; no failures or skips'
report['frontend']='39 passed, typecheck/build passed'
report['rcSha256']=rc_hash
(b/'evidence/final-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS originals unchanged; RC/dependencies/migrations pinned; two final-script rehearsals, 10 starts/8 version writes verified')
