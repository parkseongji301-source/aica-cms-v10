import {useEffect,useRef,useState} from 'react';
import {get,send} from './api';
import {BlockDialog} from './BlockDialog';
import {date,messageOf} from './ui';
import {ClassificationSummary} from './ClassificationFields';
import {initialHistoryVersion,reasonLabel,type HistoryKind,type VersionDetail,type VersionList,type VersionSnapshot,type RestoreResult} from './versionHistory';
import './version-history.css';
import {blockLabel,variationLabel} from './pagePresentation';

function SnapshotView({value,html,mediaBase}:{value:VersionSnapshot;html?:string;mediaBase?:string}){
 return <div className="version-snapshot"><h3>{value.title??value.name}</h3>{value.slug&&<p>당시 주소: /{value.slug} · 주소는 복구하지 않습니다.</p>}
 {value.description!==undefined&&<p>{value.description||'설명 없음'} · {value.active?'활성':'비활성'}</p>}
 {value.classification&&<><p>기존 카테고리: {value.categoryName||'미분류'}{value.categoryId?' (#'+value.categoryId+')':''}</p><ClassificationSummary value={value.classification}/></>}
 {value.restaurant&&<p>주소: {value.restaurant.address||'입력 없음'}</p>}
 {html?<div className="rich-content" dangerouslySetInnerHTML={{__html:html}}/>:value.content!==undefined&&<p className="version-plain">{value.content||'본문 없음'}</p>}
 {!!value.attachments?.length&&<div><h4>이미지·첨부 순서</h4><ol>{value.attachments.map(m=><li key={m.id}><a href={mediaBase?mediaBase+m.id:'/admin/media/'+m.id+'/file'} target="_blank" rel="noopener noreferrer">{m.name} · #{m.id}</a></li>)}</ol></div>}
 {(value.sections||value.blocks)&&<ol className="version-blocks">{(value.sections||value.blocks)!.map((b,i)=><li key={b.id||i}>
 <strong>{i+1}. {b.heading||blockLabel(b.type)}</strong><p>{blockLabel(b.type)} · {b.visible?'표시':'숨김'} · {variationLabel(b.variation)}</p>
 {b.body&&<p className="version-plain">{b.body}</p>}{b.imageId&&<p>이미지 #{b.imageId}</p>}
 {b.label&&<p>버튼: {b.label} · {b.link}</p>}
 {b.type==='POSTS'&&<><p>콘텐츠 소스: {b.sourceMode||'category'} · category #{b.categoryId??'전체'}</p>
 {b.query&&<p>조건: {b.query.typeCode} / 기수 {b.query.cohortIds.join(', ')||'전체'} / 주제 {b.query.topicIds.join(', ')||'전체'} / {b.query.sort} / {b.query.limit}개</p>}
 {b.manual&&<p>직접 선택 순서: {b.manual.postIds.join(' → ')||'없음'}</p>}</>}
 <details><summary>전체 블록 설정 · 식별 정보</summary><pre>{JSON.stringify(b,null,2)}</pre></details>
 </li>)}</ol>}
 </div>;
}
export function VersionHistoryDialog({kind,id,active,onClose,onRestored,onBusy,onPending}:{kind:HistoryKind;id:number;active:boolean;onClose:()=>void;onRestored:(result:RestoreResult)=>Promise<void>;onBusy:(busy:boolean)=>void;onPending?:(pending:boolean)=>void}){
 const base='/'+kind+'/'+id+'/versions';
 const [list,setList]=useState<VersionList|null>(null),[page,setPage]=useState(0),[selected,setSelected]=useState<number|null>(initialHistoryVersion),[detail,setDetail]=useState<VersionDetail|null>(null);
 const [loading,setLoading]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[confirm,setConfirm]=useState(false),[discard,setDiscard]=useState(false);
 const [refresh,setRefresh]=useState(0),[applied,setApplied]=useState<RestoreResult|null>(null);
 const operation=useRef<string|null>(null);
 const [preview,setPreview]=useState<{currentHtml:string;versionHtml:string;warning:string}|null>(null);
 useEffect(()=>{onBusy(busy);return()=>onBusy(false);},[busy,onBusy]);
 useEffect(()=>{onPending?.(kind==='page-templates'&&confirm);return()=>onPending?.(false);},[kind,confirm,onPending]);
 useEffect(()=>{if(!active)return;const c=new AbortController();setLoading(true);void get<VersionList>(base+'?page='+page,c.signal).then(setList).catch(e=>{if(!c.signal.aborted)setError(messageOf(e));}).finally(()=>{if(!c.signal.aborted)setLoading(false);});return()=>c.abort();},[base,page,active,refresh]);
 useEffect(()=>{setDetail(null);setPreview(null);setConfirm(false);setError('');if(!selected||!active)return;const c=new AbortController();setLoading(true);
 void get<VersionDetail>(base+'/'+selected,c.signal).then(setDetail).catch(e=>{if(!c.signal.aborted)setError(messageOf(e));}).finally(()=>{if(!c.signal.aborted)setLoading(false);});
 if(kind==='posts')void get<{currentHtml:string;versionHtml:string;warning:string}>(base+'/'+selected+'/preview',c.signal).then(setPreview).catch(e=>{if(!c.signal.aborted)setError(messageOf(e));});
 return()=>c.abort();},[base,selected,active,kind,refresh]);
 const close=()=>{if(busy)return;if(kind==='page-templates'&&confirm)setDiscard(true);else onClose();};
 async function prepare(){if(!detail)return;operation.current=crypto.randomUUID();setError('');if(kind==='page-templates'){setBusy(true);try{setDetail(await send<VersionDetail>(base+'/'+detail.version.id+'/prepare-restore','POST',{}));setConfirm(true);}catch(e){setError(messageOf(e));}finally{setBusy(false);}}else setConfirm(true);}
 async function restore(){
  if(!detail)return;setBusy(true);setError('');
  try{const result=applied??await send<RestoreResult>(base+'/'+detail.version.id+'/restore','POST',{expectedRevision:detail.currentRevision,operationId:operation.current??(operation.current=crypto.randomUUID()),confirmed:true});setApplied(result);try{await onRestored(result);onClose();}catch(e){setError('복구는 완료됐지만 편집 화면 재조회에 실패했습니다. 다시 조회를 눌러주세요. '+messageOf(e));}}
  catch(e){setConfirm(false);setError(messageOf(e)+' 최신 상태를 다시 비교한 뒤 복구하세요.');}
  finally{setBusy(false);}
 }
 return <BlockDialog title="버전 이력" active={active} onClose={close}>
 <div className="version-history" data-history-kind={kind} data-target-id={id}>
 <p className="muted">{kind==='page-templates'?'과거 구성을 확인한 뒤 명시적으로 저장합니다. 기존 적용 페이지는 변경되지 않습니다.':'선택한 버전으로 현재 작성본을 복구합니다. 현재 공개본은 유지되며, 다시 게시해야 변경됩니다.'}</p>
 <p className="muted">자동저장은 버전 이력에 남지 않습니다. 도입 기준점은 버전 기능을 도입할 당시 보관한 기록입니다.</p>
 {error&&<div role="alert" className="error-box">{error} <button disabled={busy} onClick={()=>applied?void restore():setRefresh(n=>n+1)}>{applied?'편집 화면 다시 조회':'최신 상태 다시 비교'}</button></div>}{loading&&<p role="status">이력 조회 중…</p>}
 <div className="version-list" aria-label="버전 목록">{list?.items.map(v=><button key={v.id} aria-pressed={selected===v.id} disabled={busy||!!applied} onClick={()=>setSelected(v.id)}><strong>{date(v.createdAt)}</strong><span>{v.creatorName} · {reasonLabel(v.reason)}</span><small>버전 #{v.id}{v.sourceVersionId?' · 복구 출처 #'+v.sourceVersionId:''}</small></button>)}</div>
 {list&&!list.items.length&&<p>아직 보관된 버전이 없습니다.</p>}
 <div className="version-pagination"><button disabled={page===0||busy} onClick={()=>setPage(p=>p-1)}>이전 이력</button><span>{page+1} / {Math.max(1,Math.ceil((list?.total||0)/20))} · 총 {list?.total||0}개</span><button disabled={!list||(page+1)*20>=list.total||busy} onClick={()=>setPage(p=>p+1)}>다음 이력</button></div>
 {!selected&&!loading&&!error&&!!list?.items.length&&<p className="version-prompt">비교할 저장 기록을 선택하세요. 내용을 확인한 뒤 복구할 수 있습니다.</p>}{detail&&<><div className="version-compare"><section aria-label="현재 초안 비교"><h3>현재 {kind==='page-templates'?'템플릿':'작성본'}</h3><SnapshotView value={detail.current} html={preview?.currentHtml}/></section>
 <section aria-label="선택 버전 비교"><h3>선택 버전 #{detail.version.id} · {reasonLabel(detail.version.reason)}</h3><SnapshotView value={detail.snapshot} html={preview?.versionHtml} mediaBase={'/api/admin/next'+base+'/'+detail.version.id+'/media/'}/></section></div>
 {preview?.warning&&<p role="status">{preview.warning}</p>}
 {kind==='pages'&&<p className="muted">삭제됐던 블록은 새 블록으로 복구합니다. 글 목록은 연결 조건을 복구하며 당시 조회 결과 전체를 보관하지 않습니다.</p>}
 {confirm&&!applied&&<div className="template-confirm" role="alert"><strong>{kind==='page-templates'?'이 구성을 현재 템플릿에 저장할까요?':'현재 초안을 선택한 버전으로 복구할까요?'}</strong><p>현재 상태를 먼저 보관합니다. {kind==='page-templates'?'활성 여부도 선택 버전의 값으로 바뀝니다.':'현재 공개본과 페이지 주소는 유지됩니다.'}</p><button disabled={busy} onClick={()=>setConfirm(false)}>취소</button><button className="primary" disabled={busy} onClick={()=>void restore()}>{kind==='page-templates'?'복구 내용 저장':'확인하고 새 초안으로 복구'}</button></div>}
 {!confirm&&!applied&&detail.canRestore&&<button className="primary" disabled={busy||loading} onClick={()=>void prepare()}>{kind==='page-templates'?'작업본으로 불러오기':'새 초안으로 복구'}</button>}
 {!detail.canRestore&&<p className="muted">이력 조회만 가능합니다. 복구는 관리자에게 요청하세요.</p>}</>}
 {discard&&<div role="alert" className="template-confirm"><p>저장하지 않은 템플릿 복구 선택을 버리고 닫을까요?</p><button onClick={()=>setDiscard(false)}>계속 확인</button><button onClick={onClose}>버리고 닫기</button></div>}
 </div></BlockDialog>;
}
