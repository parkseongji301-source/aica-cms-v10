import type {EditorGuard,GuardRegistration} from './editorGuard';
import {useCallback,useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import type {Bootstrap,Go,PageDocument,PostDocument,ViewMode,ClassificationCatalog,ComponentDefinition,PageTarget} from './types';
import {bootstrap,getPage,get} from './api';
import {TemplatesPanel} from './PageTemplates';
import {PageEditor} from './PageEditor';
import {ContentPanel} from './ContentPanel';
import {AccountsPanel,ActivityPanel,DashboardPanel,PagesPanel,PostsPanel,RolesPanel} from './ReadPanels';
import {LinkManager,MediaPanel,SettingsPanel} from './EditPanels';
import {canOpen,contentPath,destination,entries,managementGroups,pagePath} from './navigation';
import {Empty,Feedback,Heading,messageOf,useRemote,setOperatingZone} from './ui';
import {ReviewTree} from './ReviewTree';
import {useWorkspaceRoutes} from './useWorkspaceRoutes';
import {PageStructureBranch} from './PageStructureBranch';
import {BlockDialog} from './BlockDialog';
import {ContentTree} from './ContentTree';
import {contentSections,sectionType,returnSectionPath} from './contentNavigation';
import './styles.css';
import './workspace.css';

function initialMode(userId:number):ViewMode {
  const value=new URLSearchParams(location.search).get('view');
  if(value==='manage'||value==='structure')return value;
  try{return sessionStorage.getItem('aica-next-view-'+userId)==='structure'?'structure':'manage';}catch{return 'manage';}
}
function PagePanel({id,active,data,onTitle,go,blockId,viewMode,onSelectBlock,onOutline,registerGuard}:{id:number;active:boolean;data:Bootstrap;onTitle:(title:string)=>void;go:Go;blockId:string|null;viewMode:ViewMode;onSelectBlock:(id:string|null,replace?:boolean)=>void;onOutline:(outline:PageTarget)=>void;registerGuard:(path:string,guard:EditorGuard|null)=>void}) {
  const onGuard=useCallback((guard:EditorGuard|null)=>registerGuard(pagePath(id),guard),[id,registerGuard]);
  const [definitions,setDefinitions]=useState<ComponentDefinition[]>([]);
  const [document,setDocument]=useState<PageDocument|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    if(document||!active)return;let cancelled=false;
    void Promise.all([getPage(id),get<ComponentDefinition[]>('/page-components')]).then(([value,catalog])=>{if(!cancelled){setDefinitions(catalog);setDocument(value);}}).catch(e=>{if(!cancelled)setError(e.message);});
    return()=>{cancelled=true;};
  },[id,active,document,retry]);
  return document?<PageEditor templateUse={data.permissions.templateUse} templateManage={data.permissions.templateManage} blockId={blockId} viewMode={viewMode} onSelectBlock={onSelectBlock} onOutline={onOutline} onGuard={onGuard} initial={document} definitions={definitions} active={active} categories={data.categories} images={data.images} onTitle={onTitle} onContent={category=>go(contentPath(category))}/>:<><Feedback error={error} loading={!error}/>{error&&<button onClick={()=>{setError('');setRetry(n=>n+1);}}>다시 시도</button>}</>;
}

function Workspace({initial}:{initial:Bootstrap}) {
  setOperatingZone(initial.timeZone);
  const [data,setData]=useState(initial),[mode,setMode]=useState<ViewMode>(()=>initialMode(initial.user.id));
  const {route,visited,navigate,registerGuard,pendingNavigation,cancelNavigation,confirmNavigation}=useWorkspaceRoutes(mode,setMode);
  const go:Go=path=>{navigate(path);setSidebarOpen(false);};
  const [liveOutlines,setLiveOutlines]=useState<Record<number,PageTarget>>({});
  const updatedOutline=useCallback((value:PageTarget)=>setLiveOutlines(old=>({...old,[value.pageId]:value})),[]);
  const [version,setVersion]=useState(0),[sidebarOpen,setSidebarOpen]=useState(false),[navigationError,setNavigationError]=useState('');
  const [contentTargets,setContentTargets]=useState<Record<number,{title:string;categoryId:number|null}>>({});
  const loadedContent=(post:PostDocument)=>setContentTargets(old=>({...old,[post.id]:{title:post.title,categoryId:post.categoryId}}));
  const refresh=useCallback(()=>setVersion(v=>v+1),[]);
  useEffect(()=>{
    let cancelled=false;
    void bootstrap().then(value=>{if(!cancelled){setData(value);setNavigationError('');}}).catch(e=>{if(!cancelled)setNavigationError(messageOf(e));});
    return()=>{cancelled=true;};
  },[version]);
  useEffect(()=>{window.addEventListener('focus',refresh);return()=>window.removeEventListener('focus',refresh);},[refresh]);
  useEffect(()=>{try{sessionStorage.setItem('aica-next-view-'+data.user.id,mode);}catch{}},[mode,data.user.id]);
  const structure=useRemote<PageTarget[]>('/page-structure',data.permissions.site&&mode==='structure',version);
  const outlineFor=(id:number)=>liveOutlines[id]??structure.data?.find(p=>p.pageId===id);
  const navType=sectionType(new URLSearchParams(route.query));
  const reviewCatalog=useRemote<ClassificationCatalog>('/classifications',mode==='structure'||!!navType,version);
  const reviewKey=new URLSearchParams(route.query).get('reviewSection');
  const faqKey=new URLSearchParams(route.query).get('faqSection');
  const restaurantKey=new URLSearchParams(route.query).get('restaurantSection');
  const section=navType?contentSections[navType]:null,sectionKey=section?new URLSearchParams(route.query).get(section.param):null;
  const activePage=/^\/pages\/(\d+)\/edit$/.exec(route.path);
  const activeBlock=new URLSearchParams(route.query).get('block');
  const activePageId=activePage?Number(activePage[1]):null;
  const activePost=/^\/posts\/(\d+)\/edit$/.exec(route.path);
  const title=activePost?contentTargets[Number(activePost[1])]?.title||'콘텐츠 편집':activePage?data.pages.find(p=>p.id===Number(activePage[1]))?.title||'페이지 편집':route.path==='/posts'&&section?(section.label+(sectionKey==='all'?'':' · '+(section.nodes.find(n=>n.key===sectionKey)?.label||''))):entries.find(e=>e.path===route.path)?.label||'화면을 찾을 수 없습니다';
  useEffect(()=>{document.title=title+' · AICA 관리센터';},[title]);
  const linkedPages=new Set(data.menus.filter(m=>m.kind==='PAGE').map(m=>m.targetId));
  const linkedCategories=new Set(data.menus.filter(m=>m.kind==='CATEGORY').map(m=>m.targetId));
  const selected=(path:string)=>{
    const [pathname,query]=path.split('?');
    if(activePost&&navType&&pathname==='/posts')return false;
    if(route.path==='/posts'&&navType&&pathname==='/posts')return false;
    if(activePost&&pathname==='/posts'){const category=contentTargets[Number(activePost[1])]?.categoryId;return category!=null&&new URLSearchParams(query).get('categoryId')===String(category);}
    if(route.path!==pathname)return false;
    const target=new URLSearchParams(query),current=new URLSearchParams(route.query);
    return ['categoryId','edit'].every(key=>target.get(key)===current.get(key));
  };
  const savedPage=(id:number,title:string)=>{
    setData(old=>({...old,pages:old.pages.map(p=>p.id===id?{...p,title}:p),menus:old.menus.map(m=>m.kind==='PAGE'&&m.targetId===id?{...m,label:title}:m)}));refresh();
  };
  return <div className="next-admin phase-two">
    <a className="skip-link" href="#next-workspace">본문으로 바로가기</a>
    <header className="topbar"><button className="mobile-menu" aria-label="탐색 메뉴" aria-expanded={sidebarOpen} onClick={()=>setSidebarOpen(!sidebarOpen)}>☰</button><div className="view-switch" role="group" aria-label="탐색 방식"><button aria-pressed={mode==='manage'} onClick={()=>setMode('manage')}>사이트 관리</button><button aria-pressed={mode==='structure'} onClick={()=>setMode('structure')}>사이트 구조</button></div><div className="topbar-right"><span>{data.user.roleLabel} · {data.user.name}</span><a href="/admin" target="_blank" rel="noopener noreferrer">기존 관리자 ↗</a><button className="text-link" onClick={refresh} aria-label="메뉴와 데이터 새로고침">새로고침</button></div></header>
    <aside className={'sidebar'+(sidebarOpen?' sidebar-open':'')}><div className="brand"><span className="brand-icon">A</span><div>AICA<small>인공지능 사관학교</small></div></div><div className="sidebar-caption">{mode==='manage'?'사이트 관리':'사이트 구조'}</div><div className="sidebar-scroll">
    {mode==='manage'?<nav aria-label="사이트 관리">{managementGroups.map((group,index)=><div className="nav-group" key={index}>{group.label&&<h2>{group.label}</h2>}{group.items.map(item=><button key={item.path} className={route.path===item.path||item.path==='/pages'&&activePage||item.path==='/posts'&&activePost?'selected':''} aria-current={route.path===item.path?'page':undefined} disabled={!!item.access&&!data.permissions[item.access]} title={item.access&&!data.permissions[item.access]?'이 계정에는 권한이 없습니다.':undefined} onClick={()=>go(item.path)}>{item.label}{item.access&&!data.permissions[item.access]&&<small>권한 없음</small>}</button>)}</div>)}</nav>:<nav aria-label="사이트 구조">
      {!data.permissions.site?<p className="nav-note">사이트 구조 조회 권한이 없습니다.</p>:<>
      <ReviewTree catalog={reviewCatalog.data} selected={route.path==='/posts'||activePost?reviewKey:null} go={go} error={reviewCatalog.error}/>
      <ContentTree type="FAQ" catalog={reviewCatalog.data} selected={route.path==='/posts'||activePost?faqKey:null} go={go} error=""/>
      <ContentTree type="RESTAURANT" catalog={reviewCatalog.data} selected={route.path==='/posts'||activePost?restaurantKey:null} go={go} error=""/>
      {structure.error&&<p className="nav-note" role="alert">블록 탐색을 불러오지 못했습니다. {structure.error}</p>}
      <h2>홈페이지 메뉴</h2><ul className="structure-tree">{data.menus.map(menu=>menu.kind==='PAGE'&&menu.targetId!==null?<PageStructureBranch key={menu.id} id={menu.targetId} label={menu.label} hidden={!menu.visible} outline={outlineFor(menu.targetId)} activePage={activePageId} activeBlock={activeBlock} go={go}/>:<li key={menu.id}><button disabled={menu.kind==='LINK'&&!data.permissions.structure} className={selected(destination(menu))?'selected':''} data-menu-id={menu.id} data-category-id={menu.kind==='CATEGORY'?menu.targetId:undefined} onClick={()=>go(destination(menu))}><span className="tree-line">└</span><span>{menu.label}<small>{menu.kind==='CATEGORY'?'콘텐츠 목록':menu.kind==='LINK'?'직접 링크':''}{!menu.visible?' · 숨김':''}</small></span></button></li>)}</ul>{!data.menus.length&&<p className="nav-note">등록된 메뉴 없음</p>}
      {data.pages.some(p=>!linkedPages.has(p.id))&&<><h2>메뉴 밖 페이지</h2><ul className="structure-tree">{data.pages.filter(p=>!linkedPages.has(p.id)).map(p=><PageStructureBranch key={p.id} id={p.id} label={p.title} outline={outlineFor(p.id)} activePage={activePageId} activeBlock={activeBlock} go={go}/>)}</ul></>}
      {data.categories.some(c=>!linkedCategories.has(c.id))&&<><h2>기존 카테고리</h2><p className="nav-note">방문자 메뉴에 연결되지 않은 카테고리</p>{data.categories.filter(c=>!linkedCategories.has(c.id)).map(c=><button key={c.id} data-category-id={c.id} className={selected(contentPath(c.id))?'selected':''} onClick={()=>go(contentPath(c.id))}>{c.name}</button>)}</>}
      {!data.pages.length&&!data.categories.length&&!data.menus.length&&<p className="nav-note">연결할 원본이 아직 없습니다.</p>}
      </>}
    </nav>}</div><div className="sidebar-footer"><span>홈페이지 연결 전</span><small>동일한 원본 · 두 가지 탐색</small></div></aside>
    <main id="next-workspace"><div className="breadcrumbs"><span>{mode==='manage'?'사이트 관리':'사이트 구조'}</span><span>/</span><span>{title}</span></div><Feedback error={navigationError}/>
      {Object.entries(visited).map(([key,savedRoute])=>{
        const active=key===route.path,match=/^\/pages\/(\d+)\/edit$/.exec(key),postMatch=/^\/posts\/(\d+)\/edit$/.exec(key);
        const props={active,version,data,go,refresh,search:new URLSearchParams(savedRoute.query)};
        let panel;
        if(!canOpen(key,data))panel=<><Heading title={entries.some(e=>e.path===key)||match?'접근 권한이 없습니다.':'화면을 찾을 수 없습니다.'}/><Empty>사이트 관리에서 사용할 수 있는 메뉴를 선택하세요.</Empty></>;
        else if(match)panel=<PagePanel blockId={new URLSearchParams(savedRoute.query).get('block')} viewMode={mode} onSelectBlock={(block,replace)=>navigate(pagePath(Number(match[1]),block),replace)} onOutline={updatedOutline} registerGuard={registerGuard} id={Number(match[1])} active={active} data={data} go={go} onTitle={name=>savedPage(Number(match[1]),name)}/>;
        else if(postMatch)panel=<ContentPanel registerGuard={registerGuard} canPublish={!!data.permissions.publish} id={Number(postMatch[1])} active={active} categories={data.categories} onLoaded={loadedContent} onSaved={post=>{loadedContent(post);refresh();}} onList={()=>go(visited['/posts']?'/posts'+visited['/posts'].query:returnSectionPath(new URLSearchParams(savedRoute.query),reviewCatalog.data)||'/posts')} onMediaChange={refresh}/>;
        else switch(key) {
          case '/dashboard':panel=<DashboardPanel {...props}/>;break;
          case '/posts':panel=<PostsPanel {...props}/>;break;
          case '/pages':panel=<PagesPanel {...props}/>;break;
          case '/media':panel=<MediaPanel {...props}/>;break;
          case '/menus':panel=<LinkManager {...props} type="menus"/>;break;
          case '/settings/links':panel=<LinkManager {...props} type="links"/>;break;
          case '/design/style':panel=<SettingsPanel {...props} group="style"/>;break;
          case '/design/templates':panel=<TemplatesPanel registerGuard={registerGuard} active={active} version={version} categories={data.categories}/>;break;
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
    {pendingNavigation&&<BlockDialog title="다른 화면으로 이동" onClose={cancelNavigation}><p>저장되지 않은 입력 또는 진행 중인 저장이 있습니다. 저장·업로드 중에는 완료 후 이동할 수 있습니다. 미저장 입력은 이 브라우저 안에 유지되지만 숨겨진 편집기의 자동저장은 멈춥니다. 이동할까요?</p><div className="dialog-actions"><button type="button" autoFocus onClick={cancelNavigation}>계속 편집</button><button type="button" className="primary" onClick={confirmNavigation}>이동</button></div></BlockDialog>}
  </div>;
}
function App() {
  const [data,setData]=useState<Bootstrap|null>(null),[error,setError]=useState('');
  useEffect(()=>{void bootstrap().then(setData).catch(e=>setError(e.message));},[]);
  if(error)return <div className="boot-state"><h1>관리 화면을 열 수 없습니다</h1><p role="alert">{error}</p><a href="/login">로그인</a><a href="/admin">기존 관리자</a></div>;
  return data?<Workspace initial={data}/>:<div className="boot-state" role="status">관리 화면을 불러오고 있습니다…</div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
