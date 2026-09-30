import type {EditorGuard,GuardRegistration} from './editorGuard';
import {useCallback,useEffect,useRef,useState} from 'react';
import type {ViewMode} from './types';
import {workspaceHome,workspaceView} from './navigation';
import {rememberPostOrigin} from './contentNavigation';
import {ADMIN_BASE,routePath} from './adminBase';

export type Route={path:string;query:string};
type PendingNavigation={path:string;replace:boolean;view:ViewMode;historyIndex:number|null};
const readRoute=(mode:ViewMode):Route=>({path:routePath(location.pathname)||workspaceHome(mode),query:location.search});
const indexOf=(state:unknown):number|null=>typeof state==='object'&&state!==null&&'aicaNextIndex' in state&&Number.isInteger(state.aicaNextIndex)?state.aicaNextIndex as number:null;

// Keep visited editor instances; only location and selection change during navigation.
export function useWorkspaceRoutes(mode:ViewMode,setMode:(mode:ViewMode)=>void) {
 const [route,setRoute]=useState<Route>(()=>readRoute(mode)),[visited,setVisited]=useState<Record<string,Route>>(()=>{const r=readRoute(mode);return {[r.path]:r};});
 const [pendingNavigation,setPendingNavigation]=useState<PendingNavigation|null>(null);
 const [postOrigins,setPostOrigins]=useState<Record<string,string>>({});
 const current=useRef(route),positions=useRef<Record<string,number>>({}),guards=useRef<Record<string,EditorGuard>>({});
 const index=useRef(indexOf(history.state)??0),repairing=useRef<PendingNavigation|null>(null),allowedPop=useRef(false);
 const registerGuard=useCallback((path:string,guard:EditorGuard|null)=>{if(guard)guards.current[path]=guard;else delete guards.current[path];},[]);
 const remember=useCallback((next:Route,recordOrigin=false)=>{
  const previous=current.current;positions.current[previous.path]=window.scrollY;current.current=next;setRoute(next);setVisited(old=>({...old,[next.path]:next}));
  if(recordOrigin)setPostOrigins(old=>rememberPostOrigin(old,previous,next));
  if(previous.path!==next.path&&!new URLSearchParams(next.query).has('block'))requestAnimationFrame(()=>window.scrollTo(0,positions.current[next.path]||0));
 },[]);
 const navigate=useCallback((path:string,replace=false,confirmed=false,view=mode)=>{
  view=workspaceView(path,view);
  const url=new URL(ADMIN_BASE+path,location.origin);url.searchParams.set('view',view);
  const next={path:routePath(url.pathname),query:url.search};
  if(url.href===location.href)return;
  if(!confirmed&&next.path!==current.current.path&&guards.current[current.current.path]&&guards.current[current.current.path]()!==true){setPendingNavigation({path,replace,view,historyIndex:null});return;}
  if(!replace)index.current++;
  history[replace?'replaceState':'pushState']({...history.state,aicaNextIndex:index.current},'',url);remember(next,true);setMode(view);
 },[mode,remember,setMode]);
 useEffect(()=>{
  history.replaceState({...history.state,aicaNextIndex:index.current},'',location.href);
  const pop=(event:PopStateEvent)=>{
   if(repairing.current){setPendingNavigation(repairing.current);repairing.current=null;return;}
   const requested=new URLSearchParams(location.search).get('view')==='structure'?'structure':'manage';
   const next=readRoute(requested),nextIndex=indexOf(event.state);
   const allow=allowedPop.current;allowedPop.current=false;
   if(!allow&&next.path!==current.current.path&&guards.current[current.current.path]&&guards.current[current.current.path]()!==true){
    const pending:PendingNavigation={path:next.path+next.query,replace:false,view:workspaceView(next.path,requested),historyIndex:nextIndex};
    if(nextIndex!==null&&nextIndex!==index.current){repairing.current=pending;history.go(index.current-nextIndex);}
    else {history.pushState({...history.state,aicaNextIndex:index.current},'',ADMIN_BASE+current.current.path+current.current.query);setPendingNavigation({...pending,historyIndex:null});}
    return;
   }
   index.current=nextIndex??0;remember(next);
   setMode(workspaceView(next.path,requested));
  };
  window.addEventListener('popstate',pop);return()=>window.removeEventListener('popstate',pop);
 },[remember,setMode]);
 useEffect(()=>{
  const url=new URL(location.href);if(url.searchParams.get('view')===mode)return;
  url.searchParams.set('view',mode);history.replaceState({...history.state,aicaNextIndex:index.current},'',url);
  const next=readRoute(mode);current.current=next;setRoute(next);setVisited(old=>({...old,[next.path]:next}));
 },[mode]);
 const cancelNavigation=()=>setPendingNavigation(null);
 const confirmNavigation=()=>{
  const pending=pendingNavigation;if(!pending)return;if(guards.current[current.current.path]?.()==='busy')return;setPendingNavigation(null);
  if(pending.historyIndex!==null&&pending.historyIndex!==index.current){allowedPop.current=true;history.go(pending.historyIndex-index.current);}
  else navigate(pending.path,pending.replace,true,pending.view);
 };
 const allGuards=useCallback(()=>Object.values(guards.current),[]);
 return {route,visited,postOrigins,navigate,registerGuard,pendingNavigation,cancelNavigation,confirmNavigation,allGuards};
}
