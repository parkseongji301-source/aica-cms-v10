"""Read-only final consistency checks over recorded evidence and immutable source/DB hashes."""
import json,hashlib
from pathlib import Path
b=Path(Path('.cache/phase5c1-current.txt').read_text().strip())
def read(name):return json.loads((b/name).read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
old=read('evidence/original-v3.json');before=read('evidence/pre-cutover-v3.json');rollback=read('evidence/rollback-v3.json')
assert old==before==rollback
m=read('evidence/migration-v3-v10.json')
assert [s['target'] for s in m['steps']]==list(range(4,11))
assert all(s['applied']==1 and s['repeated']==0 and s['validated'] and s['legacyFieldsPreserved'] for s in m['steps'])
assert m['after']==m['afterReconnect']==read('evidence/cutover-v10-independent.json')
assert m['after']['history'][-1]['version']=='10'
assert read('evidence/dictionary-first.json')==read('evidence/dictionary-repeat.json')
assert read('evidence/baseline.json')['first']=={'posts':9,'pages':4,'page-templates':0}
assert all(n==0 for n in read('evidence/baseline.json')['repeat'].values())
v=read('evidence/different-revision-pre.json');assert v['pageDocuments']['site_pages:65']['REVISION']=='5' and v['pageDocuments']['page_publications:65']['REVISION']=='4'
d=read('evidence/different-revision-separate.json');assert d['after']==read('evidence/different-revision-post.json')
assert any(i['RETIRED']=='TRUE' for i in d['after']['blockIdentities'])
for n in range(1,4):
 r=read(f'evidence/lifetime-{n}.json');assert len(r['unanchoredRounds'])==12
 assert all(x['repeated']==0 and x['version']=='6' for x in r['unanchoredRounds'])
 assert r['snapshot']==read(f'evidence/lifetime-{n}-independent.json')
# Explicitly retain negative results; final status MUST NOT claim all migration scenarios passed.
assert read('evidence/revision-fixture-independent.json')['history'][-1]['version']=='3'
assert read('evidence/shutdown-1-independent.json')['history'][-1]['version']=='3'
assert read('evidence/restart-result.json')['identical']
assert len(read('evidence/runtime-smoke.json')['checks'])==8
assert all(r in read('evidence/roles-smoke.json') for r in ['ADMIN','SUPPORTER','SUPER_ADMIN'])
frozen=read('release/source-manifest.json')
assert all(sha(p)==h for p,h in frozen.items() if p.startswith(('src/','frontend/')) or p=='pom.xml')
hashes=read('release/checksums.json')
assert sha('.local-data/aica-local.mv.db')==hashes['original-v3.mv.db']
assert sha('.local-data/backoffice.mv.db')=='2a9ef304a1407a663f903e8924b1e7326c241033bd9e4c002a30b3f95c9b8a90'
for file in ['v3-runtime.jar','v10-rc.jar','source.zip']:assert sha(b/'release'/file)==hashes[file]
result={'evidenceChecksPassed':True,'originalV3Untouched':True,'applicationFrozen':True,'normalMigrationAndRollbackPassed':True,'connectionLifetimeFailureReproduced':True,'originalCutoverReady':False,'legacyTables':16,'baselineCreated':13,'baselineRepeated':0}
(b/'evidence/final-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
