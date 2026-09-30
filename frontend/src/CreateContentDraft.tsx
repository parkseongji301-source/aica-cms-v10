import {useEffect,useRef,useState} from 'react';
import type {ClassificationCatalog,ClassificationSelection,PostDocument} from './types';
import {createPost} from './api';
import {allowedTopicIds,toggleId} from './classification';
import {contentPresentation} from './contentPresentation';
import {messageOf} from './ui';

/** Explicit draft creation shared by both navigation sections; editing stays in ContentEditor. */
export function CreateContentDraft({selection,catalog,allowTypeSelection=false,categoryId=null,contextLabel,onCreated,onClose}:{selection:ClassificationSelection;catalog:ClassificationCatalog;allowTypeSelection?:boolean;categoryId?:number|null;contextLabel?:string;onCreated:(post:PostDocument)=>void;onClose:()=>void}) {
  const dialog=useRef<HTMLDialogElement>(null),pending=useRef(false);
  const [typeCode,setTypeCode]=useState(selection.typeCode);
  const [title,setTitle]=useState(''),[topics,setTopics]=useState(selection.topicIds),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const presentation=contentPresentation(typeCode),typeLabel=catalog.types.find(t=>t.code===typeCode)?.name||typeCode;
  const allowed=allowedTopicIds(catalog,typeCode),availableTopics=catalog.topics.filter(t=>t.active&&allowed.includes(t.id));
  const canCreate=catalog.types.some(t=>t.code===typeCode&&t.active)&&!!title.trim();
  const chooseType=(code:string)=>{setTypeCode(code);setTopics(current=>current.filter(id=>allowedTopicIds(catalog,code).includes(id)));setError('');};
  useEffect(()=>{dialog.current?.showModal();},[]);
  async function create(){
    if(pending.current||!canCreate)return;pending.current=true;setBusy(true);setError('');
    try{const post=await createPost(title,{...selection,typeCode,topicIds:topics},categoryId);dialog.current?.close();onCreated(post);}
    catch(e){setError(messageOf(e));}finally{pending.current=false;setBusy(false);}
  }
  return <dialog ref={dialog} className="create-review" aria-labelledby="create-draft-title" onCancel={e=>{e.preventDefault();if(!busy)onClose();}}>
    <form onSubmit={e=>{e.preventDefault();void create();}}><h2 id="create-draft-title">새 {contextLabel||typeLabel||'글'} 작성</h2>
      <p className="muted">{contextLabel?(selection.topicIds.length?'선택한 위치의 유형·주제를 기본값으로 넣었습니다. 주제는 아래에서 조정할 수 있습니다.':'선택한 위치의 글 종류로 작성합니다.'):typeLabel?`${typeLabel} · 기수 선택 없음`:'글 종류를 선택한 뒤 제목을 입력하세요.'}</p>
      <p className="muted">제목을 입력하고 작성을 시작하면 임시보관 글이 만들어집니다. 기수는 편집 화면에서 선택할 수 있습니다.</p>
      {allowTypeSelection&&<label>글 종류<select autoFocus={!typeCode} required value={typeCode} disabled={busy} onChange={e=>chooseType(e.target.value)}><option value="">글 종류를 선택하세요</option>{catalog.types.filter(t=>t.active).map(t=><option key={t.code} value={t.code}>{t.name}</option>)}</select></label>}
      <label>{presentation.title}<input autoFocus={!!typeCode} required maxLength={200} value={title} onChange={e=>setTitle(e.target.value)} disabled={busy}/></label>
      {typeCode!=='RESTAURANT'&&availableTopics.length>0&&<fieldset className="draft-topics"><legend>주제 <small>복수 선택 · 선택 없음 허용</small></legend>
        <div className="classification-options">{availableTopics.map(t=><label key={t.id}>
          <input type="checkbox" checked={topics.includes(t.id)} disabled={busy} onChange={()=>setTopics(toggleId(topics,t.id))}/>{t.name}
        </label>)}</div>
      </fieldset>}
      {error&&<p className="error-box" role="alert">{error}</p>}
      <div className="heading-actions"><button type="button" disabled={busy} onClick={onClose}>취소</button><button className="primary" disabled={busy||!canCreate}>{busy?'만드는 중…':'작성 시작'}</button></div>
    </form>
  </dialog>;
}
