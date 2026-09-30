import {useEffect,useRef,useState} from 'react';
import {ApiError,createPage,get,send} from './api';
import {BlockDialog} from './BlockDialog';
import {Feedback,messageOf,useRemote,useUnsaved} from './ui';
import {COLLECTION_LIMITS,collectionSections,collectionTitle} from './pageCreation';
import type {CollectionPreset,CreatedPage} from './pageCreation';
import type {ClassificationCatalog,PageRow,PreparedTemplate,TemplateDocument,TemplateSummary} from './types';
import {TemplateContents} from './PageTemplates';

export type PageCreationState={busy:boolean;dirty:boolean};
/**
 * "+ 하위 페이지": a new page is always created under the top-level item it was opened from (the server still
 * checks the placement). It starts blank, as a content collection, or as a copy of a saved page template
 * (the existing template prepare API returns fresh block copies, which become the new draft's blocks).
 */
export function CreatePageDraft({active,parent,templateUse=false,onCreated,onClose,onCheckList,onStateChange}:{
  active:boolean;parent:PageRow;templateUse?:boolean;onCreated:(page:CreatedPage)=>void;onClose:()=>void;onCheckList:()=>void;onStateChange:(state:PageCreationState)=>void;
}) {
  const [title,setTitle]=useState(''),[slug,setSlug]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[uncertain,setUncertain]=useState(false);
  const pending=useRef(false),titleInput=useRef<HTMLInputElement>(null);
  const [kind,setKind]=useState<'blank'|'collection'|'template'>('blank'),[preset,setPreset]=useState<CollectionPreset>({typeCode:'',cohortId:null,topicId:null,limit:COLLECTION_LIMITS.default});
  const suggested=useRef('');
  const catalog=useRemote<ClassificationCatalog>('/classifications',active&&kind==='collection');
  const terms=catalog.data;
  const types=terms?.types.filter(t=>t.active)??[],cohorts=terms?.cohorts.filter(c=>c.active)??[];
  const topics=terms?terms.topics.filter(t=>t.active&&terms.allowedTopics.some(a=>a.typeCode===preset.typeCode&&a.topicId===t.id)):[];
  // Saved page templates: only active ones can start a page; the chosen one is previewed before creating.
  const templates=useRemote<TemplateSummary[]>('/page-templates',active&&templateUse&&kind==='template');
  const usable=templates.data?.filter(t=>t.active)??[];
  const [templateId,setTemplateId]=useState(''),[template,setTemplate]=useState<TemplateDocument|null>(null),[templateError,setTemplateError]=useState('');
  useEffect(()=>{setTemplate(null);setTemplateError('');if(!templateId)return;const c=new AbortController();
    void get<TemplateDocument>('/page-templates/'+templateId,c.signal).then(d=>{if(!c.signal.aborted)setTemplate(d);}).catch(e=>{if(!c.signal.aborted)setTemplateError(messageOf(e));});
    return()=>c.abort();},[templateId]);
  // Keep the suggested title in step with the selection until the operator types their own.
  function choose(next:CollectionPreset){
    setPreset(next);if(!terms)return;
    const title2=collectionTitle(terms,next);if(!title.trim()||title===suggested.current){setTitle(title2);}suggested.current=title2;
  }
  const dirty=!!(title||slug)||kind!=='blank';
  const ready=kind==='blank'||kind==='collection'&&!!preset.typeCode||kind==='template'&&!!template&&template.info.active;
  useUnsaved(dirty||busy);
  useEffect(()=>{titleInput.current?.focus();return()=>onStateChange({busy:false,dirty:false});},[onStateChange]);
  useEffect(()=>onStateChange({busy,dirty}),[busy,dirty,onStateChange]);
  function close(){if(!pending.current)onClose();}
  async function create(){
    if(pending.current||!title.trim()||!ready||uncertain||!active)return;
    pending.current=true;setBusy(true);setError('');onStateChange({busy:true,dirty});
    let created:CreatedPage|undefined;
    // A template is prepared first; nothing is created if that fails (for example a template changed meanwhile).
    let sections:unknown[]=[];
    try{
      if(kind==='collection')sections=collectionSections(preset,title);
      if(kind==='template'&&template)sections=(await send<PreparedTemplate>('/page-templates/'+template.info.id+'/prepare','POST',{revision:template.info.revision})).sections;
    }catch(e){setError(messageOf(e));pending.current=false;setBusy(false);onStateChange({busy:false,dirty});return;}
    try{
      created=await createPage(title,slug,sections,parent.id);
      if(!Number.isSafeInteger(created.id)||created.id<=0)throw new Error('페이지 생성 결과를 확인하지 못했습니다.');
    }catch(e){
      const unknown=!(e instanceof ApiError&&e.status>=400&&e.status<500);
      setUncertain(unknown);setError(unknown?'응답을 확인하지 못했습니다. 페이지가 만들어졌을 수 있으니 목록에서 먼저 확인해 주세요.':messageOf(e));
      created=undefined;
    }finally{pending.current=false;setBusy(false);onStateChange({busy:false,dirty:created?false:dirty});}
    // Navigation runs only after creation has completed; no follow-up fetch can create a retry.
    if(created)onCreated(created);
  }
  return <BlockDialog active={active} title={`‘${parent.title}’ 하위 페이지 만들기`} onClose={close}>
    <form className="page-create-form" onSubmit={event=>{event.preventDefault();void create();}}>
      <p className="page-create-parent"><span>상위 항목</span><strong>{parent.title}</strong></p>
      <p className="muted">‘{parent.title}’ 아래 맨 끝에 임시보관 페이지로 만들어집니다. 순서는 목록에서 끌어서 바꿀 수 있습니다.</p>
      <fieldset className="page-create-kind" disabled={busy||uncertain}><legend>시작 방법</legend>
        <label><input type="radio" name="new-page-kind" checked={kind==='blank'} onChange={()=>setKind('blank')}/>빈 페이지</label>
        <label><input type="radio" name="new-page-kind" checked={kind==='collection'} onChange={()=>setKind('collection')}/>콘텐츠 모음 페이지 <small>유형·기수·주제로 게시된 글을 모아 보여 줍니다</small></label>
        {templateUse&&<label><input type="radio" name="new-page-kind" checked={kind==='template'} onChange={()=>setKind('template')}/>저장된 템플릿으로 시작 <small>페이지 템플릿의 블록 구성을 복사해 시작합니다</small></label>}
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
      {kind==='template'&&<fieldset className="page-create-template" disabled={busy||uncertain}><legend>템플릿</legend>
        {templates.loading&&!templates.data&&<p role="status">템플릿을 불러오는 중…</p>}<Feedback error={templates.error||templateError}/>
        {templates.data&&(usable.length?<label>사용할 템플릿 <span aria-hidden="true">*</span><select required value={templateId} onChange={e=>setTemplateId(e.target.value)}><option value="">선택하세요</option>{usable.map(t=><option key={t.id} value={t.id}>{t.name} · 블록 {t.blockCount}개</option>)}</select></label>
          :<p className="muted">사용할 수 있는 페이지 템플릿이 없습니다. 페이지 편집에서 ‘현재 구성을 템플릿으로 저장’으로 만들 수 있습니다.</p>)}
        {template&&<><p className="muted">{template.info.description||'설명 없음'}</p><TemplateContents document={template}/></>}
        <p className="page-create-help">템플릿의 복사본으로 시작합니다. 만든 뒤 템플릿을 바꾸거나 사용 중지해도 이 페이지는 바뀌지 않습니다.</p>
      </fieldset>}
      <label htmlFor="new-page-title"><span>페이지 제목 <span aria-hidden="true">*</span></span><input ref={titleInput} id="new-page-title" required maxLength={200} value={title} disabled={busy||uncertain} onChange={event=>setTitle(event.target.value)} placeholder="예: 프로젝트 후기"/></label>
      <label htmlFor="new-page-slug"><span>페이지 주소 <small>선택</small></span><input id="new-page-slug" maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" title="영문 소문자·숫자와 단어 사이 하이픈으로 입력하세요." value={slug} disabled={busy||uncertain} onChange={event=>setSlug(event.target.value.toLowerCase())} placeholder="예: project-reviews · 비워두면 자동 생성" aria-describedby="new-page-slug-help" autoCapitalize="none" spellCheck={false}/></label>
      <p id="new-page-slug-help" className="page-create-help">영문 소문자·숫자·하이픈을 사용할 수 있습니다. 이미 사용 중인 주소는 사용할 수 없습니다. 주소와 메뉴는 상위 항목과 따로 정합니다.</p>
      <Feedback error={error}/>
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={close}>취소</button>{uncertain?<button type="button" className="primary" onClick={onCheckList}>목록에서 확인</button>:<button type="submit" className="primary" disabled={busy||!title.trim()||!ready}>{busy?'페이지 만드는 중…':'만들고 편집하기'}</button>}</div>
    </form>
  </BlockDialog>;
}
