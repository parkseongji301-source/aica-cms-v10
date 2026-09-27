"""Local isolated rehearsal helper; never default to an original server port."""
import urllib.request, urllib.parse, urllib.error, http.cookiejar, re, json, sys
from pathlib import Path

class Client:
    def __init__(self, port, username='1234', password='1234'):
        assert port in (8095,8096,8097), 'Only rehearsal ports are allowed'
        self.base=f'http://127.0.0.1:{port}'
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        page=self.opener.open(self.base+'/login').read().decode()
        token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page).group(1)
        self.opener.open(self.base+'/login',urllib.parse.urlencode({'username':username,'password':password,'_csrf':token}).encode()).read()
        self.csrf=None
        self.bootstrap=self.req('/api/admin/next/bootstrap')
        self.csrf=self.bootstrap['csrf']['token']
    def req(self,path,body=None,method=None,form=False,expect=200):
        headers={}
        if body is not None:
            headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json','X-CSRF-TOKEN':self.csrf}
            body=urllib.parse.urlencode(body,doseq=True).encode() if form else json.dumps(body).encode()
        try:
            with self.opener.open(urllib.request.Request(self.base+path,data=body,headers=headers,method=method),timeout=30) as r:
                status=r.status;data=r.read().decode();url=r.url
        except urllib.error.HTTPError as e:
            status=e.code;data=e.read().decode();url=e.url
        assert status==expect, f'{method} {path}: expected {expect}, got {status}: {data[:1500]}'
        try:return json.loads(data)
        except ValueError:return {'html':data,'url':url,'status':status}
    def public(self,path,expect=200):
        try:
            with urllib.request.urlopen(self.base+'/api/public/v1'+path,timeout=30) as r:status=r.status;data=r.read().decode()
        except urllib.error.HTTPError as e:status=e.code;data=e.read().decode()
        assert status==expect,(status,data)
        return json.loads(data)

if __name__=='__main__':
    c=Client(int(sys.argv[1]));mode=sys.argv[2]
    out={'mode':mode,'role':c.bootstrap.get('user')}
    if mode=='read-smoke':
        for path in ['/admin','/admin/posts','/admin/pages','/admin/menus','/admin-next?view=manage','/admin-next?view=structure']:
            r=c.req(path);assert 'login' not in r.get('url','').split('/')[-1];out[path]='200'
        for id in [1,65]:out[f'page/{id}']=c.req(f'/api/admin/next/pages/{id}')
        out['bootstrap']=c.bootstrap
    elif mode=='baseline':
        out['first']=c.req('/api/admin/next/version-baseline',{'confirmed':True},'POST')
        out['repeat']=c.req('/api/admin/next/version-baseline',{'confirmed':True},'POST')
    else:raise ValueError(mode)
    Path(sys.argv[3]).write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
    print(mode,'passed',out.get('first',''))
