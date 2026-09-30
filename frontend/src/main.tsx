import type {EditorGuard,GuardRegistration} from './editorGuard';
import {useCallback,useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import type {Bootstrap,Go,PageDocument,PostDocument,ViewMode,ClassificationCatalog,ComponentDefinition,PageTarget} from './types';
import {bootstrap,getPage,get,logout} from './api';
import {unsavedState} from './logout';
import {TemplatesPanel} from './PageTemplates';
import {WritingTemplatesPanel} from './WritingTemplatesPanel';
import {PageEditor} from './PageEditor';
import {ContentPanel} from './ContentPanel';
import {TrashPanel} from './TrashPanel';
import {ActivityPanel,DashboardPanel,PagesPanel,PostsPanel,RolesPanel} from './ReadPanels';
import {AccountsPanel} from './AccountManager';
import {LinkManager,MediaPanel,SettingsPanel} from './EditPanels';
import {canOpen,contentPath,entries,managementGroups,pagePath,pageOverviewPath,pageOverviewId,workspaceHome,workspaceView} from './navigation';
import {Empty,Feedback,Heading,messageOf,useRemote,setOperatingZone} from './ui';
import {useWorkspaceRoutes} from './useWorkspaceRoutes';
import {routePath} from './adminBase';
import {pageLocation,publishedChildren} from './pageHierarchy';
import {PageOverview} from './PageOverview';
import {BlockDialog} from './BlockDialog';
import {ContentTree} from './ContentTree';
import {NavigationIcon,managementIcons} from './NavigationIcon';
import {WorkspaceBreadcrumbs} from './WorkspaceBreadcrumbs';
import {contentContext,hasContentLocation,returnSectionPath} from './contentNavigation';
import './styles.css';
import './workspace.css';
import './design.css';
import './posts-list.css';
import './writing-workspace.css';
import './workspace-shell.css';

import './pages-ux.css';
import './operations-ux.css';
import './admin-settings-ux.css';
import './calm-shell.css';
import './refined.css';

function initialMode(userId:number):ViewMode {
  const value=new URLSearchParams(location.search).get('view');
  let requested:ViewMode=value==='structure'?'structure':'manage';
  if(value!=='manage'&&value!=='structure')try{requested=sessionStorage.getItem('aica-next-view-'+userId)==='structure'?'structure':'manage';}catch{}
  const path=routePath(location.pathname);
  return path?workspaceView(path,requested):requested;
}
function PagePanel({id,active,data,onTitle,go,blockId,viewMode,onSelectBlock,onOutline,registerGuard}:{id:number;active:boolean;data:Bootstrap;onTitle:(title:string)=>void;go:Go;blockId:string|null;viewMode:ViewMode;onSelectBlock:(id:string|null,replace?:boolean)=>void;onOutline:(outline:PageTarget)=>void;registerGuard:(path:string,guard:EditorGuard|null)=>void}) {
  const onGuard=useCallback((guard:EditorGuard|null)=>registerGuard(pagePath(id),guard),[id,registerGuard]);
  const [definitions,setDefinitions]=useState<ComponentDefinition[]>([]);
  const [document,setDocument]=useState<PageDocument|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    if(document||!active||data.pages.find(p=>p.id===id)?.areaKind==='GROUP')return;let cancelled=false;
    void Promise.all([getPage(id),get<ComponentDefinition[]>('/page-components')]).then(([value,catalog])=>{if(!cancelled){setDefinitions(catalog);setDocument(value);}}).catch(e=>{if(!cancelled)setError(e.message);});
    return()=>{cancelled=true;};
  },[id,active,document,retry]);
  const row=data.pages.find(p=>p.id===id),parent=row?.parentId==null?null:data.pages.find(p=>p.id===row.parentId)??null;
  const location={parent:parent?pageLocation(data.pages,parent.id):null,parentPublished:!parent||parent.status==='PUBLISHED',publishedChildren:publishedChildren(data.pages,id)};
  if(row?.areaKind==='GROUP')return <><Heading title={row.title}/><Empty><p>묶음은 화면이 없는 구조 항목이라 편집할 내용이 없습니다. 이름·위치·메뉴는 사이트 구조에서 바꿉니다.</p><button type="button" className="secondary" onClick={()=>go('/pages')}>사이트 구조로</button></Empty></>;
  return document?<PageEditor location={location} canChangeAddress={!!data.permissions.structure} templateUse={data.permissions.templateUse} templateManage={data.permissions.templateManage} blockId={blockId} viewMode={viewMode} onSelectBlock={onSelectBlock} onOutline={onOutline} onGuard={onGuard} initial={document} definitions={definitions} active={active} categories={data.categories} images={data.images} onTitle={onTitle} onContent={category=>go(contentPath(category))}/>:<><Feedback error={error} loading={!error}/>{error&&<button onClick={()=>{setError('');setRetry(n=>n+1);}}>다시 시도</button>}</>;
}

function Workspace({initial}:{initial:Bootstrap}) {
  setOperatingZone(initial.timeZone);
  const [data,setData]=useState(initial),[mode,setMode]=useState<ViewMode>(()=>initialMode(initial.user.id));
  const {route,visited,postOrigins,navigate,registerGuard,pendingNavigation,cancelNavigation,confirmNavigation,allGuards}=useWorkspaceRoutes(mode,setMode);
  const [logoutNotice,setLogoutNotice]=useState<''|'busy'|'dirty'|'failed'>(''),[loggingOut,setLoggingOut]=useState(false);
  // Logging out never discards editor input: unsaved or in-flight work blocks it with an explanation.
  const signOut=async()=>{
    const state=unsavedState(allGuards());if(state!=='clean'){setLogoutNotice(state);return;}
    setLoggingOut(true);
    try{if(await logout()){location.replace('/login?logout');return;}setLogoutNotice('failed');}catch{setLogoutNotice('failed');}
    setLoggingOut(false);
  };
  const go:Go=path=>{navigate(path);setSidebarOpen(false);};
  const [liveOutlines,setLiveOutlines]=useState<Record<number,PageTarget>>({});
  const updatedOutline=useCallback((value:PageTarget)=>setLiveOutlines(old=>({...old,[value.pageId]:value})),[]);
  const [version,setVersion]=useState(0),[sidebarOpen,setSidebarOpen]=useState(false),[navigationError,setNavigationError]=useState('');
  const menuToggle=useRef<HTMLButtonElement>(null),sidebar=useRef<HTMLElement>(null);
  const closeSidebar=()=>{setSidebarOpen(false);menuToggle.current?.focus();};
  useEffect(()=>{
    if(!sidebarOpen)return;
    sidebar.current?.querySelector<HTMLButtonElement>('.sidebar-dismiss')?.focus();
    const escape=(event:KeyboardEvent)=>{if(event.key==='Escape'){setSidebarOpen(false);menuToggle.current?.focus();}};
    window.addEventListener('keydown',escape);return()=>window.removeEventListener('keydown',escape);
  },[sidebarOpen]);
  const [contentTargets,setContentTargets]=useState<Record<number,{title:string;categoryId:number|null}>>({});
  const loadedContent=(post:PostDocument)=>setContentTargets(old=>({...old,[post.id]:{title:post.title,categoryId:post.categoryId}}));
  const refresh=useCallback(()=>setVersion(v=>v+1),[]);
  const [postEpochs,setPostEpochs]=useState<Record<number,number>>({});
  const trashChanged=(id:number)=>{setPostEpochs(old=>({...old,[id]:(old[id]||0)+1}));refresh();};
  const trashed=(id:number)=>{trashChanged(id);navigate('/trash',false,true);setSidebarOpen(false);};
  useEffect(()=>{
    let cancelled=false;
    void bootstrap().then(value=>{if(!cancelled){setData(value);setNavigationError('');}}).catch(e=>{if(!cancelled)setNavigationError(messageOf(e));});
    return()=>{cancelled=true;};
  },[version]);
  useEffect(()=>{window.addEventListener('focus',refresh);return()=>window.removeEventListener('focus',refresh);},[refresh]);
  useEffect(()=>{try{sessionStorage.setItem('aica-next-view-'+data.user.id,mode);}catch{}},[mode,data.user.id]);
  const structure=useRemote<PageTarget[]>('/page-structure',data.permissions.site&&mode==='structure',version);
  // 콘텐츠 작업 locations follow the site composition (representative work areas).
  const areas=data.contentAreas??[];
  const navType=hasContentLocation(new URLSearchParams(route.query));
  const reviewCatalog=useRemote<ClassificationCatalog>('/classifications',mode==='structure'||!!navType,version);
  const contentLocation=navType?contentContext(new URLSearchParams(route.query),reviewCatalog.data,areas):null;
  const activePage=/^\/pages\/(\d+)\/edit$/.exec(route.path);
  const activeBlock=new URLSearchParams(route.query).get('block');
  const inspectedPageId=mode==='structure'&&route.path==='/pages'?pageOverviewId(new URLSearchParams(route.query)):null;
  const activePageId=activePage?Number(activePage[1]):inspectedPageId;
  const activePost=/^\/posts\/(\d+)\/edit$/.exec(route.path);
  const listForPost=(path:string,query:string)=>postOrigins[path]||returnSectionPath(new URLSearchParams(query),reviewCatalog.data,areas)||'/posts';
  const activeListSearch=new URLSearchParams(activePost?listForPost(route.path,route.query).split('?')[1]:route.query);
  const wholeContentSelected=(route.path==='/posts'||!!activePost)&&!navType&&!activeListSearch.has('categoryId');
  const title=activePost?contentTargets[Number(activePost[1])]?.title||'글 편집':activePageId!==null?data.pages.find(p=>p.id===activePageId)?.title||(activePage?'페이지 편집':'블록 보기'):route.path==='/posts'&&contentLocation&&!contentLocation.error?(contentLocation.areaLabel+(contentLocation.key==='all'?'':' · '+contentLocation.label)):entries.find(e=>e.path===route.path)?.label||'화면을 찾을 수 없습니다';
  const editPage=(id:number,block?:string)=>{navigate(pagePath(id,block),false,false,'manage');setSidebarOpen(false);};
  const switchMode=(next:ViewMode)=>{
    if(next===mode)return;
    if(next==='manage'&&inspectedPageId!==null)navigate(pagePath(inspectedPageId,activeBlock),false,false,next);
    else navigate(workspaceHome(next),false,false,next);
    setSidebarOpen(false);
  };
  useEffect(()=>{document.title=title+' · AICA 관리센터';},[title]);
  const selected=(path:string)=>{
    const [pathname,query]=path.split('?');
    if(activePost&&navType&&pathname==='/posts')return false;
    if(route.path==='/posts'&&navType&&pathname==='/posts')return false;
    if(activePost&&pathname==='/posts')return activeListSearch.get('categoryId')===new URLSearchParams(query).get('categoryId');
    if(route.path!==pathname)return false;
    const target=new URLSearchParams(query),current=new URLSearchParams(route.query);
    return ['categoryId','edit'].every(key=>target.get(key)===current.get(key));
  };
  const savedPage=(id:number,title:string)=>{
    setData(old=>({...old,pages:old.pages.map(p=>p.id===id?{...p,title}:p),menus:old.menus.map(m=>m.kind==='PAGE'&&m.targetId===id?{...m,label:title}:m)}));refresh();
  };
  return <div className="next-admin phase-two">
    <a className="skip-link" href="#next-workspace">본문으로 바로가기</a>
    <header className="topbar"><button ref={menuToggle} className="mobile-menu" aria-label={sidebarOpen?'탐색 메뉴 닫기':'탐색 메뉴 열기'} aria-controls="workspace-sidebar" aria-expanded={sidebarOpen} onClick={()=>setSidebarOpen(!sidebarOpen)}>☰</button><span className="topbar-title"><NavigationIcon name={mode==='manage'?'dashboard':'content'}/>{mode==='manage'?'사이트 관리':'콘텐츠 작업'}</span><div className="topbar-right"><span className="operator-info"><span className="operator-role">{data.user.roleLabel}</span><span className="operator-avatar" aria-hidden="true">{data.user.name.slice(0,1)}</span><span>{data.user.name}</span></span><a href="/account/password" title="본인 비밀번호 변경">비밀번호 변경</a><button className="text-link" onClick={refresh} aria-label="메뉴와 데이터 새로고침">새로고침</button><button type="button" className="text-link logout-link" disabled={loggingOut} onClick={()=>void signOut()}>{loggingOut?'로그아웃 중…':'로그아웃'}</button></div></header>
    {sidebarOpen&&<div className="sidebar-backdrop" aria-hidden="true" onClick={closeSidebar}/>}
    <aside ref={sidebar} id="workspace-sidebar" className={'sidebar'+(sidebarOpen?' sidebar-open':'')} aria-label="워크스페이스 탐색"><button type="button" className="sidebar-dismiss" onClick={closeSidebar}>메뉴 닫기</button><button type="button" className="brand brand-home" aria-label={`AICA ${mode==='manage'?'사이트 관리':'콘텐츠 작업'} 홈으로 이동`} title="현재 작업 영역의 첫 화면" onClick={()=>go(workspaceHome(mode))}><span className="brand-icon" aria-hidden="true">A</span><span className="brand-name">AICA<small>인공지능 사관학교</small></span></button>
    <div className="sidebar-view">
      <div className="view-switch" role="group" aria-label="탐색 방식">
        <button aria-pressed={mode==='manage'} onClick={()=>switchMode('manage')}>사이트 관리</button>
        <button aria-pressed={mode==='structure'} onClick={()=>switchMode('structure')}>콘텐츠 작업</button>
      </div>
    </div>
    <div className="sidebar-scroll">
    {mode==='manage'?<nav className="management-navigation" aria-label="사이트 관리">{managementGroups.map((group,index)=><div className={'nav-group'+(group.label?' nav-group-labeled':'')} key={index}>{group.label&&<h2><NavigationIcon name={managementIcons[group.label]??'folder'}/><span>{group.label}</span></h2>}{group.items.map(item=><button key={item.path} className={route.path===item.path||item.path==='/pages'&&activePage||item.path==='/posts'&&activePost?'selected':''} aria-current={route.path===item.path?'page':undefined} disabled={!!item.access&&!data.permissions[item.access]} title={item.access&&!data.permissions[item.access]?'이 계정에는 권한이 없습니다.':undefined} onClick={()=>go(item.path)}>{!group.label&&<NavigationIcon name={item.path==='/dashboard'?'dashboard':'link'}/>}<span className="nav-item-copy"><span>{item.label}</span>{item.access&&!data.permissions[item.access]&&<small>권한 없음</small>}</span></button>)}</div>)}</nav>:<nav className="structure-navigation" aria-label="콘텐츠 작업">
      <button className={'content-work-home'+(wholeContentSelected?' selected':'')} aria-current={wholeContentSelected?'page':undefined} onClick={()=>go('/posts')}><NavigationIcon name="content"/><span className="nav-item-copy"><span>전체 글</span></span></button>
      {data.permissions.site&&<>
      {areas.map((area,index)=><ContentTree key={area.pageId} area={area} areas={areas} catalog={reviewCatalog.data} selected={area.typeCode?((route.path==='/posts'||activePost)&&contentLocation?.pageId===area.pageId?contentLocation.key:null):(activePage&&Number(activePage[1])===area.pageId?'all':null)} go={go} error={index===0?reviewCatalog.error:''}/>)}
      </>}
    </nav>}</div></aside>
    <main id="next-workspace"><WorkspaceBreadcrumbs mode={mode} path={route.path} query={route.query} title={title} catalog={reviewCatalog.data} areas={areas} listPath={listForPost(route.path,route.query)} categoryLabel={data.categories.find(c=>String(c.id)===activeListSearch.get('categoryId'))?.name} pageSelected={activePageId!==null} go={go}/><Feedback error={navigationError}/>
      {Object.entries(visited).map(([key,savedRoute])=>{
        const active=key===route.path,match=/^\/pages\/(\d+)\/edit$/.exec(key),postMatch=/^\/posts\/(\d+)\/edit$/.exec(key);
        const inspectedId=mode==='structure'&&key==='/pages'?pageOverviewId(new URLSearchParams(savedRoute.query)):null;
        const props={active,version,data,go,refresh,registerGuard,search:new URLSearchParams(savedRoute.query)};
        let panel;
        // Every signed-in account receives the shell; screens its role cannot use explain why, and their APIs still answer 403.
        if(!canOpen(key,data)){const known=entries.some(e=>e.path===key)||!!match;panel=<><Heading title={known?'접근 권한이 없습니다.':'화면을 찾을 수 없습니다.'}/><Empty><p>{known?'현재 계정의 역할로는 이 화면을 사용할 수 없습니다. 필요하면 최상위 관리자에게 권한을 요청하세요.':'주소를 확인하거나 메뉴에서 사용할 화면을 선택하세요.'}</p><button type="button" className="secondary" onClick={()=>go(workspaceHome(mode))}>{mode==='manage'?'사이트 관리':'콘텐츠 작업'} 홈으로</button></Empty></>;}
        else if(match)panel=<PagePanel blockId={new URLSearchParams(savedRoute.query).get('block')} viewMode={mode} onSelectBlock={(block,replace)=>navigate(pagePath(Number(match[1]),block),replace)} onOutline={updatedOutline} registerGuard={registerGuard} id={Number(match[1])} active={active} data={data} go={go} onTitle={name=>savedPage(Number(match[1]),name)}/>;
        else if(postMatch)panel=<ContentPanel key={postEpochs[Number(postMatch[1])]||0} registerGuard={registerGuard} canPublish={!!data.permissions.publish} canDelete={!!data.permissions.permanentDelete} onTrashed={trashed} id={Number(postMatch[1])} active={active} categories={data.categories} onLoaded={loadedContent} onSaved={post=>{loadedContent(post);refresh();}} onList={()=>go(listForPost(key,savedRoute.query))} onMediaChange={refresh}/>;
        else if(inspectedId!==null)panel=<PageOverview page={data.pages.find(p=>p.id===inspectedId)} menus={data.menus} outline={structure.data?.find(p=>p.pageId===inspectedId)} loading={structure.loading} error={structure.error} blockId={new URLSearchParams(savedRoute.query).get('block')} onSelectBlock={block=>go(pageOverviewPath(inspectedId,block))} onEdit={block=>editPage(inspectedId,block)} onRetry={structure.reload}/>;
        else switch(key) {
          case '/dashboard':panel=<DashboardPanel {...props}/>;break;
          case '/posts':panel=<PostsPanel {...props} onChanged={trashChanged}/>;break;
          case '/trash':panel=<TrashPanel {...props} onChanged={trashChanged} registerGuard={registerGuard}/>;break;
          case '/pages':panel=<PagesPanel {...props} onOverview={id=>{navigate(pageOverviewPath(id),false,false,'structure');setSidebarOpen(false);}}/>;break;
          case '/media':panel=<MediaPanel {...props}/>;break;
          case '/menus':panel=<LinkManager {...props} type="menus"/>;break;
          case '/settings/links':panel=<LinkManager {...props} type="links"/>;break;
          case '/design/style':panel=<SettingsPanel {...props} group="style"/>;break;
          case '/design/templates':panel=<TemplatesPanel registerGuard={registerGuard} active={active} version={version} categories={data.categories}/>;break;
          case '/design/writing-templates':panel=<WritingTemplatesPanel registerGuard={registerGuard} active={active} version={version}/>;break;
          case '/design/components':panel=<SettingsPanel {...props} group="components"/>;break;
          case '/settings/basic':panel=<SettingsPanel {...props} group="basic"/>;break;
          case '/settings/system':panel=<SettingsPanel {...props} group="system"/>;break;
          case '/accounts':panel=<AccountsPanel {...props}/>;break;
          case '/roles':panel=<RolesPanel {...props}/>;break;
          case '/activity':panel=<ActivityPanel {...props}/>;break;
        }
        return <div key={key} hidden={!active} data-screen={key}>{panel}</div>;
      })}
      <footer className="page-footer">© AICA. 인공지능 사관학교</footer>
    </main>
    {logoutNotice&&<BlockDialog title="로그아웃" onClose={()=>setLogoutNotice('')}><p role="alert">{logoutNotice==='busy'?'저장·업로드가 진행 중입니다. 끝난 뒤 로그아웃하세요.':logoutNotice==='dirty'?'저장하지 않은 입력이 있습니다. 입력 중인 화면에서 저장하거나 변경을 취소한 뒤 로그아웃하세요.':'로그아웃하지 못했습니다. 새로고침한 뒤 다시 시도하세요.'}</p><div className="dialog-actions"><button type="button" autoFocus onClick={()=>setLogoutNotice('')}>확인</button></div></BlockDialog>}
    {pendingNavigation&&<BlockDialog title="다른 화면으로 이동" onClose={cancelNavigation}><p>저장되지 않은 입력 또는 진행 중인 저장이 있습니다. 저장·업로드 중에는 완료 후 이동할 수 있습니다. 미저장 입력은 이 브라우저 안에 유지되지만 숨겨진 편집기의 자동저장은 멈춥니다. 이동할까요?</p><div className="dialog-actions"><button type="button" autoFocus onClick={cancelNavigation}>계속 편집</button><button type="button" className="primary" onClick={confirmNavigation}>이동</button></div></BlockDialog>}
  </div>;
}
function App() {
  const [data,setData]=useState<Bootstrap|null>(null),[error,setError]=useState('');
  useEffect(()=>{void bootstrap().then(setData).catch(e=>setError(e.message));},[]);
  if(error)return <div className="boot-state"><h1>관리 화면을 열 수 없습니다</h1><p role="alert">{error}</p><a href="/login">로그인</a></div>;
  return data?<Workspace initial={data}/>:<div className="boot-state" role="status">관리 화면을 불러오고 있습니다…</div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
