import {useEffect,useRef,useState} from 'react';
import {ApiError,createPage} from './api';
import {BlockDialog} from './BlockDialog';
import {Feedback,messageOf,useRemote,useUnsaved} from './ui';
import {COLLECTION_LIMITS,collectionSections,collectionTitle} from './pageCreation';
import type {CollectionPreset,CreatedPage} from './pageCreation';
import type {ClassificationCatalog,PageRow} from './types';
import {parentOptions} from './pageHierarchy';

export type PageCreationState={busy:boolean;dirty:boolean};
export function CreatePageDraft({active,onCreated,onClose,onCheckList,onStateChange,pages=[],homePageId=null}:{
  active:boolean;onCreated:(page:CreatedPage)=>void;onClose:()=>void;onCheckList:()=>void;onStateChange:(state:PageCreationState)=>void;
  pages?:PageRow[];homePageId?:number|null;
}) {
  // Default: no parent. Only pages that can take a child page are offered (top-level, not the home page).
  const [parentId,setParentId]=useState<number|null>(null);
  const parents=parentOptions(pages,null,homePageId).filter(option=>!option.reason);
  const [title,setTitle]=useState(''),[slug,setSlug]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[uncertain,setUncertain]=useState(false);
  const pending=useRef(false),titleInput=useRef<HTMLInputElement>(null);
  const [kind,setKind]=useState<'blank'|'collection'>('blank'),[preset,setPreset]=useState<CollectionPreset>({typeCode:'',cohortId:null,topicId:null,limit:COLLECTION_LIMITS.default});
  const suggested=useRef('');
  const catalog=useRemote<ClassificationCatalog>('/classifications',active&&kind==='collection');
  const terms=catalog.data;
  const types=terms?.types.filter(t=>t.active)??[],cohorts=terms?.cohorts.filter(c=>c.active)??[];
  const topics=terms?terms.topics.filter(t=>t.active&&terms.allowedTopics.some(a=>a.typeCode===preset.typeCode&&a.topicId===t.id)):[];
  // Keep the suggested title in step with the selection until the operator types their own.
  function choose(next:CollectionPreset){
    setPreset(next);if(!terms)return;
    const title2=collectionTitle(terms,next);if(!title.trim()||title===suggested.current){setTitle(title2);}suggested.current=title2;
  }
  const dirty=!!(title||slug)||kind==='collection'||parentId!==null;
  const ready=kind==='blank'||!!preset.typeCode;
  useUnsaved(dirty||busy);
  useEffect(()=>{titleInput.current?.focus();return()=>onStateChange({busy:false,dirty:false});},[onStateChange]);
  useEffect(()=>onStateChange({busy,dirty}),[busy,dirty,onStateChange]);
  function close(){if(!pending.current)onClose();}
  async function create(){
    if(pending.current||!title.trim()||!ready||uncertain||!active)return;
    pending.current=true;setBusy(true);setError('');onStateChange({busy:true,dirty});
    let created:CreatedPage|undefined;
    try{
      created=await createPage(title,slug,kind==='collection'?collectionSections(preset,title):[],parentId);
      if(!Number.isSafeInteger(created.id)||created.id<=0)throw new Error('페이지 생성 결과를 확인하지 못했습니다.');
    }catch(e){
      const unknown=!(e instanceof ApiError&&e.status>=400&&e.status<500);
      setUncertain(unknown);setError(unknown?'응답을 확인하지 못했습니다. 페이지가 만들어졌을 수 있으니 목록에서 먼저 확인해 주세요.':messageOf(e));
      created=undefined;
    }finally{pending.current=false;setBusy(false);onStateChange({busy:false,dirty:created?false:dirty});}
    // Navigation runs only after creation has completed; no follow-up fetch can create a retry.
    if(created)onCreated(created);
  }
  return <BlockDialog active={active} title="새 페이지 만들기" onClose={close}>
    <form className="page-create-form" onSubmit={event=>{event.preventDefault();void create();}}>
      <p className="muted">페이지를 만든 뒤 글 목록·이미지·본문 블록을 추가할 수 있습니다. 먼저 임시보관으로 만들어집니다.</p>
      <fieldset className="page-create-kind" disabled={busy||uncertain}><legend>만들 페이지</legend>
        <label><input type="radio" name="new-page-kind" checked={kind==='blank'} onChange={()=>setKind('blank')}/>빈 페이지</label>
        <label><input type="radio" name="new-page-kind" checked={kind==='collection'} onChange={()=>setKind('collection')}/>콘텐츠 모음 페이지 <small>유형·기수·주제로 게시된 글을 모아 보여 줍니다</small></label>
      </fieldset>
      {kind==='collection'&&<fieldset className="page-create-collection" disabled={busy||uncertain}><legend>모을 콘텐츠</legend>
        {catalog.loading&&!terms&&<p role="status">분류를 불러오는 중…</p>}<Feedback error={catalog.error}/>
        {terms&&<>
          <label>콘텐츠 유형 <span aria-hidden="true">*</span><select required value={preset.typeCode} onChange={e=>choose({...preset,typeCode:e.target.value,topicId:null})}><option value="">선택하세요</option>{types.map(t=><option key={t.code} value={t.code}>{t.name}</option>)}</select></label>
          <label>기수 <small>선택</small><select value={preset.cohortId??''} onChange={e=>choose({...preset,cohortId:e.target.value?Number(e.target.value):null})}><option value="">전체 기수</option>{cohorts.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
          <label>주제 <small>선택</small><select value={preset.topicId??''} disabled={!preset.typeCode||!topics.length} onChange={e=>choose({...preset,topicId:e.target.value?Number(e.target.value):null})}><option value="">{preset.typeCode&&!topics.length?'이 유형에는 주제가 없습니다':'전체 주제'}</option>{topics.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
          <label>표시 개수<select value={preset.limit} onChange={e=>setPreset({...preset,limit:Number(e.target.value)})}>{Array.from({length:COLLECTION_LIMITS.max},(_,i)=>i+1).map(n=><option key={n} value={n}>{n}개</option>)}</select></label>
        </>}
        <p className="page-create-help">게시된 글 중 조건에 맞는 글이 최신순으로 보입니다. 새 글은 게시되는 즉시 목록에 반영됩니다. 조건을 바꾸면 페이지를 다시 게시해야 공개본에 반영됩니다. 조건은 만든 뒤 글 목록 블록에서 바꿀 수 있습니다.</p>
      </fieldset>}
      <label htmlFor="new-page-title"><span>페이지 제목 <span aria-hidden="true">*</span></span><input ref={titleInput} id="new-page-title" required maxLength={200} value={title} disabled={busy||uncertain} onChange={event=>setTitle(event.target.value)} placeholder="예: 교육생 후기"/></label>
      <label htmlFor="new-page-slug"><span>페이지 주소 <small>선택</small></span><input id="new-page-slug" maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" title="영문 소문자·숫자와 단어 사이 하이픈으로 입력하세요." value={slug} disabled={busy||uncertain} onChange={event=>setSlug(event.target.value.toLowerCase())} placeholder="예: reviews · 비워두면 자동 생성" aria-describedby="new-page-slug-help" autoCapitalize="none" spellCheck={false}/></label>
      <p id="new-page-slug-help" className="page-create-help">영문 소문자·숫자·하이픈을 사용할 수 있습니다. 이미 사용 중인 주소는 사용할 수 없습니다.</p>
      <label htmlFor="new-page-parent"><span>상위 페이지 <small>선택</small></span><select id="new-page-parent" value={parentId??''} disabled={busy||uncertain} onChange={e=>setParentId(e.target.value?Number(e.target.value):null)}><option value="">상위 없음</option>{parents.map(option=><option key={option.id} value={option.id}>{option.title}</option>)}</select></label>
      <p className="page-create-help">상위 페이지를 고르면 그 아래 맨 끝에 하위 페이지로 만들어집니다. 주소와 메뉴는 상위 페이지와 따로 정합니다.</p>
      <Feedback error={error}/>
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={close}>취소</button>{uncertain?<button type="button" className="primary" onClick={onCheckList}>목록에서 확인</button>:<button type="submit" className="primary" disabled={busy||!title.trim()||!ready}>{busy?'페이지 만드는 중…':'만들고 편집하기'}</button>}</div>
    </form>
  </BlockDialog>;
}
