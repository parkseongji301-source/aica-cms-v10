"""Writes labeled fixtures ONLY in the isolated smoke fork on 8096."""
from http_client import Client
import json,sys,uuid,copy,urllib.parse,urllib.request,base64
from pathlib import Path
c=Client(8096);out=Path(sys.argv[1]);report={'checks':[],'fixtureIds':{}}
def passed(name):
 report['checks'].append(name);out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('PASS',name)
def get(kind,id):return c.req(f'/api/admin/next/{kind}/{id}')
def history(kind,id):return c.req(f'/api/admin/next/{kind}/{id}/versions')
def post_save(d,**changes):
 body={k:d[k] for k in ['revision','title','content','richContent','categoryId','mediaIds','classification']}
 if d.get('restaurant') is not None:body['restaurant']=d['restaurant']
 body.update(saveIntent='MANUAL_DRAFT');body.update(changes)
 return c.req('/api/admin/next/posts/'+str(d['id']),body,'PUT')
def publish(kind,id,actor=c):
 d=actor.req(f'/api/admin/next/{kind}/{id}')
 body={'id':id,'revision':d['revision'],'title':d['title'],'action':'publish','saveIntent':'MANUAL_DRAFT'}
 if kind=='pages':body.update(sectionsJson=json.dumps(d['sections'],ensure_ascii=False),slug=d['slug'])
 else:body.update(content=d['content'],richContent=d['richContent'] or '',categoryId=d['categoryId'],mediaIds=d['mediaIds'])
 return actor.req(f'/admin/{kind}/save-json',body,'POST',form=True)
def page_save(d):return c.req(f'/api/admin/next/pages/{d["id"]}',{'revision':d['revision'],'title':d['title'],'sections':d['sections'],'saveIntent':'MANUAL_DRAFT'},'PUT')
def block(type='POSTS',**kw):return dict(id='block_'+str(uuid.uuid4()),schemaVersion=2,type=type,variation='default',visible=True,heading='[5C-1 시험] '+type,body='',**kw)
def query(type,topics=[],cohorts=[]):return dict(typeCode=type,topicIds=topics,cohortIds=cohorts,sort='LATEST',limit=6)
def result(page,b):return next(x['data']['posts'] for x in page['blocks'] if x['id']==b['id'])
def ids(result):
 values=[p['id'] for p in result['items']];assert len(values)==len(set(values));assert result['total']==len(values);return values
dictionary=c.req('/api/admin/next/classifications');topics={x['code']:x['id'] for x in dictionary['topics']};cohorts={x['code']:x['id'] for x in dictionary['cohorts']}
cat=c.bootstrap['categories'][0]['id']
if '--resume' not in sys.argv:
 for code,ts,cs in [('REVIEW',['REVIEW_LIFE'],['COHORT_06','COHORT_07']),('FAQ',['FAQ_LIFE'],[]),('RESTAURANT',[],[])]:
  body=dict(title='[5C-1 시험] '+code,content='공개 전환 리허설용 본문',richContent='',categoryId=cat,mediaIds=[],classification=dict(typeCode=code,cohortIds=[cohorts[x] for x in cs],topicIds=[topics[x] for x in ts]),saveIntent='MANUAL_DRAFT')
  if code=='RESTAURANT':body['restaurant']={'address':'[검증용] 이전 주소'}
  d=c.req('/api/admin/next/posts',body,'POST',expect=201);report['fixtureIds'][code]=d['id'];assert get('posts',d['id'])==d;publish('posts',d['id'])
 passed('REVIEW/FAQ/RESTAURANT 생성·재조회·Thymeleaf 발행, 복수 기수와 신규 분류 보존')
 review,faq,restaurant=[report['fixtureIds'][x] for x in ['REVIEW','FAQ','RESTAURANT']]
 original_page=get('pages',1);initial=c.public('/pages/1');report['originalPageSections']=original_page['sections'];report['originalMenus']=c.req('/api/admin/next/menus')
 blocks=[block(sourceMode='category',categoryId=cat),block(sourceMode='query',query=query('REVIEW',[topics['REVIEW_LIFE'],topics['REVIEW_PROJECT']],[cohorts['COHORT_07']])),block(sourceMode='query',query=query('FAQ',[topics['FAQ_LIFE']])),block(sourceMode='query',query=query('RESTAURANT')),block(sourceMode='manual',manual={'postIds':[restaurant,33,faq,review,999999]})]
 p=copy.deepcopy(original_page);p['sections']+=blocks;p=page_save(p);assert c.public('/pages/1')==initial;publish('pages',1);live=c.public('/pages/1')
 expected=[[restaurant,faq,review],[review],[faq],[restaurant],[restaurant,faq,review]]
 for b,want in zip(blocks,expected):assert ids(result(live,b))==want,(b,result(live,b));assert c.public(f'/pages/1/blocks/{b["id"]}/posts')==result(live,b)
 report['publicResults']=[{'source':b['sourceMode'],'ids':want} for b,want in zip(blocks,expected)]
 passed('category/query/manual 발행본 조회, AND/OR·count·순서·미발행/삭제 제외·REVIEW/FAQ 생활 격리')
 selected=c.req('/api/admin/next/pages/selected-posts?ids='+','.join(str(x) for x in blocks[-1]['manual']['postIds']));report['selectedStatuses']=selected
 assert len(selected)==5
 before=c.public('/posts/'+str(review));d=get('posts',review);newclass=copy.deepcopy(d['classification']);newclass['topicIds']=[topics['REVIEW_CLASS']]
 d=post_save(d,title='[5C-1 시험] 재발행 후기',classification=newclass);assert c.public('/posts/'+str(review))==before and c.public('/pages/1')==live
 publish('posts',review);live=c.public('/pages/1');assert ids(result(live,blocks[1]))==[];assert c.public('/posts/'+str(review))['title']==d['title']
 before=c.public('/posts/'+str(restaurant));d=post_save(get('posts',restaurant),restaurant={'address':'[검증용] 변경 주소'});assert c.public('/posts/'+str(restaurant))==before
 publish('posts',restaurant);assert c.public('/posts/'+str(restaurant))['restaurant']['address']=='[검증용] 변경 주소'
 passed('콘텐츠 초안 title/분류/주소 공개 불변 → 재발행 후 공개 변경')
 live=c.public('/pages/1');p=get('pages',1);p['sections'][-1]['manual']['postIds']=[review,restaurant,faq,33,999999];p['sections'][-4]['query']['topicIds']=[topics['REVIEW_CLASS']];p=page_save(p);assert c.public('/pages/1')==live
 publish('pages',1);live=c.public('/pages/1');assert ids(result(live,blocks[-1]))==[review,restaurant,faq];assert ids(result(live,blocks[1]))==[review]
 passed('페이지 초안 query/manual 공개 불변 → 재발행 후 새 설정/순서 반영')
else:
 report=json.loads(out.read_text(encoding='utf-8'))
 review,faq,restaurant=[report['fixtureIds'][x] for x in ['REVIEW','FAQ','RESTAURANT']]
 blocks=get('pages',1)['sections'][-5:]
# Autosave creates no versions; stale requests cannot overwrite.
d=get('posts',review);total=history('posts',review)['total'];d=post_save(d,content='자동저장 리허설',saveIntent='AUTOSAVE');assert history('posts',review)['total']==total
d=post_save(d,content='명시적 초안 저장 리허설');assert history('posts',review)['total']==total+1
c.req('/api/admin/next/posts/'+str(review),{'revision':d['revision']-1,'title':'덮어쓰면 안 됨','content':'','mediaIds':[]},'PUT',expect=409);assert get('posts',review)['title']==d['title']
h=history('posts',review);version=next(v for v in h['items'] if v['reason']=='PUBLISH');before=c.public('/posts/'+str(review));c.req(f'/api/admin/next/posts/{review}/versions/{version["id"]}/restore',{'expectedRevision':d['revision'],'confirmed':True,'operationId':str(uuid.uuid4())},'POST');assert c.public('/posts/'+str(review))==before
assert {'RESTORE','RESTORE_BACKUP'}.issubset({v['reason'] for v in history('posts',review)['items']})
passed('자동저장 version 미생성, 명시 저장 version 생성, 충돌 거부, 콘텐츠 새 초안 복구·공개본 유지')
baseline=next(v for v in history('pages',65)['items'] if v['reason']=='BASELINE_DRAFT');p=get('pages',65);p['title']='[5C-1 시험] 소개 초안';p=page_save(p);before=c.public('/pages/65');c.req(f'/api/admin/next/pages/65/versions/{baseline["id"]}/restore',{'expectedRevision':p['revision'],'confirmed':True,'operationId':str(uuid.uuid4())},'POST');assert c.public('/pages/65')==before
passed('page 65 baseline 새 초안 복구·공개본 유지')
# Upload a tiny image through the existing media endpoint; preserve only via template/version.
boundary='----rehearsal'+uuid.uuid4().hex
image=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jC3sAAAAASUVORK5CYII=')
payload=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="5c1-fixture.png"\r\nContent-Type: image/png\r\n\r\n').encode()+image+f'\r\n--{boundary}--\r\n'.encode()
req=urllib.request.Request(c.base+'/api/admin/next/media',payload,headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-CSRF-TOKEN':c.csrf});media=json.load(c.opener.open(req));mid=media['id'];report['fixtureIds']['media']=mid
tblocks=copy.deepcopy(blocks)+[block('IMAGE',imageId=mid)]
hero=block('HERO');hero['variation']='centered';hero['visible']=False;tblocks.append(hero)
t=c.req('/api/admin/next/page-templates',{'name':'[5C-1 시험] 공용 구성','description':'전환 검증 fixture','active':True,'blocks':tblocks,'saveIntent':'MANUAL_DRAFT'},'POST');tid=t['info']['id'];report['fixtureIds']['template']=tid
assert all('id' not in b for b in t['blocks']);prep=c.req(f'/api/admin/next/page-templates/{tid}/prepare',{'revision':t['info']['revision']},'POST');assert set(b['id'] for b in prep['sections']).isdisjoint(b['id'] for b in tblocks)
assert prep['sections'][-1]['variation']=='centered' and not prep['sections'][-1]['visible'];assert prep['sections'][4]['manual']==blocks[4]['manual']
usage=c.req(f'/api/admin/next/media/{mid}/usage');assert any('템플릿' in str(x) for x in usage);c.req(f'/api/admin/next/media/{mid}',{},'DELETE',expect=400)
p=get('pages',65);p['sections']+=prep['sections'];p=page_save(p);assert c.req(f'/api/admin/next/page-templates/{tid}')==t
# Remove active image from the template, while keeping the historical reference protected.
t=c.req(f'/api/admin/next/page-templates/{tid}',{'revision':t['info']['revision'],'name':t['info']['name'],'description':'변경 시험','active':False,'blocks':blocks,'saveIntent':'MANUAL_DRAFT'},'PUT')
assert get('pages',65)==p;usage=c.req(f'/api/admin/next/media/{mid}/usage');assert any('버전' in str(x) for x in usage);c.req(f'/api/admin/next/media/{mid}',{},'DELETE',expect=400)
th=history('page-templates',tid);old=th['items'][-1];c.req(f'/api/admin/next/page-templates/{tid}/versions/{old["id"]}/prepare-restore',{},'POST')
passed('템플릿 type/variation/visible/POSTS 보존·새 block ID·페이지 독립, 템플릿/버전 미디어 보호 및 복구 준비')
assert c.req('/api/admin/next/menus')==report['originalMenus'];assert c.public('/menus');assert c.req('/api/admin/next/page-structure')
for id in [1,65]:assert c.req(f'/api/admin/next/pages/{id}/preview')['sections']
report['restartExpected']={path:c.req(path) for path in ['/api/admin/next/pages/1','/api/admin/next/pages/65',f'/api/admin/next/posts/{review}',f'/api/admin/next/posts/{faq}',f'/api/admin/next/posts/{restaurant}',f'/api/admin/next/page-templates/{tid}',f'/api/admin/next/posts/{review}/versions',f'/api/admin/next/pages/65/versions']}
report['restartPublic']={path:c.public(path) for path in ['/pages/1','/pages/65','/menus',f'/posts/{review}',f'/posts/{faq}',f'/posts/{restaurant}']}
passed('기존 메뉴 유지, 페이지 1·65 미리보기, 재시작 비교 기준점 저장')
