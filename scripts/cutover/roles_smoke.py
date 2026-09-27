from http_client import Client
import os
assert os.environ.get('AICA_ISOLATED_SMOKE') == 'true', 'Fixture smoke requires an isolated fork'
import urllib.request,urllib.parse,http.cookiejar,re,json,sys,uuid
from pathlib import Path
c=Client(int(os.environ['AICA_SMOKE_PORT']));out=Path(sys.argv[1]);report={}
def fixture(role):
 email=f'5c1-{role.lower()}-{uuid.uuid4().hex[:6]}@example.test'
 issued=c.req('/admin/accounts',{'email':email,'displayName':'[5C-1 시험] '+role,'role':role},'POST',form=True)['html']
 temporary=re.search(r'<code[^>]*id="temporary-password"[^>]*>([^<]+)</code>',issued).group(1)
 opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 html=opener.open(c.base+'/login').read().decode();token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',html).group(1)
 html=opener.open(c.base+'/login',urllib.parse.urlencode({'username':email,'password':temporary,'_csrf':token}).encode()).read().decode()
 token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',html).group(1);password='Rehearsal-'+uuid.uuid4().hex+'!'
 opener.open(c.base+'/account/password',urllib.parse.urlencode({'currentPassword':temporary,'newPassword':password,'confirmPassword':password,'_csrf':token}).encode()).read()
 return Client(int(os.environ['AICA_SMOKE_PORT']),email,password)
supporter=fixture('SUPPORTER');admin=fixture('ADMIN')
report['roles']={x.bootstrap['user']['role']:x.bootstrap['permissions'] for x in [c,admin,supporter]}
p=supporter.req('/api/admin/next/posts',dict(title='[5C-1 시험] 서포터 초안',content='역할 검증',mediaIds=[],saveIntent='MANUAL_DRAFT'),'POST',expect=201);id=p['id']
p=supporter.req(f'/api/admin/next/posts/{id}',dict(revision=p['revision'],title=p['title'],content='본인 수정',mediaIds=[],saveIntent='MANUAL_DRAFT'),'PUT')
pub=dict(id=id,revision=p['revision'],title=p['title'],content=p['content'],action='publish',saveIntent='MANUAL_DRAFT')
supporter.req('/admin/posts/save-json',pub,'POST',form=True,expect=403)
for suffix in ['unpublish','delete']:supporter.req(f'/admin/posts/{id}/{suffix}',dict(revision=p['revision'],confirmed=True),'POST',form=True,expect=403)
supporter.req('/api/admin/next/posts/33',expect=403);supporter.req('/api/admin/next/pages/65',expect=403)
h=supporter.req(f'/api/admin/next/posts/{id}/versions');assert not h['canRestore'];supporter.req(f'/api/admin/next/posts/{id}/versions/{h["items"][0]["id"]}/restore',{'expectedRevision':p['revision'],'confirmed':True},'POST',expect=403)
report['SUPPORTER']='본인 생성/수정/초안 및 이력조회 허용; 발행/중단/삭제/복구/타인/페이지 차단'
admin.req('/admin/posts/save-json',pub,'POST',form=True);p=admin.req(f'/api/admin/next/posts/{id}');admin.req(f'/admin/posts/{id}/unpublish',{'revision':p['revision']},'POST',form=True);p=admin.req(f'/api/admin/next/posts/{id}');assert p['status']=='PRIVATE';c.public(f'/posts/{id}',expect=404);admin.req(f'/admin/posts/{id}/delete',dict(revision=p['revision'],confirmed=True),'POST',form=True,expect=403)
page=admin.req('/api/admin/next/pages/65');page=admin.req('/api/admin/next/pages/65',dict(revision=page['revision'],title=page['title'],sections=page['sections'],saveIntent='MANUAL_DRAFT'),'PUT')
admin.req('/admin/pages/save-json',dict(id=65,revision=page['revision'],title=page['title'],sectionsJson=json.dumps(page['sections']),action='publish'),'POST',form=True)
page=admin.req('/api/admin/next/pages/65')
admin.req('/admin/pages/save-json',dict(title='불허 페이지',slug='denied-5c1',sectionsJson='[]',action='save'),'POST',form=True,expect=403)
admin.req('/admin/pages/save-json',dict(id=65,revision=page['revision'],title=page['title'],slug='denied-5c1',sectionsJson=json.dumps(page['sections']),action='save'),'POST',form=True,expect=403)
admin.req('/admin/pages/65/delete',dict(revision=page['revision'],confirmed=True),'POST',form=True,expect=403)
for path in ['/api/admin/next/page-templates','/api/admin/next/accounts','/api/admin/next/activity','/api/admin/next/settings/basic']:
 admin.req(path,expect=403)
admin.req('/api/admin/next/menus',dict(label='불허 메뉴',kind='PAGE',targetId=65,visible=True),'POST',expect=403)
report['ADMIN']='전체 콘텐츠 발행/중단·기존 페이지 편집/발행 허용; 새 페이지/slug/영구삭제/템플릿/전역설정/계정/이력 차단'
# Required revision checks and SUPER_ADMIN delete on the disposable supporter fixture.
missing=c.req(f'/admin/posts/{id}/unpublish',{},'POST',form=True)
assert '저장 버전이 필요합니다' in missing['html'] and c.req(f'/api/admin/next/posts/{id}')==p
c.req(f'/admin/posts/{id}/delete',{'confirmed':True},'POST',form=True,expect=400)
c.req(f'/admin/posts/{id}/delete',{'revision':p['revision'],'confirmed':True},'POST',form=True)
report['SUPER_ADMIN']='계정 발급·메뉴/설정/템플릿/이력 조회·fixture 영구삭제 허용; revision 누락 시 삭제 400, 공개중단 오류 안내 redirect 및 데이터 불변'
for path in ['/api/admin/next/menus','/api/admin/next/settings/basic','/api/admin/next/accounts','/api/admin/next/activity']:c.req(path)
out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('PASS role permission matrix and required revision checks')
