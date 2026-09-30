import {useRef,useState} from 'react';
import {BlockDialog} from './BlockDialog';
import {importMenus,publishStructure,republishStructure} from './api';
import type {MenuImportPlan,PublicMenuItem,StructurePublication,StructureStatus} from './types';
import {date,messageOf,useRemote} from './ui';

// 구성 게시 (V14 step 2). The composition in 전체 페이지 현황 is the working draft; the homepage menu and
// structure change only when it is published. Until the first publication the managed menus stay public.
type Remote<T>={data:T|null;error:string;loading:boolean;reload:()=>void};
type Props={active:boolean;status:Remote<StructureStatus>;onChanged:()=>void;onBusy:(busy:boolean)=>void};

export function StructurePublicationBar({active,status,onChanged,onBusy}:Props) {
  const [dialog,setDialog]=useState<'publish'|'history'|'import'|null>(null);
  const s=status.data;
  const done=(message?:string)=>{setDialog(null);setNotice(message??'');onChanged();status.reload();};
  const [notice,setNotice]=useState('');
  return <section className="card structure-publication" aria-label="구성 게시">
    <div className="structure-publication-summary">
      <div>
        <strong>구성 게시</strong>
        {status.loading&&!s&&<span role="status"> 확인 중…</span>}
        {status.error&&<span className="error-box" role="alert">{status.error}</span>}
        {s&&(s.latest
          ?<span className="structure-publication-state">최근 게시 {date(s.latest.publishedAt)} · {s.latest.publisherName}{s.latest.reason==='REPUBLISH'?` · 게시본 #${s.latest.sourcePublicationId} 다시 게시`:''}</span>
          :<span className="structure-publication-state">아직 게시한 적 없음 · 홈페이지는 기존 메뉴 관리의 메뉴를 쓰고 있습니다</span>)}
        {s&&<small className="row-meta">{s.changed?`게시하지 않은 변경 ${s.changes.length}건`:'게시된 구성과 같음'}{s.errors.length?` · 오류 ${s.errors.length}건`:''}{s.warnings.length?` · 확인할 점 ${s.warnings.length}건`:''}</small>}
      </div>
      <div className="heading-actions">
        {s&&!s.latest&&<button type="button" onClick={()=>setDialog('import')}>현재 메뉴에서 가져오기</button>}
        {s?.latest&&<button type="button" onClick={()=>setDialog('history')}>게시 이력</button>}
        <button type="button" className="primary" disabled={!s||!s.changed} onClick={()=>setDialog('publish')}>구성 게시</button>
      </div>
    </div>
    {notice&&<p className="success-box" role="status">{notice}</p>}
    {dialog==='publish'&&s&&<PublishDialog active={active} status={s} onClose={()=>setDialog(null)} onBusy={onBusy} onDone={()=>done('구성을 게시했습니다. 홈페이지 메뉴와 구조가 새 구성을 따릅니다.')} onStale={()=>{status.reload();onChanged();}}/>}
    {dialog==='history'&&s&&<HistoryDialog active={active} status={s} onClose={()=>setDialog(null)} onBusy={onBusy} onDone={removed=>done('이전 구성을 다시 게시했습니다.'+(removed.length?` 지금 없는 영역은 빼고 게시했습니다: ${removed.join(', ')}`:''))} onStale={()=>{status.reload();onChanged();}}/>}
    {dialog==='import'&&s&&<ImportDialog active={active} onClose={()=>setDialog(null)} onBusy={onBusy} onDone={()=>done('현재 메뉴의 노출과 순서를 구성에 옮겼습니다. 아직 게시되지 않았습니다.')} onStale={()=>{status.reload();onChanged();}}/>}
  </section>;
}

/** Menu items as an indented outline (key/parentKey). */
export function MenuOutline({items,empty}:{items:PublicMenuItem[];empty:string}) {
  if(!items.length)return <p className="muted">{empty}</p>;
  const depth=(item:PublicMenuItem):number=>{let d=0,parent=item.parentKey;while(parent&&d<5){d++;parent=items.find(i=>i.key===parent)?.parentKey??null;}return d;};
  const kind=(item:PublicMenuItem)=>item.kind==='LINK'?'외부 링크':item.kind==='CATEGORY'?'카테고리':item.kind==='GROUP'?'이름만':'페이지';
  return <ul className="menu-outline">{items.map(item=><li key={item.key} style={{paddingInlineStart:`${depth(item)*1.25}rem`}}>
    <span>{item.label}</span><small className="muted"> · {kind(item)}{item.slug?` · /${item.slug}`:''}{item.kind==='LINK'&&item.url?` · ${item.url}`:''}</small></li>)}</ul>;
}

function Issues({title,items,tone}:{title:string;items:{message:string}[];tone:'error'|'warning'}) {
  if(!items.length)return null;
  return <div className={tone==='error'?'error-box':'page-hierarchy-warning'} role={tone==='error'?'alert':'note'}><strong>{title}</strong><ul>{items.map((item,i)=><li key={i}>{item.message}</li>)}</ul></div>;
}

function useAction(onBusy:(busy:boolean)=>void) {
  const pending=useRef(false);const [busy,setBusy]=useState(false),[error,setError]=useState('');
  async function run(action:()=>Promise<void>,onStale:()=>void){
    if(pending.current)return;pending.current=true;setBusy(true);onBusy(true);setError('');
    try{await action();}catch(e){setError(messageOf(e));onStale();}
    finally{pending.current=false;setBusy(false);onBusy(false);}
  }
  return {busy,error,run,pending};
}

function PublishDialog({active,status,onClose,onBusy,onDone,onStale}:{active:boolean;status:StructureStatus;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void;onStale:()=>void}) {
  const action=useAction(onBusy);const blocked=status.errors.length>0;
  return <BlockDialog active={active} title="구성 게시" className="structure-dialog" onClose={()=>{if(!action.pending.current)onClose();}}>
    <div className="page-create-form">
      <p>{status.latest?'게시된 구성과 달라진 점을 확인한 뒤 게시하세요.':'첫 구성 게시입니다. 게시하면 홈페이지 메뉴가 기존 메뉴 관리 대신 이 구성을 따릅니다.'}</p>
      <Issues title="게시할 수 없는 문제" items={status.errors} tone="error"/>
      <Issues title="확인할 점" items={status.warnings} tone="warning"/>
      <h3>변경 내용 {status.changes.length}건</h3>
      {status.changes.length?<ul className="structure-changes">{status.changes.map((c,i)=><li key={i}><strong>{c.label}</strong> · {c.detail}</li>)}</ul>:<p className="muted">변경 없음</p>}
      <div className="structure-menu-compare">
        <section><h3>지금 홈페이지 메뉴</h3><MenuOutline items={status.publicMenus} empty="메뉴 없음"/></section>
        <section><h3>게시 후 메뉴</h3><MenuOutline items={status.draftMenus} empty="메뉴 없음"/></section>
      </div>
      <p className="page-create-help">메뉴 링크는 각 페이지의 현재 게시본 주소를 따릅니다. 공개되지 않은 페이지는 링크 없이 이름만 나오거나 빠집니다. 메뉴에서 숨긴 게시 페이지도 주소로는 계속 열립니다.</p>
      {action.error&&<p className="error-box" role="alert">{action.error}</p>}
      <div className="dialog-actions"><button type="button" disabled={action.busy} onClick={onClose}>취소</button>
        <button type="button" className="primary" disabled={action.busy||blocked} onClick={()=>void action.run(async()=>{await publishStructure(status.draftFingerprint,status.latest?.id??null);onDone();},onStale)}>{action.busy?'게시 중…':'구성 게시'}</button></div>
    </div>
  </BlockDialog>;
}

function HistoryDialog({active,status,onClose,onBusy,onDone,onStale}:{active:boolean;status:StructureStatus;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:(removed:string[])=>void;onStale:()=>void}) {
  const history=useRemote<StructurePublication[]>('/site-structure/publications',active);
  const action=useAction(onBusy);const [chosen,setChosen]=useState<number|null>(null);
  return <BlockDialog active={active} title="구성 게시 이력" className="structure-dialog" onClose={()=>{if(!action.pending.current)onClose();}}>
    <div className="page-create-form">
      <p className="muted">이전 구성을 다시 게시하면 홈페이지 메뉴·구조만 그때로 돌아갑니다. 전체 페이지 현황의 작업 중 구성은 바뀌지 않습니다. 지금 없는 영역은 빼고 게시합니다.</p>
      {history.loading&&<p role="status">불러오는 중…</p>}{history.error&&<p className="error-box" role="alert">{history.error}</p>}
      <ul className="structure-history">{history.data?.map(p=><li key={p.id}>
        <label><input type="radio" name="structure-publication" disabled={action.busy||p.id===status.latest?.id} checked={chosen===p.id} onChange={()=>setChosen(p.id)}/>
          <span>#{p.id} · {date(p.publishedAt)} · {p.publisherName}{p.reason==='REPUBLISH'?` · #${p.sourcePublicationId} 다시 게시`:''}<small className="row-meta">영역 {p.areas}개 · 메뉴 노출 {p.menuAreas}개{p.id===status.latest?.id?' · 현재 게시본':''}</small></span></label></li>)}</ul>
      {action.error&&<p className="error-box" role="alert">{action.error}</p>}
      <div className="dialog-actions"><button type="button" disabled={action.busy} onClick={onClose}>닫기</button>
        <button type="button" className="primary" disabled={action.busy||chosen===null} onClick={()=>void action.run(async()=>{const r=await republishStructure(chosen!,status.latest?.id??null);onDone(r.removed);},onStale)}>{action.busy?'게시 중…':'이 구성 다시 게시'}</button></div>
    </div>
  </BlockDialog>;
}

function ImportDialog({active,onClose,onBusy,onDone,onStale}:{active:boolean;onClose:()=>void;onBusy:(busy:boolean)=>void;onDone:()=>void;onStale:()=>void}) {
  const plan=useRemote<MenuImportPlan>('/site-structure/menu-import',active);
  const action=useAction(onBusy);const p=plan.data;
  return <BlockDialog active={active} title="현재 메뉴에서 가져오기" className="structure-dialog" onClose={()=>{if(!action.pending.current)onClose();}}>
    <div className="page-create-form">
      <p className="muted">메뉴 관리에 있는 페이지 메뉴의 노출과 순서를 구성에 옮깁니다. 게시하지 않으며, 기존 메뉴는 지우지 않습니다.</p>
      {plan.loading&&<p role="status">확인 중…</p>}{plan.error&&<p className="error-box" role="alert">{plan.error}</p>}
      {p&&<><h3>바뀌는 점 {p.changes.length}건</h3>
        {p.changes.length?<ul className="structure-changes">{p.changes.map((c,i)=><li key={i}><strong>{c.label}</strong> · {c.detail}</li>)}</ul>:<p className="muted">가져올 변경이 없습니다.</p>}
        {!!p.notes.length&&<div className="page-hierarchy-warning" role="note"><strong>참고</strong><ul>{p.notes.map((n,i)=><li key={i}>{n}</li>)}</ul></div>}</>}
      {action.error&&<p className="error-box" role="alert">{action.error}</p>}
      <div className="dialog-actions"><button type="button" disabled={action.busy} onClick={onClose}>취소</button>
        <button type="button" className="primary" disabled={action.busy||!p||!p.changes.length} onClick={()=>void action.run(async()=>{await importMenus(p!.draftFingerprint);onDone();},()=>{plan.reload();onStale();})}>{action.busy?'가져오는 중…':'구성에 반영'}</button></div>
    </div>
  </BlockDialog>;
}
