import type {EditorGuard} from './editorGuard';
import {useCallback,useEffect,useRef,useState} from 'react';
import type {DragEvent} from 'react';
import {CreatePageDraft} from './CreatePageDraft';
import type {PageCreationState} from './CreatePageDraft';
import {useBulkDelete} from './BulkDelete';
import {BlockDialog} from './BlockDialog';
import {addContentNode,deleteContentNode,deletePage,editContentNode,getContentNodes,getPage,getPageDeleteImpact,placePage,reorderContentNodes,reorderPages,saveComposition,setStructureMembership} from './api';
import type {Bootstrap,ClassificationCatalog,ContentNodeRow,Go,PageRow} from './types';
import {pagePath} from './navigation';
import {childHost,childParentOptions,childrenAllSelected,deletionOrder,draggable,dropPlan,isGroup,isTopLevel,pageLocation,pageTree,parentWarning,topAncestorId,unfoldedRows} from './pageHierarchy';
import type {PageTreeRow} from './pageHierarchy';
import {date,Empty,Heading,messageOf,Status,useRemote} from './ui';
import {StructurePublicationBar} from './StructurePublication';
import type {StructureStatus} from './types';

type Props={registerGuard?:(path:string,guard:EditorGuard|null)=>void;active:boolean;data:Bootstrap;go:Go;onOverview:(id:number)=>void;refresh:()=>void};
const homeOf=(data:Bootstrap)=>data.homePageId&&data.homePageId>0?data.homePageId:null;
// Folded top-level items are a per-viewer convenience; the list still works when storage is unavailable.
const FOLD_KEY='aica.siteStructure.folded';
function readFolded():Set<number>{
  try{const saved:unknown=JSON.parse(localStorage.getItem(FOLD_KEY)??'[]');return new Set(Array.isArray(saved)?saved.filter((id):id is number=>Number.isSafeInteger(id)):[]);}
  catch{return new Set();}
}
function writeFolded(folded:Set<number>){try{localStorage.setItem(FOLD_KEY,JSON.stringify([...folded]));}catch{/* not remembered */}}

export function PagesPanel({active,data,go,onOverview,refresh,registerGuard}:Props) {
  // "+ 하위 페이지" opens creation under that top-level item; top-level items themselves are not created here.
  const [creating,setCreating]=useState<PageRow|null>(null),[placing,setPlacing]=useState<PageRow|null>(null);
  // Site composition: each area's content-work link, menu visibility and label (V14), sub-navigation (V16).
  const [composing,setComposing]=useState<PageRow|null>(null);
  const catalog=useRemote<ClassificationCatalog>('/classifications',active);
  const typeName=(code:string)=>catalog.data?.types.find(t=>t.code===code)?.name??code;
  // 콘텐츠 작업 항목 (V16) are listed under their page. Saved rows are read again whenever the site changes;
  // if that fails the entries every role receives with the sidebar areas are shown instead.
  const typedIds=data.pages.filter(p=>p.contentTypeCode&&!isGroup(p)).map(p=>p.id).join(',');
  const [nodeMap,setNodeMap]=useState<Map<number,ContentNodeRow[]>>(new Map());
  useEffect(()=>{
    if(!active)return;
    const fromAreas=(id:number):ContentNodeRow[]=>(data.contentAreas??[]).find(a=>a.pageId===id)?.nodes.map((n,i)=>({...n,pageId:id,sortOrder:i}))??[];
    const ids=typedIds?typedIds.split(',').map(Number):[];let cancelled=false;
    void Promise.all(ids.map(id=>getContentNodes(id).then(list=>[id,list] as const,()=>[id,fromAreas(id)] as const))).then(entries=>{if(!cancelled)setNodeMap(new Map(entries));});
    return()=>{cancelled=true;};
  },[active,typedIds,data.contentAreas]);
  const nodesOf=(pageId:number)=>nodeMap.get(pageId)??[];
  const topicName=(id:number|null)=>id===null?'주제 없음':catalog.data?.topics.find(t=>t.id===id)?.name??`주제 #${id}`;
  const [nodeEdit,setNodeEdit]=useState<{pageId:number;nodeId:number|null}|null>(null);
  const [folded,setFolded]=useState<Set<number>>(readFolded);
  const toggleFold=(id:number)=>setFolded(current=>{const next=new Set(current);if(!next.delete(id))next.add(id);writeFolded(next);return next;});
  // Adding something under a folded item opens it, so the new row is not hidden on return.
  const unfold=(page:PageRow)=>{const top=topAncestorId(data.pages,page.id);if(folded.has(top))toggleFold(top);};
  const [ordering,setOrdering]=useState(false),[orderError,setOrderError]=useState('');
  const creationState=useRef<PageCreationState>({busy:false,dirty:false}),deleting=useRef(false),moving=useRef(false);
  const onCreationState=useCallback((state:PageCreationState)=>{creationState.current=state;},[]);
  useEffect(()=>{registerGuard?.('/pages',()=>deleting.current||moving.current||creationState.current.busy?'busy':!creationState.current.dirty);return()=>registerGuard?.('/pages',null);},[registerGuard]);
  useEffect(()=>{if(!active){setCreating(null);setPlacing(null);setComposing(null);setNodeEdit(null);}},[active]);
  const structure=!!data.permissions.structure,canDelete=!!data.permissions.permanentDelete,home=homeOf(data);
  // 구성 게시 (V14 step 2): the status reloads whenever the composition (bootstrap pages) changes.
  const [structureVersion,setStructureVersion]=useState(0);
  useEffect(()=>setStructureVersion(v=>v+1),[data.pages]);
  const structureStatus=useRemote<StructureStatus>('/site-structure',active&&structure,structureVersion),published=structureStatus.data?.latest!=null;
  const [q,setQ]=useState(''),[status,setStatus]=useState('');
  const filtered=!!q||!!status;
  // Without a filter the list follows the site structure; a filter shows matches flat with their parent named.
  const rows:PageTreeRow[]=filtered
    ?pageTree(data.pages).filter(r=>r.page.title.toLowerCase().includes(q.toLowerCase())&&(!status||r.page.status===status))
    :pageTree(data.pages);
  const items=rows.map(r=>r.page);
  // Folding applies to the structure view only; a filtered list always shows every match.
  const shown=filtered?rows:unfoldedRows(rows,data.pages,folded);
  // Top-level items are fixed site sections: they are never offered for deletion here (the server keeps its rules).
  const deletable=items.filter(page=>!isTopLevel(page));
  const deletion=useBulkDelete({items:deletable,active,scope:q+':'+status,allowed:!!data.permissions.permanentDelete,label:page=>page.title,permanent:true,
    // Selected child pages are deleted before their selected parent; a parent still refuses children that are not selected.
    order:chosen=>deletionOrder(chosen,data.pages),
    // The server's delete-impact (same as the legacy confirm screen) decides what blocks deletion and what goes with it.
    prepare:async(page,chosen)=>{const impact=await getPageDeleteImpact(page.id);
      const together=childrenAllSelected(data.pages,page.id,chosen.map(p=>p.id));
      const uses=impact.uses.filter(use=>!(together&&use.label.startsWith('하위 페이지 · ')));
      if(uses.length)throw new Error('사용 중: '+uses.map(use=>use.label).join(', ')+'. 연결을 해제한 뒤 삭제하세요.');
      return {id:page.id,label:page.title,revision:impact.revision,details:[impact.consequence,...(together?['선택한 하위 페이지를 먼저 삭제한 뒤 삭제합니다.']:[]),...(impact.history?['버전 이력 '+impact.history.versionCount+'개도 함께 삭제됩니다.']:[])]};},
    remove:target=>deletePage(target.id,target.revision!),onDone:()=>refresh(),
    description:'선택한 페이지와 설정·발행본·버전 이력을 영구 삭제합니다. 복구할 수 없습니다. 메뉴나 첫 화면에서 쓰는 페이지, 선택하지 않은 하위 페이지가 있는 페이지, 홈페이지에 반영된 구조에 들어 있는 페이지(메뉴 숨김 포함)는 삭제되지 않습니다. 반영된 페이지는 구조에서 뺀 뒤 홈페이지에 다시 반영하고 삭제하세요. 미디어 파일은 유지됩니다.'});
  deleting.current=deletion.busy;
  // Drag and drop: top-level items reorder among themselves (home stays first); second-level pages reorder in their
  // parent or move under another top-level item. A move is the existing placement (end of the new parent) followed by
  // the existing sibling order; if the second call fails the page simply stays at the end of its new parent.
  const [dragging,setDragging]=useState<number|null>(null),[dropAt,setDropAt]=useState<{id:number;after:boolean}|null>(null);
  const canDrag=structure&&!filtered&&!ordering;
  async function drop(dragId:number,overId:number,after:boolean){
    setDragging(null);setDropAt(null);
    const plan=dropPlan(data.pages,home,dragId,{overId,after});
    if(plan===null||moving.current)return;
    if(typeof plan==='string'){setOrderError(plan);return;}
    moving.current=true;setOrdering(true);setOrderError('');
    try{
      if(plan.moved)await placePage(plan.pageId,plan.parentId,data.pages.find(p=>p.id===plan.pageId)?.parentId??null);
      await reorderPages(plan.parentId,plan.ids);refresh();
    }catch(e){setOrderError(messageOf(e));refresh();}
    finally{moving.current=false;setOrdering(false);}
  }
  // Order and removal of 콘텐츠 작업 항목 run straight from their rows; name and topic open the item editor.
  async function nodeAction(pageId:number,action:()=>Promise<ContentNodeRow[]>){
    if(moving.current)return;
    moving.current=true;setOrdering(true);setOrderError('');
    try{const list=await action();setNodeMap(current=>new Map(current).set(pageId,list));refresh();}
    catch(e){setOrderError(messageOf(e));refresh();}
    finally{moving.current=false;setOrdering(false);}
  }
  const moveNode=(pageId:number,nodeId:number,delta:-1|1)=>{
    const ids=nodesOf(pageId).map(n=>n.id),index=ids.indexOf(nodeId),target=index+delta;
    if(index<0||target<0||target>=ids.length)return;
    [ids[index],ids[target]]=[ids[target],ids[index]];void nodeAction(pageId,()=>reorderContentNodes(pageId,ids));
  };
  const removeNode=(node:ContentNodeRow)=>{
    if(!confirm(`‘${node.name}’ 항목을 콘텐츠 작업 메뉴에서 없앱니다. 글과 주제는 그대로 남습니다.`))return;
    void nodeAction(node.pageId,()=>deleteContentNode(node.id));
  };
  const half=(event:DragEvent<HTMLTableRowElement>)=>{const box=event.currentTarget.getBoundingClientRect();return event.clientY>box.top+box.height/2;};
  const row=({page,depth,parent,children}:PageTreeRow)=>{
    const links=published?[]:data.menus.filter(m=>m.kind==='PAGE'&&m.targetId===page.id),warning=parentWarning(data.pages,page);
    const top=isTopLevel(page),fixed=page.id===home,movable=canDrag&&draggable(data.pages,page,home),nodeTotal=nodesOf(page.id).length;
    const foldable=top&&!filtered&&(children>0||nodeTotal>0),isFolded=foldable&&folded.has(page.id);
    const classes=[depth>1&&!filtered?'page-child-row':'',top&&!filtered?'page-top-row':'',dragging===page.id?'page-dragging':'',dropAt?.id===page.id&&dragging!==page.id?(dropAt.after?'page-drop-after':'page-drop-before'):''].filter(Boolean).join(' ');
    return <tr key={page.id} className={classes||undefined} data-page-depth={depth} draggable={movable}
      onDragStart={event=>{if(!movable)return;event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',String(page.id));setDragging(page.id);setOrderError('');}}
      onDragEnd={()=>{setDragging(null);setDropAt(null);}}
      onDragOver={event=>{if(dragging===null||!canDrag)return;event.preventDefault();event.dataTransfer.dropEffect='move';const after=half(event);if(dropAt?.id!==page.id||dropAt.after!==after)setDropAt({id:page.id,after});}}
      onDrop={event=>{if(dragging===null)return;event.preventDefault();void drop(dragging,page.id,half(event));}}>
      {canDelete&&<td className="bulk-cell">{top?null:deletion.checkbox(page)}</td>}
      <td><div className={depth>1&&!filtered?'page-title-cell page-title-child':'page-title-cell'}>{depth>1&&!filtered&&<span className="page-child-mark" aria-hidden="true">↳</span>}
        {top&&!filtered&&(foldable?<button type="button" className="page-fold" aria-expanded={!isFolded} aria-label={`${page.title} ${isFolded?'펼치기':'접기'}`} title={isFolded?'펼치기':'접기'} onClick={()=>toggleFold(page.id)}>{isFolded?'▸':'▾'}</button>:<span className="page-fold-space" aria-hidden="true"/>)}<div>
        <button className="text-link" data-page-id={page.id} onClick={()=>isGroup(page)?structure&&setComposing(page):go(pagePath(page.id))}>{page.title}</button>
        <small className="row-meta">{movable&&<span className="page-drag-handle" aria-hidden="true" title="끌어서 순서·위치 바꾸기">⠿</span>}{isGroup(page)?'묶음 · 화면 없음':'/'+page.slug}{fixed?' · 홈(첫 화면) · 고정':top&&!filtered?' · 최상위':''}{!page.inStructure&&' · 구조에서 뺌'}{filtered&&parent&&` · 상위: ${parent.title}`}{depth>1&&!filtered&&' · 하위 페이지'}{children>0&&` · 하위 페이지 ${children}개`}{nodeTotal>0&&` · 콘텐츠 작업 항목 ${nodeTotal}개`}</small>
        {(page.contentTypeCode||page.menuVisible||page.contentWorkVisible)&&<small className="page-composition-meta">{page.contentWorkVisible&&<span>콘텐츠 작업에 보임: {page.contentTypeCode?`${typeName(page.contentTypeCode)} 글`:'내용 편집'}</span>}{!page.contentWorkVisible&&page.contentTypeCode&&<span>글 종류: {typeName(page.contentTypeCode)} (콘텐츠 작업에 숨김)</span>}{page.menuVisible&&<span>메뉴에 보임{page.menuLabel?` · ${page.menuLabel}`:''}</span>}</small>}
        {warning&&<small className="page-hierarchy-warning" title="게시된 하위 페이지는 상위 페이지 상태와 관계없이 자기 주소로 공개됩니다.">{warning}</small>}
      </div></div></td>
      <td>{isGroup(page)?<span className="status-tag state-group">묶음</span>:<Status value={page.status} pending={page.pending} pageWording/>}</td>
      <td>{!page.inStructure?<span className="muted">구조에서 뺌</span>:published?(page.menuVisible?<span className="page-menu-label">{page.menuLabel||page.title}</span>:<span className="muted">메뉴 숨김</span>)
        :links.length?links.map(m=><span className="page-menu-label" key={m.id}>{m.label}<small> · 기존 메뉴{m.visible?'':' · 숨김'}</small></span>):<span className="muted">—</span>}</td>
      <td>{date(page.updatedAt)}</td>
      <td><div className="page-row-actions">
        {structure&&childHost(page,home)&&<button type="button" className="secondary" disabled={ordering} onClick={()=>{unfold(page);setCreating(page);}}>＋ 하위 페이지</button>}
        {structure&&!!page.contentTypeCode&&!isGroup(page)&&<button type="button" className="secondary" disabled={ordering} onClick={()=>{unfold(page);setNodeEdit({pageId:page.id,nodeId:null});}}>＋ 콘텐츠 작업 항목</button>}
        {!isGroup(page)&&<><button onClick={()=>go(pagePath(page.id))}>내용 편집</button><button onClick={()=>onOverview(page.id)}>블록 보기</button></>}
        {structure&&!top&&<button type="button" disabled={ordering} onClick={()=>setPlacing(page)}>옮기기</button>}
        {structure&&<button type="button" disabled={ordering} onClick={()=>setComposing(page)}>설정</button>}
        {!top&&deletion.rowButton(page)}
      </div></td>
    </tr>;
  };
  // 콘텐츠 작업 항목: admin sub-navigation under the page, never a homepage page; these rows are not dragged.
  const nodeRow=(page:PageRow,depth:number)=>(node:ContentNodeRow,index:number,list:ContentNodeRow[])=>
    <tr key={'node-'+node.id} className="page-node-row" data-node-id={node.id}>
      {canDelete&&<td className="bulk-cell"/>}
      <td><div className={'page-title-cell page-title-node'+(depth>1?' page-title-node-deep':'')}><span className="page-node-mark" aria-hidden="true">└</span><div>
        <span className="page-node-name">{node.name}</span>
        <small className="row-meta">콘텐츠 작업 항목 · 주제: {topicName(node.topicId)}{!page.contentWorkVisible&&' · 콘텐츠 작업에 숨김'}</small>
      </div></div></td>
      <td><span className="status-tag state-work">관리자 화면</span></td>
      <td><span className="muted">—</span></td>
      <td><span className="muted">—</span></td>
      <td><div className="page-row-actions">{structure&&<>
        <span className="order-buttons"><button type="button" aria-label={`${node.name} 위로`} disabled={ordering||index===0} onClick={()=>moveNode(page.id,node.id,-1)}>↑</button><button type="button" aria-label={`${node.name} 아래로`} disabled={ordering||index===list.length-1} onClick={()=>moveNode(page.id,node.id,1)}>↓</button></span>
        <button type="button" disabled={ordering} onClick={()=>setNodeEdit({pageId:page.id,nodeId:node.id})}>수정</button>
        <button type="button" className="danger-link" disabled={ordering} aria-label={`${node.name} 없애기`} onClick={()=>removeNode(node)}>없애기</button>
      </>}</div></td>
    </tr>;
  const editingPage=nodeEdit?data.pages.find(p=>p.id===nodeEdit.pageId)??null:null;
  return <section className="pages-workspace"><Heading title="사이트 구조" note="홈페이지에 어떤 페이지를 어떤 순서로 둘지 정하고, 각 페이지의 내용을 편집하러 들어가는 곳입니다. 최상위 항목은 고정되어 있고 그 아래에 하위 페이지를 만듭니다. 여기서 바꾼 위치·순서·메뉴는 [홈페이지에 반영]을 눌러야 홈페이지에 나타납니다."/>
    {structure&&<StructurePublicationBar active={active} status={structureStatus} onChanged={refresh} onBusy={busy=>{moving.current=busy;}}/>}
    {composing&&active&&structure&&<CompositionDialog active={active} page={data.pages.find(p=>p.id===composing.id)??composing} pages={data.pages} catalog={catalog.data} onClose={()=>setComposing(null)} onBusy={busy=>{moving.current=busy;}} onDone={()=>{setComposing(null);refresh();}} onStale={refresh} onNodesChanged={refresh}/>}
    {creating&&active&&structure&&<CreatePageDraft active={active} parent={creating} templateUse={!!data.permissions.templateUse} onStateChange={onCreationState} onClose={()=>setCreating(null)} onCheckList={()=>{setCreating(null);setQ('');setStatus('');refresh();}} onCreated={page=>{setCreating(null);refresh();go(pagePath(page.id));}}/>}
    {nodeEdit&&editingPage&&active&&structure&&<BlockDialog active={active} title={`‘${editingPage.title}’ 콘텐츠 작업 항목`} onClose={()=>{if(!moving.current)setNodeEdit(null);}}>
      <div className="page-create-form"><ContentNodeEditor page={editingPage} catalog={catalog.data} nodes={nodeMap.get(editingPage.id)??null} pendingType={editingPage.contentTypeCode??''} busy={false} startEditing={nodeEdit.nodeId} focusName onBusy={busy=>{moving.current=busy;}} onChanged={list=>{setNodeMap(current=>new Map(current).set(editingPage.id,list));refresh();}}/>
        <div className="dialog-actions"><button type="button" onClick={()=>{if(!moving.current)setNodeEdit(null);}}>닫기</button></div></div>
    </BlockDialog>}
    {placing&&active&&structure&&<PagePlacementDialog active={active} page={placing} pages={data.pages} homePageId={home} onClose={()=>setPlacing(null)} onBusy={busy=>{moving.current=busy;}} onDone={()=>{setPlacing(null);refresh();}} onStale={refresh}/>}
    <section className="card"><div className="search-bar"><input aria-label="페이지 검색" value={q} onChange={e=>setQ(e.target.value)} placeholder="페이지 이름 검색"/><select aria-label="페이지 상태" value={status} onChange={e=>setStatus(e.target.value)}><option value="">전체 상태</option><option value="DRAFT">임시보관</option><option value="PUBLISHED">게시됨</option><option value="PRIVATE">비공개</option></select><span>{items.length}개</span>{deletion.action}</div>
      {structure&&!filtered&&<p className="muted page-drag-note">⠿ 행을 끌어서 옮깁니다. 최상위 항목은 최상위끼리 순서를, 하위 페이지는 같은 항목 안의 순서를 바꾸거나 다른 최상위 항목 위에 놓아 그 아래로 옮깁니다. 키보드로는 하위 페이지의 [옮기기]를 쓰세요.</p>}
      {filtered&&structure&&<p className="muted page-drag-note">검색·상태 필터 중에는 끌어서 옮길 수 없습니다.</p>}
      {orderError&&<p className="error-box" role="alert">{orderError}</p>}
      {deletion.feedback}{deletion.dialog}
      <div className="table-scroll"><table className="data-table pages-table"><thead><tr>{canDelete&&<th className="bulk-cell">{deletion.selectAll}</th>}<th>페이지</th><th>페이지 상태</th><th>홈페이지 메뉴</th><th>최근 수정</th><th>작업</th></tr></thead><tbody>{shown.flatMap(r=>[row(r),...(filtered||isTopLevel(r.page)&&folded.has(r.page.id)?[]:nodesOf(r.page.id).map(nodeRow(r.page,r.depth)))])}</tbody></table>
      {!items.length&&<Empty>{filtered?'조건에 맞는 페이지가 없습니다. 검색어나 상태를 바꿔보세요.':'등록된 페이지가 없습니다.'}</Empty>}</div></section>
    <p className="muted page-list-note">페이지 내용은 각 페이지의 [게시]로, 위치·순서·메뉴는 [홈페이지에 반영]으로 홈페이지에 나타납니다. 위치를 옮겨도 페이지 주소는 바뀌지 않습니다. 최상위 항목의 추가·삭제는 이 화면에서 하지 않습니다.{!published&&' 처음 반영하기 전까지 홈페이지 메뉴는 메뉴 관리의 기존 메뉴를 씁니다.'}</p>
  </section>;
}

function PagePlacementDialog({active,page,pages,homePageId,onClose,onBusy,onDone,onStale}:{active:boolean;page:PageRow;pages:PageRow[];homePageId:number|null;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void;onStale:()=>void}) {
  const current=page.parentId??null;
  const [parent,setParent]=useState<number|null>(current),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const pending=useRef(false);
  // Keyboard alternative to dragging: a second-level page moves under another top-level item (end of its children).
  const options=childParentOptions(pages,page.id,homePageId);
  async function save(){
    if(pending.current||parent===current)return;
    pending.current=true;setBusy(true);onBusy(true);setError('');
    try{await placePage(page.id,parent,current);onDone();}
    catch(e){setError(messageOf(e));onStale();}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  return <BlockDialog active={active} title={`‘${page.title}’ 옮기기`} onClose={()=>{if(!pending.current)onClose();}}>
    <form className="page-create-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <p className="page-location"><span className="muted">지금 위치</span> <strong>{pageLocation(pages,page.id)}</strong></p>
      <label htmlFor="page-parent-select"><span>옮길 최상위 항목</span>
        <select id="page-parent-select" value={parent??''} disabled={busy} onChange={e=>setParent(e.target.value?Number(e.target.value):current)}>
          {options.map(option=><option key={option.id} value={option.id} disabled={!!option.reason&&option.id!==current}>{option.title}{option.reason&&option.id!==current?` — ${option.reason}`:''}</option>)}
        </select>
      </label>
      <p className="page-create-help">선택한 최상위 항목 아래 맨 끝으로 옮깁니다. 주소와 게시 상태는 바뀌지 않고, 순서는 목록에서 끌어서 바꿉니다. 홈페이지에는 [홈페이지에 반영] 뒤에 나타납니다.</p>
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button type="submit" className="primary" disabled={busy||parent===current}>{busy?'옮기는 중…':'옮기기'}</button></div>
    </form>
  </BlockDialog>;
}

/**
 * Site composition of one area. The content-work link makes this page its type's representative work
 * area (one per type, an operating rule) and never limits where that type's posts appear; the menu
 * settings take effect on the homepage only after a structure publication.
 */
function CompositionDialog({active,page,pages,catalog,onClose,onBusy,onDone,onStale,onNodesChanged}:{active:boolean;page:PageRow;pages:PageRow[];catalog:ClassificationCatalog|null;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void;onStale:()=>void;onNodesChanged:()=>void}) {
  const group=isGroup(page);
  const [type,setType]=useState(page.contentTypeCode??''),[visible,setVisible]=useState(page.menuVisible),[work,setWork]=useState(page.contentWorkVisible),[label,setLabel]=useState(page.menuLabel??''),[name,setName]=useState(page.title);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[blockTypes,setBlockTypes]=useState<string[]|null>(null);
  const pending=useRef(false);
  // 콘텐츠 작업 하위 항목 (V16): loaded for a saved link; every change is saved at once and the sidebar follows.
  const [nodes,setNodes]=useState<ContentNodeRow[]|null>(null);
  useEffect(()=>{if(group||!page.contentTypeCode){setNodes([]);return;}let cancelled=false;
    void getContentNodes(page.id).then(list=>{if(!cancelled)setNodes(list);}).catch(e=>{if(!cancelled){setNodes([]);setError(messageOf(e));}});
    return()=>{cancelled=true;};},[group,page.id,page.contentTypeCode]);
  useEffect(()=>{if(group)return;let cancelled=false;
    void getPage(page.id).then(doc=>{if(!cancelled)setBlockTypes(doc.sections.filter(s=>s.type==='POSTS').map(s=>{const q=(s as {query?:{typeCode?:string|null}}).query;
      // All types (query without a type, or the old category mode without a category) lists every type.
      return s.sourceMode==='query'?(q?.typeCode??'*'):(s.sourceMode==null||s.sourceMode==='category')&&s.categoryId==null?'*':'';}).filter(Boolean));}).catch(()=>{if(!cancelled)setBlockTypes([]);});
    return()=>{cancelled=true;};},[group,page.id]);
  const representedBy=(code:string)=>pages.find(p=>p.id!==page.id&&p.contentTypeCode===code);
  const types=catalog?.types.filter(t=>t.active||t.code===page.contentTypeCode)??[];
  const unchanged=type===(page.contentTypeCode??'')&&visible===page.menuVisible&&work===page.contentWorkVisible&&label.trim()===(page.menuLabel??'')&&(!group||name.trim()===page.title);
  async function save(){
    if(pending.current||unchanged||group&&!name.trim())return;
    pending.current=true;setBusy(true);onBusy(true);setError('');
    // A newly saved link keeps the dialog open so the operator can add the sub-navigation right away.
    const linkedNow=!group&&!!type&&type!==(page.contentTypeCode??'');
    try{await saveComposition(page.id,{contentTypeCode:type||null,menuVisible:visible,menuLabel:label.trim()||null,name:group?name.trim():null,contentWorkVisible:work});
      if(linkedNow){setNotice('저장했습니다. 이제 아래에서 콘텐츠 작업 항목을 추가할 수 있습니다.');onStale();}else onDone();}
    catch(e){setError(messageOf(e));onStale();}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  const missingBlock=!group&&!!type&&blockTypes!==null&&!blockTypes.includes(type)&&!blockTypes.includes('*');
  // 구성에서 제거 / 다시 포함 (V15): separate from menu visibility; the homepage follows at the next structure publication.
  const childrenInStructure=pages.filter(p=>p.parentId===page.id&&p.inStructure).length;
  const parentRemoved=page.parentId!=null&&pages.some(p=>p.id===page.parentId&&!p.inStructure);
  const membershipBlock=page.inStructure?(childrenInStructure?`구조에 남은 하위 페이지 ${childrenInStructure}개를 먼저 옮기거나 빼세요.`:page.contentTypeCode?'콘텐츠 작업의 글 종류 선택을 먼저 해제하고 저장하세요. 글은 그대로 남습니다.':null)
    :parentRemoved?'상위 항목이 구조에서 빠져 있습니다. 상위 항목을 먼저 다시 넣으세요.':null;
  async function membership(){
    if(pending.current||membershipBlock)return;
    pending.current=true;setBusy(true);onBusy(true);setError('');
    try{await setStructureMembership(page.id,!page.inStructure);onDone();}
    catch(e){setError(messageOf(e));onStale();}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  const typeLabel=catalog?.types.find(t=>t.code===type)?.name??'';
  return <BlockDialog active={active} title={`‘${page.title}’ 설정`} onClose={()=>{if(!pending.current)onClose();}}>
    <form className="page-create-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <p className="page-location"><span className="muted">위치</span> <strong>{pageLocation(pages,page.id)}</strong>{group&&<small className="muted"> · 묶음(화면 없음)</small>}</p>
      {group&&<label htmlFor="composition-name"><span>묶음 이름</span><input id="composition-name" required maxLength={200} value={name} disabled={busy} onChange={e=>setName(e.target.value)}/></label>}
      {!page.inStructure&&<p className="page-hierarchy-warning" role="note">사이트 구조에서 뺀 {group?'묶음':'페이지'}입니다. 홈페이지에 반영하면 홈페이지 구조와 메뉴에서 빠지고, 내용은 그대로 남습니다.</p>}
      <fieldset className="composition-section"><legend>홈페이지 메뉴</legend>
        <div className="checkbox-pair">
          <label className="checkbox-row"><input type="checkbox" checked={visible} disabled={busy} onChange={e=>setVisible(e.target.checked)}/> 홈페이지 메뉴에 보이기</label>
          {!group&&<label className="checkbox-row"><input type="checkbox" checked={work} disabled={busy||!page.inStructure} onChange={e=>setWork(e.target.checked)}/> 콘텐츠 작업에 보이기</label>}
        </div>
        <label htmlFor="composition-label"><span>메뉴에 표시할 이름 <small>선택</small></span><input id="composition-label" maxLength={80} value={label} placeholder={page.title} disabled={busy} onChange={e=>setLabel(e.target.value)}/></label>
        <p className="page-create-help">[홈페이지에 반영] 뒤에 적용됩니다. 비워 두면 {group?'묶음 이름':'게시된 페이지 제목'}을 씁니다. 메뉴에서 숨겨도 게시된 페이지는 주소로 열립니다.</p>
      </fieldset>
      {!group&&<fieldset className="composition-section"><legend>콘텐츠 작업 <small>관리자 화면</small></legend>
        <p className="page-create-help">{work?'왼쪽 콘텐츠 작업 메뉴에 이 페이지가 보입니다.':'‘콘텐츠 작업에 보이기’를 켜면 왼쪽 콘텐츠 작업 메뉴에 이 페이지가 보입니다.'} 글 종류를 고르지 않으면 항목을 눌렀을 때 페이지 내용 편집으로 갑니다.</p>
        <label htmlFor="composition-type"><span>이 페이지에서 관리할 글 종류 <small>선택</small></span>
          <select id="composition-type" value={type} disabled={busy||!catalog||!page.inStructure&&!page.contentTypeCode} onChange={e=>{setType(e.target.value);if(e.target.value)setWork(true);}}>
            <option value="">선택 안 함 — 내용 편집만</option>
            {types.map(t=>{const other=representedBy(t.code);return <option key={t.code} value={t.code} disabled={!!other}>{t.name}{other?` — ‘${other.title}’ 페이지가 사용 중`:''}</option>;})}
          </select></label>
        <p className="page-create-help">고르면 왼쪽 <strong>콘텐츠 작업</strong> 메뉴에 이 페이지가 생기고, 그 종류의 글을 여기서 쓰고 관리합니다. 글 종류 하나에 페이지 하나입니다. 같은 종류의 글은 다른 페이지의 글 목록 블록에도 넣을 수 있습니다.</p>
        {work&&<p className="composition-preview">→ 콘텐츠 작업 메뉴에 <strong>{page.title}</strong>{type&&typeLabel?` (${typeLabel} 글)`:' (내용 편집)'}이 보입니다.</p>}
        {!work&&!!type&&<p className="page-hierarchy-warning" role="note">‘콘텐츠 작업에 보이기’가 꺼져 있어 이 페이지는 콘텐츠 작업 메뉴에 나오지 않습니다. 글은 전체 콘텐츠에서 쓸 수 있습니다.</p>}
        {missingBlock&&<p className="page-hierarchy-warning" role="note">이 페이지에는 아직 이 글 종류의 글 목록 블록이 없습니다. 홈페이지에 글을 보여 주려면 내용 편집에서 글 목록 블록을 추가하세요(이 설정은 저장할 수 있습니다).</p>}
        {notice&&<p className="success-box" role="status">{notice}</p>}
        {!!nodes?.length&&type!==(page.contentTypeCode??'')&&<p className="page-hierarchy-warning" role="note">글 종류를 바꾸거나 선택을 해제하면 아래 콘텐츠 작업 항목 {nodes.length}개도 함께 없어집니다. 글과 주제는 남습니다.</p>}
        <ContentNodeEditor page={page} catalog={catalog} nodes={nodes} pendingType={type} busy={busy} onBusy={onBusy} onChanged={list=>{setNodes(list);onNodesChanged();}}/>
      </fieldset>}
      {!isTopLevel(page)&&<fieldset className="structure-membership"><legend>사이트 구조에서 빼기</legend>
        <p className="page-create-help">메뉴 숨김은 구조에 남긴 채 메뉴에서만 빼는 것이고, 구조에서 빼기는 홈페이지 구조 자체에서 빼는 것입니다([홈페이지에 반영] 뒤 적용). 홈페이지에 반영된 페이지는 구조에서 뺀 뒤 다시 반영해야 영구 삭제할 수 있습니다.</p>
        {membershipBlock&&<p className="page-hierarchy-warning" role="note">{membershipBlock}</p>}
        <button type="button" disabled={busy||!!membershipBlock} onClick={()=>void membership()}>{page.inStructure?'구조에서 빼기':'구조에 다시 넣기'}</button>
      </fieldset>}
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button type="submit" className="primary" disabled={busy||unchanged||group&&!name.trim()}>{busy?'저장 중…':'저장'}</button></div>
    </form>
  </BlockDialog>;
}

/**
 * 콘텐츠 작업 하위 항목 (V16). The operator builds the admin sub-navigation under a linked page by hand: each
 * entry is a name and one topic of the page's content type, in the order saved here. Nothing comes from the
 * topic dictionary, and removing an entry never touches posts or topics. This is admin navigation only and
 * has nothing to do with the homepage's child pages (those are placed with 위치).
 */
function ContentNodeEditor({page,catalog,nodes,pendingType,busy,startEditing=null,focusName=false,onBusy,onChanged}:{page:PageRow;catalog:ClassificationCatalog|null;nodes:ContentNodeRow[]|null;pendingType:string;busy:boolean;startEditing?:number|null;focusName?:boolean;onBusy:(busy:boolean)=>void;onChanged:(nodes:ContentNodeRow[])=>void}) {
  const linked=page.contentTypeCode;
  const [name,setName]=useState(''),[topic,setTopic]=useState(''),[editing,setEditing]=useState<number|null>(null),[working,setWorking]=useState(false),[error,setError]=useState('');
  const pending=useRef(false);
  const topics=linked&&catalog?catalog.topics.filter(t=>t.active&&catalog.allowedTopics.some(a=>a.typeCode===linked&&a.topicId===t.id)):[];
  const topicName=(id:number|null)=>id===null?'주제 없음':catalog?.topics.find(t=>t.id===id)?.name??`주제 #${id}`;
  const usedBy=(topicId:number)=>nodes?.find(n=>n.id!==editing&&n.topicId===topicId);
  const disabled=busy||working||nodes===null;
  async function run(action:()=>Promise<ContentNodeRow[]>){
    if(pending.current)return;pending.current=true;setWorking(true);onBusy(true);setError('');
    try{onChanged(await action());return true;}
    catch(e){setError(messageOf(e));return false;}
    finally{pending.current=false;setWorking(false);onBusy(false);}
  }
  const reset=()=>{setName('');setTopic('');setEditing(null);};
  const edit=(node:ContentNodeRow)=>{setEditing(node.id);setName(node.name);setTopic(node.topicId===null?'':String(node.topicId));setError('');};
  // Opened from a row's [수정]: start on that entry once the saved rows are there.
  const nameInput=useRef<HTMLInputElement>(null),started=useRef(false);
  useEffect(()=>{if(started.current||!nodes)return;started.current=true;
    const node=startEditing===null?undefined:nodes.find(n=>n.id===startEditing);if(node)edit(node);
    // After the dialog has opened (it moves focus when shown).
    if(focusName)setTimeout(()=>nameInput.current?.focus(),0);},[nodes,startEditing,focusName]);
  async function submit(){
    if(!name.trim()||!topic)return;
    const value={name:name.trim(),topicId:Number(topic)};
    if(await run(()=>editing===null?addContentNode(page.id,value):editContentNode(editing,value)))reset();
  }
  const move=(node:ContentNodeRow,delta:-1|1)=>{
    if(!nodes)return;const ids=nodes.map(n=>n.id),index=ids.indexOf(node.id),target=index+delta;
    if(index<0||target<0||target>=ids.length)return;
    [ids[index],ids[target]]=[ids[target],ids[index]];void run(()=>reorderContentNodes(page.id,ids));
  };
  const remove=(node:ContentNodeRow)=>{
    if(!confirm(`‘${node.name}’ 항목을 콘텐츠 작업 메뉴에서 없앱니다. 글과 주제는 그대로 남습니다.`))return;
    if(editing===node.id)reset();void run(()=>deleteContentNode(node.id));
  };
  return <fieldset className="content-nodes"><legend>콘텐츠 작업 항목</legend>
    <p className="page-create-help">왼쪽 콘텐츠 작업 메뉴에서 ‘{page.title}’ 아래에 보일 항목입니다. 항목마다 주제 하나를 골라 그 주제의 글만 모아 보고 씁니다. 홈페이지에는 나타나지 않습니다(홈페이지 화면은 하위 페이지로 만듭니다). 항목을 없애도 글과 주제는 남습니다.</p>
    {!linked?<p className="muted">{pendingType?'글 종류를 저장한 뒤 항목을 추가할 수 있습니다.':'글 종류를 고르고 저장하면 항목을 추가할 수 있습니다.'}</p>:<>
      {nodes===null?<p role="status">불러오는 중…</p>:nodes.length===0?<p className="muted">아직 항목이 없습니다. 콘텐츠 작업 메뉴에는 이 페이지만 보입니다.</p>
        :<ol className="content-node-list">{nodes.map((node,index)=><li key={node.id} className={editing===node.id?'editing':undefined}>
          <span className="content-node-name">{node.name}<small className="muted">주제: {topicName(node.topicId)}</small></span>
          <span className="content-node-actions"><button type="button" aria-label={`${node.name} 위로`} disabled={disabled||index===0} onClick={()=>move(node,-1)}>↑</button><button type="button" aria-label={`${node.name} 아래로`} disabled={disabled||index===nodes.length-1} onClick={()=>move(node,1)}>↓</button><button type="button" disabled={disabled} onClick={()=>edit(node)}>수정</button><button type="button" disabled={disabled} onClick={()=>remove(node)}>없애기</button></span>
        </li>)}</ol>}
      <div className="content-node-form" role="group" aria-label={editing===null?'콘텐츠 작업 항목 추가':'콘텐츠 작업 항목 수정'}>
        <label htmlFor="content-node-name"><span>항목 이름</span><input ref={nameInput} id="content-node-name" maxLength={80} value={name} disabled={disabled} placeholder="예: 프로젝트" onChange={e=>setName(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();void submit();}}}/></label>
        <label htmlFor="content-node-topic"><span>모아 볼 주제</span>
          <select id="content-node-topic" value={topic} disabled={disabled||!catalog} onChange={e=>setTopic(e.target.value)}>
            <option value="">{catalog?topics.length?'주제를 고르세요':'이 글 종류에 주제가 없습니다':'불러오는 중'}</option>
            {topics.map(t=>{const other=usedBy(t.id);return <option key={t.id} value={t.id} disabled={!!other}>{t.name}{other?` — ‘${other.name}’ 항목이 사용 중`:''}</option>;})}
          </select></label>
        <span className="content-node-form-actions">
          {editing!==null&&<button type="button" disabled={disabled} onClick={reset}>취소</button>}
          <button type="button" className="secondary" disabled={disabled||!name.trim()||!topic} onClick={()=>void submit()}>{working?'저장 중…':editing===null?'항목 추가':'항목 저장'}</button>
        </span>
      </div>
      {!topics.length&&catalog&&<p className="muted">주제가 없는 글 종류에는 항목을 만들 수 없습니다. 항목 없이도 이 페이지에서 글을 관리할 수 있습니다.</p>}
    </>}
    {error&&<p className="error-box" role="alert">{error}</p>}
  </fieldset>;
}
