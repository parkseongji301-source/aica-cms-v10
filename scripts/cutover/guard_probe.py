"""Packaged RC negative-path checks, restricted to disposable fresh V3 copies."""
import argparse, json, os, shutil, subprocess
from pathlib import Path
from cutover import digest, save, load, tool, jdbc, clean_env, NO_WINDOW, require

p=argparse.ArgumentParser()
for key in ['java','rc','backup','output']: p.add_argument('--'+key,required=True)
a=p.parse_args();root=Path(a.output).resolve();require(not root.exists(),'new evidence directory required')
root.mkdir(parents=True);java=Path(a.java).resolve();rc=Path(a.rc).resolve()
require('/.local-data/' not in root.as_posix(),'disposable evidence directory required')
db=root/'guard.mv.db';shutil.copy2(a.backup,db);original=digest(db)
plan=root/'approval.json';tool(java,rc,'plan',db,plan);base=load(plan);results=[]

def rejected(name, args, env=None):
    with (root/(name+'.log')).open('w',encoding='utf-8') as log:
        r=subprocess.run([str(x) for x in args],env=env or clean_env(),stdout=log,stderr=subprocess.STDOUT,timeout=60,creationflags=NO_WINDOW)
    require(r.returncode!=0,name+' unexpectedly succeeded')
    require(digest(db)==original,name+' changed the V3 database')
    results.append({'case':name,'rejected':True,'databaseUnchanged':True})

def migrate(file,out):
    return [java,'-Dfile.encoding=UTF-8','-Dloader.main=egovframework.backoffice.mvp.operations.CutoverTool','-cp',rc,
            'org.springframework.boot.loader.launch.PropertiesLauncher','migrate',file,out]

rejected('no-cutover-flag',migrate(plan,root/'no-flag-receipt.json'))
e=clean_env();e['AICA_CUTOVER_ENABLED']='true';e['AICA_CUTOVER_APPROVAL_SHA256']='wrong'
rejected('wrong-approval-sha',migrate(plan,root/'wrong-sha-receipt.json'),e)
for name in ['jar','database-hash','fingerprint','history','migrations','option','path','expired','spent']:
    changed=json.loads(json.dumps(base));file=root/(name+'-plan.json')
    if name=='jar': changed['jarSha256']='wrong'
    elif name=='database-hash': changed['database']['sha256']='wrong'
    elif name=='fingerprint': changed['database']['fingerprints']={}
    elif name=='history': changed['database']['history'][0]['checksum']=0
    elif name=='migrations': changed['migrations'][3]['checksum']=0
    elif name=='option': changed['jdbcUrl']=changed['jdbcUrl'].replace(';AUTO_COMPACT_FILL_RATE=0','')
    elif name=='path': changed['jdbcUrl']=changed['jdbcUrl'].replace('guard;', 'other;')
    elif name=='expired': changed['expiresAt']='2000-01-01T00:00:00Z'
    elif name=='spent': Path(str(file)+'.spent').write_text('prior attempt',encoding='utf-8')
    save(file,changed);e=clean_env();e.update(AICA_CUTOVER_ENABLED='true',AICA_CUTOVER_APPROVAL_SHA256=digest(file))
    rejected(name,migrate(file,root/(name+'-receipt.json')),e)

runtime=[java,'-Dfile.encoding=UTF-8','-jar',rc,'--spring.profiles.active=dev','--server.port=0','--backoffice.bootstrap.enabled=false',
         '--backoffice.classification-migration.copy-validation=true']
for name,extra in [
    ('runtime-no-option',['--spring.datasource.url='+jdbc(db).replace(';AUTO_COMPACT_FILL_RATE=0','')]),
    ('runtime-wrong-option',['--spring.datasource.url='+jdbc(db).replace('FILL_RATE=0','FILL_RATE=90')]),
    ('hikari-url-override',['--spring.datasource.url='+jdbc(db),'--spring.datasource.hikari.jdbc-url='+jdbc(db).replace('FILL_RATE=0','FILL_RATE=90')]),
    ('runtime-no-migration',['--spring.datasource.url='+jdbc(db)]),
    ('runtime-cutover-flag',['--spring.datasource.url='+jdbc(db),'--AICA_CUTOVER_ENABLED=true']),
    ('runtime-init-override',['--spring.datasource.url='+jdbc(db),'--spring.datasource.hikari.connection-init-sql=SET AUTO_COMPACT_FILL_RATE 90']),
]: rejected(name,runtime+extra)

original_like=root/'.local-data';original_like.mkdir();alias=original_like/'candidate.mv.db';shutil.copy2(db,alias)
rejected('original-guard-with-copy-flag',runtime+['--spring.datasource.url='+jdbc(alias)])
require(digest(alias)==original,'original-like copy changed')
save(root/'results.json',dict(cases=results,passed=len(results),jarSha256=digest(rc)))
print('PASS packaged fail-closed cases:',len(results),flush=True)
