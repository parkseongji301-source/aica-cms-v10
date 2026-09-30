import type {ClassificationCatalog,ContentArea,Go,ViewMode} from './types';
import {contentContext,sectionPath} from './contentNavigation';
import {managementGroups,workspaceHome} from './navigation';

export function WorkspaceBreadcrumbs({mode,path,query,title,catalog,areas=[],listPath,categoryLabel,pageSelected,go}:{mode:ViewMode;path:string;query:string;title:string;catalog:ClassificationCatalog|null;areas?:ContentArea[];listPath:string;categoryLabel?:string;pageSelected:boolean;go:Go}) {
  const items:{label:string;path?:string}[]=[{label:mode==='manage'?'사이트 관리':'콘텐츠 작업',path:workspaceHome(mode)}];
  const postEditor=/^\/posts\/\d+\/edit$/.test(path),scope=contentContext(new URLSearchParams(query),catalog,areas),area=scope?areas.find(a=>a.pageId===scope.pageId):undefined;
  if((path==='/posts'||postEditor)&&scope&&area){
    for(const group of area.groups)items.push({label:group});
    items.push({label:area.label,path:sectionPath(area,'all',catalog,areas)||undefined});
    if(scope.key!=='all')items.push({label:scope.label,path:sectionPath(area,scope.key,catalog,areas)||undefined});
    if(postEditor){items[items.length-1].path=listPath;items.push({label:title});}
  }else if(path==='/posts'||postEditor){
    items.push({label:'전체 콘텐츠',path:postEditor&&!categoryLabel?listPath:'/posts'});
    if(categoryLabel)items.push({label:categoryLabel,path:postEditor?listPath:undefined});
    if(postEditor)items.push({label:title});
  }else{
    const group=managementGroups.find(group=>group.items.some(item=>item.path===path||pageSelected&&item.path==='/pages'));
    if(group?.label)items.push({label:group.label});
    if(pageSelected)items.push({label:'전체 페이지 현황',path:'/pages'});
    items.push({label:title});
  }
  return <nav className="workspace-breadcrumbs" aria-label="현재 위치"><ol>{items.map((item,index)=><li key={index}>{index>0&&<span className="breadcrumb-separator" aria-hidden="true">/</span>}{item.path&&index<items.length-1?<button type="button" onClick={()=>go(item.path!)}>{item.label}</button>:<span aria-current={index===items.length-1?'page':undefined}>{item.label}</span>}</li>)}</ol></nav>;
}
