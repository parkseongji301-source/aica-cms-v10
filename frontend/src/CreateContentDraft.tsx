import {useEffect,useRef,useState} from 'react';
import type {ClassificationCatalog,ClassificationSelection,PostDocument} from './types';
import {createPost} from './api';
import {allowedTopicIds,toggleId} from './classification';
import {contentPresentation} from './contentPresentation';
import {messageOf} from './ui';

/** Explicit draft creation shared by both navigation sections; editing stays in ContentEditor. */
export function CreateContentDraft({selection,catalog,onCreated,onClose}:{selection:ClassificationSelection;catalog:ClassificationCatalog;onCreated:(post:PostDocument)=>void;onClose:()=>void}) {
  const dialog=useRef<HTMLDialogElement>(null),pending=useRef(false);
  const [title,setTitle]=useState(''),[topics,setTopics]=useState(selection.topicIds),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const presentation=contentPresentation(selection.typeCode),typeLabel=catalog.types.find(t=>t.code===selection.typeCode)?.name||selection.typeCode;
  const allowed=allowedTopicIds(catalog,selection.typeCode);
  useEffect(()=>{dialog.current?.showModal();},[]);
  async function create(){
    if(pending.current||!title.trim())return;pending.current=true;setBusy(true);setError('');
    try{const post=await createPost(title,{...selection,topicIds:topics});dialog.current?.close();onCreated(post);}
    catch(e){setError(messageOf(e));}finally{pending.current=false;setBusy(false);}
  }
  return <dialog ref={dialog} className="create-review" aria-labelledby="create-draft-title" onCancel={e=>{e.preventDefault();if(!busy)onClose();}}>
    <form onSubmit={e=>{e.preventDefault();void create();}}><h2 id="create-draft-title">새 {typeLabel}</h2>
      <p className="muted">{typeLabel} · 기수 선택 없음</p>
      <label>{presentation.title}<input autoFocus required maxLength={200} value={title} onChange={e=>setTitle(e.target.value)} disabled={busy}/></label>
      {selection.typeCode!=='RESTAURANT'&&<fieldset className="draft-topics"><legend>주제 <small>복수 선택 · 선택 없음 허용</small></legend>
        <div className="classification-options">{catalog.topics.filter(t=>t.active&&allowed.includes(t.id)).map(t=><label key={t.id}>
          <input type="checkbox" checked={topics.includes(t.id)} disabled={busy} onChange={()=>setTopics(toggleId(topics,t.id))}/>{t.name}
        </label>)}</div>
      </fieldset>}
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="heading-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button className="primary" disabled={busy||!title.trim()}>{busy?'만드는 중…':'초안 만들기'}</button></div>
    </form>
  </dialog>;
}
