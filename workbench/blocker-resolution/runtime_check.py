import sys,json,hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cutover-rehearsal'))
from http_client import Client
c=Client(8096);out=Path(sys.argv[1]);name=sys.argv[2];round=int(sys.argv[3])
page=c.req('/api/admin/next/pages/65');p1=c.req('/api/admin/next/pages/1');post=c.req('/api/admin/next/posts/33');public=c.public('/pages/65')
prior=out.parent/f'{name}-http-{round-1}.json'
if round>1:
 previous=json.loads(prior.read_text(encoding='utf-8'));assert page==previous['saved'];assert p1==previous['page1'];assert post==previous['post33'];assert public==previous['public']
saved=c.req('/api/admin/next/pages/65',dict(revision=page['revision'],title=f'[5C-1B {name}] restart {round}',sections=page['sections'],saveIntent='MANUAL_DRAFT'),'PUT')
assert saved['revision']==page['revision']+1 and saved['sections']==page['sections']
assert c.req('/api/admin/next/pages/65')==saved and c.public('/pages/65')==public
result=dict(round=round,before=page,saved=saved,page1=p1,post33=post,public=public,allChecksPassed=True)
out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print('PASS',name,round,'revision',saved['revision'])
