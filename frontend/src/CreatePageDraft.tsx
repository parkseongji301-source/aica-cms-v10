import {useEffect,useRef,useState} from 'react';
import {ApiError,createPage} from './api';
import {BlockDialog} from './BlockDialog';
import {Feedback,messageOf,useUnsaved} from './ui';
import type {CreatedPage} from './pageCreation';

export type PageCreationState={busy:boolean;dirty:boolean};
export function CreatePageDraft({active,onCreated,onClose,onCheckList,onStateChange}:{
  active:boolean;onCreated:(page:CreatedPage)=>void;onClose:()=>void;onCheckList:()=>void;onStateChange:(state:PageCreationState)=>void;
}) {
  const [title,setTitle]=useState(''),[slug,setSlug]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[uncertain,setUncertain]=useState(false);
  const pending=useRef(false),titleInput=useRef<HTMLInputElement>(null);
  const dirty=!!(title||slug);
  useUnsaved(dirty||busy);
  useEffect(()=>{titleInput.current?.focus();return()=>onStateChange({busy:false,dirty:false});},[onStateChange]);
  useEffect(()=>onStateChange({busy,dirty}),[busy,dirty,onStateChange]);
  function close(){if(!pending.current)onClose();}
  async function create(){
    if(pending.current||!title.trim()||uncertain||!active)return;
    pending.current=true;setBusy(true);setError('');onStateChange({busy:true,dirty});
    let created:CreatedPage|undefined;
    try{
      created=await createPage(title,slug);
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
      <label htmlFor="new-page-title"><span>페이지 제목 <span aria-hidden="true">*</span></span><input ref={titleInput} id="new-page-title" required maxLength={200} value={title} disabled={busy||uncertain} onChange={event=>setTitle(event.target.value)} placeholder="예: 교육생 후기"/></label>
      <label htmlFor="new-page-slug"><span>페이지 주소 <small>선택</small></span><input id="new-page-slug" maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" title="영문 소문자·숫자와 단어 사이 하이픈으로 입력하세요." value={slug} disabled={busy||uncertain} onChange={event=>setSlug(event.target.value.toLowerCase())} placeholder="예: reviews · 비워두면 자동 생성" aria-describedby="new-page-slug-help" autoCapitalize="none" spellCheck={false}/></label>
      <p id="new-page-slug-help" className="page-create-help">영문 소문자·숫자·하이픈을 사용할 수 있습니다. 이미 사용 중인 주소는 사용할 수 없습니다.</p>
      <Feedback error={error}/>
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={close}>취소</button>{uncertain?<button type="button" className="primary" onClick={onCheckList}>목록에서 확인</button>:<button type="submit" className="primary" disabled={busy||!title.trim()}>{busy?'페이지 만드는 중…':'만들고 편집하기'}</button>}</div>
    </form>
  </BlockDialog>;
}
