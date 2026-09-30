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
/** "인사교 소개 › 후기" */
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
  return parent&&parent.status!=='PUBLISHED'?'상위 페이지 비공개':null;
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
