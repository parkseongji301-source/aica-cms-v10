import type {EditorGuard} from './editorGuard';
import {useCallback,useEffect,useRef,useState} from 'react';
import {CreatePageDraft} from './CreatePageDraft';
import type {PageCreationState} from './CreatePageDraft';
import {useBulkDelete} from './BulkDelete';
import {BlockDialog} from './BlockDialog';
import {createPageGroup,deletePage,getPage,getPageDeleteImpact,placePage,reorderPages,saveComposition} from './api';
import type {Bootstrap,ClassificationCatalog,Go,PageRow} from './types';
import {pagePath} from './navigation';
import {childrenAllSelected,deletionOrder,isGroup,movedSiblings,pageLocation,pageTree,parentOptions,parentWarning} from './pageHierarchy';
import type {PageTreeRow} from './pageHierarchy';
import {date,Empty,Heading,messageOf,Status,useRemote} from './ui';

type Props={registerGuard?:(path:string,guard:EditorGuard|null)=>void;active:boolean;data:Bootstrap;go:Go;onOverview:(id:number)=>void;refresh:()=>void};
const homeOf=(data:Bootstrap)=>data.homePageId&&data.homePageId>0?data.homePageId:null;

export function PagesPanel({active,data,go,onOverview,refresh,registerGuard}:Props) {
  const [creating,setCreating]=useState(false),[placing,setPlacing]=useState<PageRow|null>(null);
  // Site composition (V14 step 1): GROUP areas and each area's content-work link, menu visibility and label.
  const [grouping,setGrouping]=useState(false),[composing,setComposing]=useState<PageRow|null>(null);
  const catalog=useRemote<ClassificationCatalog>('/classifications',active);
  const typeName=(code:string)=>catalog.data?.types.find(t=>t.code===code)?.name??code;
  const [ordering,setOrdering]=useState(false),[orderError,setOrderError]=useState('');
  const creationState=useRef<PageCreationState>({busy:false,dirty:false}),deleting=useRef(false),moving=useRef(false);
  const onCreationState=useCallback((state:PageCreationState)=>{creationState.current=state;},[]);
  useEffect(()=>{registerGuard?.('/pages',()=>deleting.current||moving.current||creationState.current.busy?'busy':!creationState.current.dirty);return()=>registerGuard?.('/pages',null);},[registerGuard]);
  useEffect(()=>{if(!active){setCreating(false);setPlacing(null);setGrouping(false);setComposing(null);}},[active]);
  const structure=!!data.permissions.structure,canDelete=!!data.permissions.permanentDelete,home=homeOf(data);
  const [q,setQ]=useState(''),[status,setStatus]=useState('');
  const filtered=!!q||!!status;
  // Without a filter the list follows the site structure; a filter shows matches flat with their parent named.
  const rows:PageTreeRow[]=filtered
    ?pageTree(data.pages).filter(r=>r.page.title.toLowerCase().includes(q.toLowerCase())&&(!status||r.page.status===status))
    :pageTree(data.pages);
  const items=rows.map(r=>r.page);
  const deletion=useBulkDelete({items,active,scope:q+':'+status,allowed:!!data.permissions.permanentDelete,label:page=>page.title,permanent:true,
    // Selected child pages are deleted before their selected parent; a parent still refuses children that are not selected.
    order:chosen=>deletionOrder(chosen,data.pages),
    // The server's delete-impact (same as the legacy confirm screen) decides what blocks deletion and what goes with it.
    prepare:async(page,chosen)=>{const impact=await getPageDeleteImpact(page.id);
      const together=childrenAllSelected(data.pages,page.id,chosen.map(p=>p.id));
      const uses=impact.uses.filter(use=>!(together&&use.label.startsWith('하위 페이지 · ')));
      if(uses.length)throw new Error('사용 중: '+uses.map(use=>use.label).join(', ')+'. 연결을 해제한 뒤 삭제하세요.');
      return {id:page.id,label:page.title,revision:impact.revision,details:[impact.consequence,...(together?['선택한 하위 페이지를 먼저 삭제한 뒤 삭제합니다.']:[]),...(impact.history?['버전 이력 '+impact.history.versionCount+'개도 함께 삭제됩니다.']:[])]};},
    remove:target=>deletePage(target.id,target.revision!),onDone:()=>refresh(),
    description:'선택한 페이지와 구성·발행본·버전 이력을 영구삭제합니다. 복구할 수 없습니다. 메뉴나 홈페이지 첫 화면에서 사용 중인 페이지와 선택하지 않은 하위 페이지가 있는 페이지는 삭제되지 않습니다. 미디어 파일은 유지됩니다.'});
  deleting.current=deletion.busy;
  async function move(page:PageRow,delta:-1|1){
    const ids=movedSiblings(data.pages,page.id,delta);if(!ids||moving.current)return;
    moving.current=true;setOrdering(true);setOrderError('');
    try{await reorderPages(page.parentId,ids);refresh();}
    catch(e){setOrderError(messageOf(e));refresh();}
    finally{moving.current=false;setOrdering(false);}
  }
  const row=({page,depth,parent,children}:PageTreeRow)=>{
    const links=data.menus.filter(m=>m.kind==='PAGE'&&m.targetId===page.id),warning=parentWarning(data.pages,page);
    const siblings=data.pages.filter(p=>(p.parentId??null)===(page.parentId??null)),index=siblings.findIndex(p=>p.id===page.id);
    return <tr key={page.id} className={depth>1&&!filtered?'page-child-row':undefined} data-page-depth={depth}>
      {canDelete&&<td className="bulk-cell">{deletion.checkbox(page)}</td>}
      <td><div className={depth>1&&!filtered?'page-title-cell page-title-child':'page-title-cell'}>{depth>1&&!filtered&&<span className="page-child-mark" aria-hidden="true">↳</span>}<div>
        <button className="text-link" data-page-id={page.id} onClick={()=>isGroup(page)?structure&&setComposing(page):go(pagePath(page.id))}>{page.title}</button>
        <small className="row-meta">{isGroup(page)?'묶음 · 화면 없음':'/'+page.slug}{page.id===home&&' · 홈(첫 화면)'}{filtered&&parent&&` · 상위: ${parent.title}`}{depth>1&&!filtered&&' · 하위 페이지'}{children>0&&` · 하위 ${children}개`}</small>
        {(page.contentTypeCode||page.menuVisible)&&<small className="page-composition-meta">{page.contentTypeCode&&<span>콘텐츠 작업: {typeName(page.contentTypeCode)}</span>}{page.menuVisible&&<span>메뉴 노출{page.menuLabel?` · ${page.menuLabel}`:''}</span>}</small>}
        {warning&&<small className="page-hierarchy-warning" title="게시된 하위 페이지는 상위 페이지 상태와 관계없이 자기 주소로 공개됩니다.">{warning}</small>}
      </div></div></td>
      <td>{isGroup(page)?<span className="status-tag state-group">묶음</span>:<Status value={page.status} pending={page.pending} pageWording/>}</td>
      <td>{links.length?links.map(m=><span className="page-menu-label" key={m.id}>{m.label}{!m.visible&&<small> · 메뉴 숨김</small>}</span>):<span className="muted">메뉴 미연결</span>}</td>
      <td>{date(page.updatedAt)}</td>
      <td><div className="page-row-actions">
        {structure&&!filtered&&<div className="order-buttons"><button type="button" aria-label={`${page.title} 위로`} disabled={ordering||index<=0} onClick={()=>void move(page,-1)}>↑</button><button type="button" aria-label={`${page.title} 아래로`} disabled={ordering||index<0||index>=siblings.length-1} onClick={()=>void move(page,1)}>↓</button></div>}
        {!isGroup(page)&&<><button onClick={()=>go(pagePath(page.id))}>편집</button><button onClick={()=>onOverview(page.id)}>구조 보기</button></>}
        {structure&&<button type="button" disabled={ordering} onClick={()=>setPlacing(page)}>위치</button>}
        {structure&&<button type="button" disabled={ordering} onClick={()=>setComposing(page)}>구성</button>}
        {deletion.rowButton(page)}
      </div></td>
    </tr>;
  };
  return <section className="pages-workspace"><Heading title="전체 페이지 현황" note="페이지 상태와 연결된 메뉴를 확인하고 내용을 편집합니다. 하위 페이지는 상위 페이지 아래에 들여써서 보여 줍니다." actions={<>{structure&&<button type="button" onClick={()=>setGrouping(true)}>＋ 묶음</button>}{structure&&<button type="button" className="primary" onClick={()=>setCreating(true)}>＋ 새 페이지</button>}</>}/>
    {grouping&&active&&structure&&<GroupDialog active={active} pages={data.pages} homePageId={home} onClose={()=>setGrouping(false)} onBusy={busy=>{moving.current=busy;}} onDone={()=>{setGrouping(false);refresh();}}/>}
    {composing&&active&&structure&&<CompositionDialog active={active} page={composing} pages={data.pages} catalog={catalog.data} onClose={()=>setComposing(null)} onBusy={busy=>{moving.current=busy;}} onDone={()=>{setComposing(null);refresh();}} onStale={refresh}/>}
    {creating&&active&&structure&&<CreatePageDraft active={active} pages={data.pages} homePageId={home} onStateChange={onCreationState} onClose={()=>setCreating(false)} onCheckList={()=>{setCreating(false);setQ('');setStatus('');refresh();}} onCreated={page=>{setCreating(false);refresh();go(pagePath(page.id));}}/>}
    {placing&&active&&structure&&<PagePlacementDialog active={active} page={placing} pages={data.pages} homePageId={home} onClose={()=>setPlacing(null)} onBusy={busy=>{moving.current=busy;}} onDone={()=>{setPlacing(null);refresh();}} onStale={refresh}/>}
    <section className="card"><div className="search-bar"><input aria-label="페이지 검색" value={q} onChange={e=>setQ(e.target.value)} placeholder="페이지 이름 검색"/><select aria-label="페이지 상태" value={status} onChange={e=>setStatus(e.target.value)}><option value="">전체 상태</option><option value="DRAFT">임시보관</option><option value="PUBLISHED">게시됨</option><option value="PRIVATE">비공개</option></select><span>{items.length}개</span>{deletion.action}</div>
      {orderError&&<p className="error-box" role="alert">{orderError}</p>}
      {deletion.feedback}{deletion.dialog}
      <div className="table-scroll"><table className="data-table pages-table"><thead><tr>{canDelete&&<th className="bulk-cell">{deletion.selectAll}</th>}<th>페이지</th><th>상태</th><th>연결된 메뉴</th><th>최근 수정</th><th>작업</th></tr></thead><tbody>{rows.map(row)}</tbody></table>
      {!items.length&&<Empty>{filtered?'조건에 맞는 페이지가 없습니다. 검색어나 상태를 바꿔보세요.':'등록된 페이지가 없습니다.'}</Empty>}</div></section>
    <p className="muted page-list-note">메뉴 연결과 게시 상태를 함께 확인하세요. 내용 저장만으로 현재 공개본이 바뀌지는 않습니다. 페이지 위치(상위 페이지)는 메뉴와 따로 관리되며, 위치를 바꿔도 주소와 메뉴는 바뀌지 않습니다.</p>
  </section>;
}

function PagePlacementDialog({active,page,pages,homePageId,onClose,onBusy,onDone,onStale}:{active:boolean;page:PageRow;pages:PageRow[];homePageId:number|null;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void;onStale:()=>void}) {
  const current=page.parentId??null;
  const [parent,setParent]=useState<number|null>(current),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const pending=useRef(false);
  const options=parentOptions(pages,page.id,homePageId);
  const topLevelOnly=page.id===homePageId;
  async function save(){
    if(pending.current||parent===current)return;
    pending.current=true;setBusy(true);onBusy(true);setError('');
    try{await placePage(page.id,parent,current);onDone();}
    catch(e){setError(messageOf(e));onStale();}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  return <BlockDialog active={active} title="페이지 위치" onClose={()=>{if(!pending.current)onClose();}}>
    <form className="page-create-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <p><strong>{pageLocation(pages,page.id)}</strong></p>
      <label htmlFor="page-parent-select"><span>상위 페이지</span>
        <select id="page-parent-select" value={parent??''} disabled={busy} onChange={e=>setParent(e.target.value?Number(e.target.value):null)}>
          <option value="">상위 없음 (최상위)</option>
          {!topLevelOnly&&options.map(option=><option key={option.id} value={option.id} disabled={!!option.reason&&option.id!==current}>{option.title}{option.reason&&option.id!==current?` — ${option.reason}`:''}</option>)}
        </select>
      </label>
      {topLevelOnly&&<p className="page-create-help">홈(첫 화면) 페이지는 최상위에 고정됩니다.</p>}
      <p className="page-create-help">페이지는 최대 2단계(최상위와 하위 페이지)까지 둘 수 있습니다. 옮긴 페이지는 새 위치의 맨 끝에 놓이고, 주소·메뉴·게시 상태는 바뀌지 않습니다.</p>
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button type="submit" className="primary" disabled={busy||parent===current}>{busy?'저장 중…':'위치 저장'}</button></div>
    </form>
  </BlockDialog>;
}

/** A GROUP is a structure node: a name and a place, no screen, publication, home or menu-target role. */
function GroupDialog({active,pages,homePageId,onClose,onBusy,onDone}:{active:boolean;pages:PageRow[];homePageId:number|null;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void}) {
  const [name,setName]=useState(''),[parent,setParent]=useState<number|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const pending=useRef(false);
  const parents=parentOptions(pages,null,homePageId).filter(option=>!option.reason);
  async function save(){
    if(pending.current||!name.trim())return;
    pending.current=true;setBusy(true);onBusy(true);setError('');
    try{await createPageGroup(name.trim(),parent);onDone();}
    catch(e){setError(messageOf(e));}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  return <BlockDialog active={active} title="묶음 추가" onClose={()=>{if(!pending.current)onClose();}}>
    <form className="page-create-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <p className="muted">화면 없이 여러 영역을 묶는 이름입니다(예: 선배들의 SSUL). 실제 화면이 필요하면 새 페이지를 만드세요. 묶음은 게시·첫 화면·메뉴 대상이 되지 않고, 메뉴에서는 하위 영역의 이름으로만 쓰입니다.</p>
      <label htmlFor="group-name"><span>묶음 이름 <span aria-hidden="true">*</span></span><input id="group-name" required maxLength={200} value={name} disabled={busy} onChange={e=>setName(e.target.value)}/></label>
      <label htmlFor="group-parent"><span>상위 <small>선택</small></span><select id="group-parent" value={parent??''} disabled={busy} onChange={e=>setParent(e.target.value?Number(e.target.value):null)}><option value="">상위 없음</option>{parents.map(option=><option key={option.id} value={option.id}>{option.title}</option>)}</select></label>
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button type="submit" className="primary" disabled={busy||!name.trim()}>{busy?'만드는 중…':'묶음 추가'}</button></div>
    </form>
  </BlockDialog>;
}

/**
 * Site composition of one area. The content-work link makes this page its type's representative work
 * area (one per type, an operating rule) and never limits where that type's posts appear; the menu
 * settings take effect on the homepage only after a structure publication (V14 step 2).
 */
function CompositionDialog({active,page,pages,catalog,onClose,onBusy,onDone,onStale}:{active:boolean;page:PageRow;pages:PageRow[];catalog:ClassificationCatalog|null;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void;onStale:()=>void}) {
  const group=isGroup(page);
  const [type,setType]=useState(page.contentTypeCode??''),[visible,setVisible]=useState(page.menuVisible),[label,setLabel]=useState(page.menuLabel??''),[name,setName]=useState(page.title);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[blockTypes,setBlockTypes]=useState<string[]|null>(null);
  const pending=useRef(false);
  useEffect(()=>{if(group)return;let cancelled=false;
    void getPage(page.id).then(doc=>{if(!cancelled)setBlockTypes(doc.sections.filter(s=>s.type==='POSTS').map(s=>(s as {query?:{typeCode?:string}}).query?.typeCode??'').filter(Boolean));}).catch(()=>{if(!cancelled)setBlockTypes([]);});
    return()=>{cancelled=true;};},[group,page.id]);
  const representedBy=(code:string)=>pages.find(p=>p.id!==page.id&&p.contentTypeCode===code);
  const types=catalog?.types.filter(t=>t.active||t.code===page.contentTypeCode)??[];
  const unchanged=type===(page.contentTypeCode??'')&&visible===page.menuVisible&&label.trim()===(page.menuLabel??'')&&(!group||name.trim()===page.title);
  async function save(){
    if(pending.current||unchanged||group&&!name.trim())return;
    pending.current=true;setBusy(true);onBusy(true);setError('');
    try{await saveComposition(page.id,{contentTypeCode:type||null,menuVisible:visible,menuLabel:label.trim()||null,name:group?name.trim():null});onDone();}
    catch(e){setError(messageOf(e));onStale();}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  const missingBlock=!group&&!!type&&blockTypes!==null&&!blockTypes.includes(type);
  return <BlockDialog active={active} title="사이트 구성" onClose={()=>{if(!pending.current)onClose();}}>
    <form className="page-create-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <p><strong>{pageLocation(pages,page.id)}</strong>{group&&<small className="muted"> · 묶음</small>}</p>
      {group&&<label htmlFor="composition-name"><span>묶음 이름</span><input id="composition-name" required maxLength={200} value={name} disabled={busy} onChange={e=>setName(e.target.value)}/></label>}
      {!group&&<label htmlFor="composition-type"><span>콘텐츠 작업 연결 <small>선택</small></span>
        <select id="composition-type" value={type} disabled={busy||!catalog} onChange={e=>setType(e.target.value)}>
          <option value="">연결 안 함 (내용 편집만)</option>
          {types.map(t=>{const other=representedBy(t.code);return <option key={t.code} value={t.code} disabled={!!other}>{t.name}{other?` — 대표 작업 영역: ${other.title}`:''}</option>;})}
        </select></label>}
      {!group&&<p className="page-create-help">연결하면 이 페이지가 그 유형의 대표 작업 영역이 되어 콘텐츠 작업에 나타나고, 유형의 주제가 하위 탐색이 됩니다. 같은 유형의 글은 다른 페이지의 콘텐츠 목록 블록에도 계속 넣을 수 있습니다.</p>}
      {missingBlock&&<p className="page-hierarchy-warning" role="note">이 페이지에는 이 유형의 콘텐츠 목록 블록이 없습니다. 홈페이지에 글을 보여 주려면 편집기에서 콘텐츠 목록 블록을 추가하세요(연결은 저장할 수 있습니다).</p>}
      <label className="checkbox-row"><input type="checkbox" checked={visible} disabled={busy} onChange={e=>setVisible(e.target.checked)}/> 홈페이지 메뉴에 노출</label>
      <label htmlFor="composition-label"><span>메뉴 표시명 <small>선택</small></span><input id="composition-label" maxLength={80} value={label} placeholder={page.title} disabled={busy} onChange={e=>setLabel(e.target.value)}/></label>
      <p className="page-create-help">메뉴 노출과 표시명은 구성 게시 뒤 홈페이지에 반영됩니다(구성 게시는 다음 단계). 비워 두면 {group?'묶음 이름':'게시된 페이지 제목'}을 씁니다. 메뉴에서 숨겨도 게시된 페이지는 주소로 계속 열립니다.</p>
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button type="submit" className="primary" disabled={busy||unchanged||group&&!name.trim()}>{busy?'저장 중…':'구성 저장'}</button></div>
    </form>
  </BlockDialog>;
}
