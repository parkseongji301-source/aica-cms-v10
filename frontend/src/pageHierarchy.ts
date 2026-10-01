import type {PageRow} from './types';

// Mirrors the server's PageHierarchy rules so the screen can explain them; the server still decides.
// Current operating limit: three levels (V14). The home page stays top-level. GROUP areas are structure
// nodes only: they can hold areas, but have no screen, publication or home/menu-target role.
export const MAX_PAGE_DEPTH=3;
export const isGroup=(page:PageRow)=>page.areaKind==='GROUP';

export type PageTreeRow={page:PageRow;depth:number;parent:PageRow|null;children:number};

const byId=(pages:PageRow[])=>new Map(pages.map(p=>[p.id,p]));
/** Children in the server's sibling order (sort_order, then id), which the rows already arrive in. */
export function childrenOf(pages:PageRow[],parentId:number|null){return pages.filter(p=>(p.parentId??null)===parentId);}

/** Depth-first rows: each page followed by its children. A missing parent is shown at the top level. */
export function pageTree(pages:PageRow[]):PageTreeRow[] {
  const index=byId(pages),rows:PageTreeRow[]=[],seen=new Set<number>();
  const visit=(page:PageRow,depth:number)=>{
    if(seen.has(page.id))return;seen.add(page.id);
    const children=childrenOf(pages,page.id);
    rows.push({page,depth,parent:page.parentId==null?null:index.get(page.parentId)??null,children:children.length});
    for(const child of children)visit(child,depth+1);
  };
  for(const page of pages)if(page.parentId==null||!index.has(page.parentId))visit(page,1);
  for(const page of pages)visit(page,1);
  return rows;
}
export function depthOf(pages:PageRow[],id:number){
  const index=byId(pages);let depth=0,current:number|null|undefined=id;
  while(current!=null&&depth<=pages.length){depth++;current=index.get(current)?.parentId;}
  return depth;
}
export function heightOf(pages:PageRow[],id:number):number{
  return 1+Math.max(0,...childrenOf(pages,id).map(child=>heightOf(pages,child.id)));
}
/** "상위 › 페이지" */
export function pageLocation(pages:PageRow[],id:number){
  const index=byId(pages),names:string[]=[];let current=index.get(id);
  while(current&&names.length<=pages.length){names.unshift(current.title);current=current.parentId==null?undefined:index.get(current.parentId);}
  return names.join(' › ');
}

export type ParentOption={id:number;title:string;reason:string|null};
/** Candidate parents for a page (null = a page being created), with the reason a candidate cannot be used. */
export function parentOptions(pages:PageRow[],pageId:number|null,homePageId:number|null):ParentOption[] {
  const levels=pageId==null?1:heightOf(pages,pageId);
  return pageTree(pages).map(row=>row.page).filter(p=>p.id!==pageId).map(candidate=>{
    let reason:string|null=null;
    if(pageId!=null&&pageId===homePageId)reason='홈(첫 화면) 페이지는 최상위에 고정됩니다.';
    else if(candidate.id===homePageId)reason='홈(첫 화면) 페이지 아래에는 하위 페이지를 둘 수 없습니다.';
    else if(candidate.inStructure===false)reason='구성에서 제거된 영역 아래로는 옮기거나 추가할 수 없습니다.';
    else if(pageId!=null&&isWithin(pages,candidate.id,pageId))reason='자기 하위 페이지 아래로는 옮길 수 없습니다.';
    else if(depthOf(pages,candidate.id)+levels>MAX_PAGE_DEPTH)reason=levels>1?`최대 ${MAX_PAGE_DEPTH}단계를 넘습니다(이 페이지와 그 하위 페이지).`:`최대 ${MAX_PAGE_DEPTH}단계라 그 아래에는 더 둘 수 없습니다.`;
    return {id:candidate.id,title:pageLocation(pages,candidate.id),reason};
  });
}
function isWithin(pages:PageRow[],candidate:number,ancestor:number){
  const index=byId(pages);let current:number|null|undefined=candidate,steps=0;
  while(current!=null&&steps++<=pages.length){if(current===ancestor)return true;current=index.get(current)?.parentId;}
  return false;
}

/** Warning only: a published child stays public whatever its parent's status is. */
export function parentWarning(pages:PageRow[],page:PageRow):string|null {
  if(page.status!=='PUBLISHED'||page.parentId==null)return null;
  const parent=pages.find(p=>p.id===page.parentId);
  // A GROUP has no screen or publication of its own, so it never makes a child look hidden.
  return parent&&parent.areaKind!=='GROUP'&&parent.status!=='PUBLISHED'?'상위 페이지 비공개':null;
}
export const publishedChildren=(pages:PageRow[],id:number)=>childrenOf(pages,id).filter(p=>p.status==='PUBLISHED').length;

/** Sibling order after moving one page up (-1) or down (+1); null at either end. */
export function movedSiblings(pages:PageRow[],id:number,delta:-1|1):number[]|null {
  const page=pages.find(p=>p.id===id);if(!page)return null;
  const ids=childrenOf(pages,page.parentId??null).map(p=>p.id),at=ids.indexOf(id),to=at+delta;
  if(to<0||to>=ids.length)return null;
  [ids[at],ids[to]]=[ids[to],ids[at]];return ids;
}
/** Selected pages deepest first, so selected children go before their selected parent. */
export function deletionOrder<T extends {id:number}>(selected:T[],pages:PageRow[]):T[] {
  return [...selected].sort((a,b)=>depthOf(pages,b.id)-depthOf(pages,a.id));
}
/** Child uses do not block a parent when every child page is part of the same deletion. */
export function childrenAllSelected(pages:PageRow[],id:number,selectedIds:number[]){
  const children=childrenOf(pages,id);return children.length>0&&children.every(child=>selectedIds.includes(child.id));
}
/** First-screen choices: published top-level pages without child pages (the current value stays selectable). */
export function homeCandidates(pages:PageRow[],current:string){
  return pages.filter(p=>!isGroup(p)&&p.status==='PUBLISHED'&&(p.parentId==null&&childrenOf(pages,p.id).length===0||String(p.id)===current));
}

// 사이트 구조 화면 규칙 (검수 전 UX): top-level items are the fixed site sections. The screen offers no way to
// create, delete, nest or remove them (the server keeps those functions); the home page is completely fixed.
export const isTopLevel=(page:PageRow)=>page.parentId==null;
/** Top-level items that take "+ 하위 페이지": every one except the home page and areas removed from the structure. */
export const childHost=(page:PageRow,homePageId:number|null)=>isTopLevel(page)&&page.id!==homePageId&&page.inStructure!==false;
/** The row a page is listed under at the top: itself for a top-level page or one whose parent is missing. */
export function topAncestorId(pages:PageRow[],id:number):number {
  const index=byId(pages),seen=new Set<number>();
  let current=index.get(id);
  while(current&&current.parentId!=null&&index.has(current.parentId)&&!seen.has(current.id)){seen.add(current.id);current=index.get(current.parentId);}
  return current?.id??id;
}
/** Rows still shown while some top-level items are folded: a folded item keeps its own row and hides everything under it. */
export function unfoldedRows(rows:PageTreeRow[],pages:PageRow[],folded:ReadonlySet<number>):PageTreeRow[] {
  if(!folded.size)return rows;
  return rows.filter(r=>{const top=topAncestorId(pages,r.page.id);return top===r.page.id||!folded.has(top);});
}
/** Rows the screen lets the operator drag: top-level items except home, and second-level pages. */
export function draggable(pages:PageRow[],page:PageRow,homePageId:number|null){
  if(page.id===homePageId)return false;
  return isTopLevel(page)||depthOf(pages,page.id)===2;
}
export type DropRequest={overId:number;after:boolean};
export type DropPlan={pageId:number;parentId:number|null;ids:number[];moved:boolean};
/**
 * Where a dragged row lands. A top-level item moves only among top-level items and never ahead of the home
 * page. A second-level page moves before/after another second-level page (in that page's parent) or, dropped
 * on a top-level item, to the end of that item's children. Returns null when the drop changes nothing and a
 * message when the screen does not allow it; the server still checks every placement.
 */
export function dropPlan(pages:PageRow[],homePageId:number|null,dragId:number,drop:DropRequest):DropPlan|string|null {
  const dragged=pages.find(p=>p.id===dragId),over=pages.find(p=>p.id===drop.overId);
  if(!dragged||!over)return '목록이 바뀌었습니다. 새로 고친 뒤 다시 옮기세요.';
  if(!draggable(pages,dragged,homePageId))return dragged.id===homePageId?'홈(첫 화면)은 맨 위에 고정됩니다.':'이 페이지는 위치 버튼으로 옮기세요.';
  if(dragged.id===over.id)return null;
  const place=(parentId:number|null,beforeId:number|null)=>{
    const ids=childrenOf(pages,parentId).map(p=>p.id).filter(id=>id!==dragId);
    const at=beforeId==null?ids.length:ids.indexOf(beforeId);ids.splice(at<0?ids.length:at,0,dragId);
    const moved=(dragged.parentId??null)!==parentId,current=childrenOf(pages,parentId).map(p=>p.id);
    if(!moved&&current.join(',')===ids.join(','))return null;
    return {pageId:dragId,parentId,ids,moved};
  };
  const nextSibling=(page:PageRow)=>{const ids=childrenOf(pages,page.parentId??null).map(p=>p.id).filter(id=>id!==dragId);const i=ids.indexOf(page.id);return i<0||i+1>=ids.length?null:ids[i+1];};
  if(isTopLevel(dragged)){
    if(!isTopLevel(over))return '최상위 항목은 최상위 항목 사이에서만 순서를 바꿀 수 있습니다.';
    const plan=place(null,drop.after?nextSibling(over):over.id);
    if(plan&&homePageId!=null&&plan.ids.includes(homePageId)&&plan.ids[0]!==homePageId)return '홈(첫 화면)은 맨 위에 고정됩니다. 그 아래로 옮기세요.';
    return plan;
  }
  if(isTopLevel(over)){
    if(!childHost(over,homePageId))return over.id===homePageId?'홈(첫 화면) 아래에는 하위 페이지를 둘 수 없습니다.':'구성에서 제거된 항목 아래로는 옮길 수 없습니다.';
    if((dragged.parentId??null)===over.id)return null;
    return place(over.id,null);
  }
  if(depthOf(pages,over.id)!==2)return '하위 페이지는 다른 하위 페이지 앞뒤나 최상위 항목 위에 놓으세요.';
  const parent=pages.find(p=>p.id===over.parentId);
  if(!parent||!childHost(parent,homePageId))return '구성에서 제거된 항목 아래로는 옮길 수 없습니다.';
  return place(parent.id,drop.after?nextSibling(over):over.id);
}
/** Keyboard alternative for a second-level page: its possible parents are the top-level items only. */
export function childParentOptions(pages:PageRow[],pageId:number,homePageId:number|null):ParentOption[] {
  return parentOptions(pages,pageId,homePageId).filter(option=>{const p=pages.find(x=>x.id===option.id);return !!p&&isTopLevel(p)&&p.id!==homePageId;});
}
