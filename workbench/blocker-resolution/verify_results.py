"""Verify saved evidence without opening or modifying any DB."""
import json,hashlib,csv,collections
from pathlib import Path
b=Path(Path('.cache/phase5c1b-current.txt').read_text().strip())
old=Path(Path('.cache/phase5c1-current.txt').read_text().strip())
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
baseline=read(b/'baseline.json')
assert sha(Path('.local-data/aica-local.mv.db')).upper()==baseline['sourceHash']
assert sha(Path('.local-data/backoffice.mv.db')).upper()==baseline['secondaryHash']
manifest=read(old/'release/source-manifest.json')
assert not [p for p,h in manifest.items() if (p.startswith(('src/','frontend/','scripts/')) or p=='pom.xml') and sha(Path(p))!=h]
matrix=read(b/'evidence/comparison-matrix.json');counts=collections.defaultdict(collections.Counter)
for row in matrix:
 assert row['before']['fileId']==row['after']['fileId'] and not row['after']['locked'] and row['externalHashUnchanged']
 counts[row['variant']][row['externalVersion']]+=1
assert counts['no-compact']=={'10':6} and counts['upstream-commit']=={'10':6} and counts['default-again']=={'3':6}
for mode,version in [('default','3'),('no-compact','10')]:
 proof=read(b/f'evidence/proof-{mode}.json');physical=read(b/f'evidence/proof-{mode}.physical.json')
 assert proof['runtime']['pid']>0 and proof['steps'][-1]['target']==10
 assert physical['before']['fileId']==physical['after']['fileId']
 external=next(x for x in read(b/f'evidence/proof-{mode}.external.json') if x['phase']=='readonly')
 assert external['history'][-1]['version']==version
cycles=[];unstable=[]
for p in sorted((b/'evidence').glob('server-*.result.json')):
 d=read(p);assert d['flywayVersion']=='10' and d['shutdownLockReleased'] and d['processExited'] and d['liveLockBlocked']
 assert d['before']['fileId']==d['after']['fileId']
 start=read(b/f"evidence/{d['name']}.start.json");stop=read(b/f"evidence/{d['name']}.before-stop.json")
 for stage,v in [('start',start),('before-stop',stop)]:
  assert v['pid']==d['pid'] and v['history'][-1]['version']=='10' and v['effectiveJdbcUrl']==d['jdbcUrl']
  if not v['twoReadHashesEqual']:unstable.append(d['name']+'.'+stage)
  v['representativeSha256']=hashlib.sha256(json.dumps({k:v[k] for k in ['page1','page65','post33']},sort_keys=True,ensure_ascii=False).encode()).hexdigest()
 d.update(startLiveSha256=start['liveSha256'],beforeStopLiveSha256=stop['liveSha256'],startFingerprint=start['representativeSha256'],beforeStopFingerprint=stop['representativeSha256'])
 cycles.append(d)
assert len(cycles)==8
for place in ['inside','outside']:
 docs=[read(b/f'evidence/server-{place}-http-{n}.json') for n in range(1,5)]
 for prev,now in zip(docs,docs[1:]):assert prev['saved']==now['before'] and prev['post33']==now['post33'] and prev['page1']==now['page1'] and prev['public']==now['public']
assert read(b/'evidence/rollback-before.json')==read(b/'evidence/rollback-after.json')
assert read(b/'evidence/rollback.concurrent-lock.json')['sqlErrorCode']==90020
assert read(b/'evidence/precedence.concurrent-lock.json')['sqlErrorCode']==90020
assert read(b/'evidence/precedence.start.json')['history'][-1]['version']=='10'
for kind in ['v3','v10']:
 p=read(b/f'evidence/path-{kind}.json');assert p[0]['path']==p[1]['path'] and p[0]['history']==p[1]['history'] and p[2]['sqlErrorCode']==90146
summary=dict(matrix=dict(counts),restartCycles=8,pids=[c['pid'] for c in cycles],liveNonAtomicHashObservations=unstable,
 rollbackIdentical=True,rollbackTables=len(read(b/'evidence/rollback-after.json')['tables']),rollbackVersion='3',
 applicationScriptsAndMigrationsUnchanged=True,originalSha256=baseline['sourceHash'],secondarySha256=baseline['secondaryHash'],
 rootCause='H2 2.3.232 close-time MVStore compaction/commit ordering',mitigation='AUTO_COMPACT_FILL_RATE=0 on every file DB writer',originalCutoverAuthorized=False)
(b/'evidence/verified-results.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
fields=['name','profile','pid','jdbcUrl','jarSha256','startLiveSha256','beforeStopLiveSha256','startFingerprint','beforeStopFingerprint']
with (b/'evidence/runtime-index.csv').open('w',encoding='utf-8-sig',newline='') as f:
 writer=csv.DictWriter(f,fieldnames=fields+['physicalPath','fileId','beforeSize','afterSize','beforeColdSha256','afterColdSha256','afterModified']);writer.writeheader()
 for c in cycles:writer.writerow({**{k:c[k] for k in fields},'physicalPath':c['after']['path'],'fileId':c['after']['fileId'],'beforeSize':c['before']['size'],'afterSize':c['after']['size'],'beforeColdSha256':c['before']['sha256'],'afterColdSha256':c['after']['sha256'],'afterModified':c['after']['modified']})
print(json.dumps(summary,indent=2))
