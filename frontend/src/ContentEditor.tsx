import {useDocumentVisible} from './editorGuard';
import {savedTime} from './ui';
import {VersionHistoryDialog} from './VersionHistoryDialog';
import {historyRequested,type RestoreResult} from './versionHistory';
import {useEffect,useRef,useState} from 'react';
import {useEditorGuard,type GuardRegistration} from './editorGuard';
import type {Category,PostDocument,PostPreview,Classification,ClassificationCatalog,RestaurantDetails} from './types';
import {ApiError,bootstrap,getPost,getPublication,previewPost,savePost} from './api';
import {classificationProblem,classificationView,sameClassification} from './classification';
import {ClassificationFields,ClassificationSummary} from './ClassificationFields';
import {editablePost,postFingerprint as fingerprint} from './contentDocument';
import {contentPresentation} from './contentPresentation';
import {restaurantProblem} from './restaurantFields';
import {RichEditor} from './RichEditor';
import {date,messageOf} from './ui';
import './content-editor.css';

type Props={onGuard:GuardRegistration;canPublish:boolean;initial:PostDocument;catalog:ClassificationCatalog;categories:Category[];active:boolean;onList:()=>void;
  onSaved:(post:PostDocument)=>void;onMediaChange:()=>void};

export function ContentEditor({initial,catalog,categories,active,onList,onSaved,onMediaChange,onGuard,canPublish}:Props) {
  const documentVisible=useDocumentVisible();
 const [historyOpen,setHistoryOpen]=useState(()=>historyRequested()),[historyBusy,setHistoryBusy]=useState(false);
 const [doc,setDoc]=useState(()=>editablePost(initial));
  const [saved,setSaved]=useState(()=>fingerprint(editablePost(initial)));
  const [busy,setBusy]=useState(false),[uploading,setUploading]=useState(false),[blocked,setBlocked]=useState(false);
  const [fatal,setFatal]=useState(false),[authError,setAuthError]=useState(false),[epoch,setEpoch]=useState(0);
  const [message,setMessage]=useState('저장된 내용을 불러왔습니다.'),[error,setError]=useState('');
  const [preview,setPreview]=useState<PostPreview|null>(null),[previewError,setPreviewError]=useState('');
  const [previewLoading,setPreviewLoading]=useState(true),[mobile,setMobile]=useState(false);
  const [published,setPublished]=useState<Classification|null>(null),[publicationError,setPublicationError]=useState(''),[publicationLoading,setPublicationLoading]=useState(false),[publicationVersion,setPublicationVersion]=useState(0);
  const [publishedRestaurant,setPublishedRestaurant]=useState<RestaurantDetails|null>(null);
  const baseline=useRef(initial.classification);
  const live=useRef(doc),savedRef=useRef(saved),inFlight=useRef(false),uploadPending=useRef(false);
  const version=useRef(0),failed=useRef(''),previewVersion=useRef(0);
  live.current=doc;savedRef.current=saved;
  const dirty=fingerprint(doc)!==saved;
  useEditorGuard(dirty,busy||uploading||historyBusy,onGuard);
  const presentation=contentPresentation(doc.classification.typeCode);
  const classificationIssue=classificationProblem(doc.classification,catalog,baseline.current),addressIssue=restaurantProblem(doc);
  const problem=classificationIssue||addressIssue;
  const draftClassification=classificationView(doc.classification,catalog);

  function change(patch:Partial<PostDocument>) {
    version.current++;live.current={...live.current,...patch};setDoc(live.current);
    setMessage('변경사항 있음 · 잠시 후 자동 저장');if(!blocked)setError('');
  }
  async function save(automatic=false) {
    if(historyOpen||automatic&&(!active||document.hidden))return;
    const current=live.current;
    if(inFlight.current||uploadPending.current||blocked||fatal)return;
    if(classificationProblem(current.classification,catalog,baseline.current)||restaurantProblem(current))return;
    if(!current.title.trim()){if(!automatic)setError(contentPresentation(current.classification.typeCode).missingTitle);return;}
    if(automatic&&(fingerprint(current)===savedRef.current||fingerprint(current)===failed.current))return;
    const submitted=version.current;inFlight.current=true;setBusy(true);setError('');
    try {
      const result=editablePost(await savePost(current,automatic?'AUTOSAVE':'MANUAL_DRAFT'));failed.current='';setSaved(fingerprint(result));
      baseline.current=result.classification;
      live.current=submitted===version.current?result:{...live.current,revision:result.revision,status:result.status,
        publishedRevision:result.publishedRevision,pending:result.pending,attachments:result.attachments,
        mediaIds:result.mediaIds,updatedAt:result.updatedAt};
      setDoc(live.current);onSaved(result);setAuthError(false);
      setMessage((automatic?'자동 저장됨 · ':'초안 버전 저장됨 · ')+savedTime());
    } catch(e) {
      failed.current=fingerprint(current);setError(messageOf(e));setMessage('저장되지 않음');
      if(e instanceof ApiError&&[401,403,404,409].includes(e.status)){setBlocked(true);setAuthError([401,403].includes(e.status));}
    } finally {inFlight.current=false;setBusy(false);}
  }
  useEffect(()=>{
    if(historyOpen||!active||!documentVisible||!dirty||busy||uploading||blocked||fatal||problem)return;
    const timer=setTimeout(()=>void save(true),1800);return()=>clearTimeout(timer);
  },[historyOpen,active,documentVisible,doc,dirty,busy,uploading,blocked,fatal,problem]);
  useEffect(()=>{
    const leave=(event:BeforeUnloadEvent)=>{if(fingerprint(live.current)!==savedRef.current||inFlight.current||uploadPending.current){event.preventDefault();event.returnValue='';}};
    window.addEventListener('beforeunload',leave);return()=>window.removeEventListener('beforeunload',leave);
  },[]);
  useEffect(()=>{
    const shortcut=(event:KeyboardEvent)=>{if(active&&(event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();void save();}};
    document.addEventListener('keydown',shortcut);return()=>document.removeEventListener('keydown',shortcut);
  });
  useEffect(()=>{
    const controller=new AbortController(),request=++previewVersion.current;
    if(fatal||problem||!doc.title.trim()){setPreviewLoading(false);setPreviewError(problem?'유형·주제·주소를 확인한 뒤 미리보기를 갱신합니다.':fatal?'본문을 확인한 뒤 다시 조회하세요.':presentation.title+'을 입력하면 미리보기가 갱신됩니다.');return;}
    setPreviewLoading(true);setPreviewError('');
    const timer=setTimeout(()=>void previewPost(doc,controller.signal).then(result=>{
      if(!controller.signal.aborted&&request===previewVersion.current){setPreview(result);setPreviewLoading(false);}
    }).catch(e=>{if(!controller.signal.aborted&&request===previewVersion.current){setPreviewError(messageOf(e));setPreviewLoading(false);}}),250);
    return()=>{clearTimeout(timer);controller.abort();};
  },[doc.title,doc.content,doc.richContent,doc.classification,doc.restaurant,fatal,problem]);
  useEffect(()=>{
    if(!active)return;
    const controller=new AbortController();setPublicationLoading(true);setPublicationError('');
    void getPublication(doc.id,controller.signal).then(value=>{if(!controller.signal.aborted){setPublished(value.classification);setPublishedRestaurant(value.restaurant);}})
      .catch(e=>{if(!controller.signal.aborted){if(e instanceof ApiError&&e.status===404){setPublished(null);setPublishedRestaurant(null);}else setPublicationError(messageOf(e));}})
      .finally(()=>{if(!controller.signal.aborted)setPublicationLoading(false);});
    return()=>controller.abort();
  },[active,doc.id,doc.updatedAt,publicationVersion]);
  useEffect(()=>{const refresh=()=>setPublicationVersion(v=>v+1);window.addEventListener('focus',refresh);return()=>window.removeEventListener('focus',refresh);},[]);
  async function reload() {
    if(inFlight.current||uploadPending.current)return;
    if(dirty&&!window.confirm('저장되지 않은 입력을 버리고 저장된 내용을 다시 조회할까요?'))return;
    const requested=version.current;inFlight.current=true;setBusy(true);
    try {
      const result=editablePost(await getPost(initial.id));
      if(requested!==version.current){setMessage('조회 중 변경된 입력을 유지했습니다.');return;}
      live.current=result;setDoc(result);setSaved(fingerprint(result));failed.current='';setEpoch(n=>n+1);
      baseline.current=result.classification;setPublicationVersion(v=>v+1);
      setBlocked(false);setFatal(false);setAuthError(false);setError('');setMessage('저장된 내용을 다시 불러왔습니다.');onSaved(result);
    }catch(e){setError(messageOf(e));}finally{inFlight.current=false;setBusy(false);}
  }
  async function restored(_result:RestoreResult){
    const value=editablePost(await getPost(initial.id));version.current++;live.current=value;savedRef.current=fingerprint(value);
    setDoc(value);setSaved(savedRef.current);baseline.current=value.classification;failed.current='';setEpoch(n=>n+1);
    setBlocked(false);setFatal(false);setAuthError(false);setError('');setMessage('새 초안으로 복구했습니다. 발행본은 유지됩니다.');onSaved(value);
  }
  function uploadState(value:boolean){uploadPending.current=value;setUploading(value);}
  const category=categories.find(c=>c.id===doc.categoryId)?.name||'미분류';

  return <section className="content-editor" data-testid="content-editor" data-content-id={doc.id}>
    <div className="editor-heading"><div><p className="eyebrow">{presentation.heading}</p><h1>{doc.title.trim()||initial.title}</h1>
      <p className="target-caption">{doc.authorName} · 최근 수정 {date(doc.updatedAt)}</p></div>
      <div className="heading-actions"><button onClick={onList}>목록</button><a aria-disabled={dirty||busy||uploading||blocked} href={dirty||busy||uploading||blocked?undefined:'/admin/posts/'+doc.id+'/edit'} target="_blank" rel="noopener noreferrer">{canPublish?'발행·상세 관리':'기존 편집 화면'} <span>기존 화면 ↗</span></a></div>
    </div>
    <div className="editor-actions"><div><span className="status-tag">{doc.status==='PUBLISHED'?'발행본 있음':doc.status==='PRIVATE'?'비공개':'임시저장'}</span>
      {doc.pending&&<span className="pending-tag">미반영 수정</span>}<span className="save-message" role="status">{busy?'저장·조회 중…':uploading?'파일 업로드 중…':problem?'입력 확인 필요 · 저장 대기':message}</span></div>
      <div className="action-buttons"><button disabled={busy||uploading} onClick={()=>{if(dirty)setError('초안을 저장한 뒤 버전 이력을 열어 주세요.');else setHistoryOpen(true);}}>버전 이력</button><button disabled={busy||uploading} onClick={()=>void reload()}>다시 조회</button><button className="primary" disabled={busy||uploading||blocked||fatal||!!problem} onClick={()=>void save()}>초안 저장</button></div>
    </div>
    {error&&<div className="error-box" role="alert">{error}{authError&&<div><a href="/login" target="_blank" rel="noopener">새 탭에서 로그인</a><button onClick={()=>void bootstrap().then(()=>{setBlocked(false);setAuthError(false);setError('');}).catch(e=>setError(messageOf(e)))}>로그인 상태 다시 확인</button></div>}</div>}
    <div className="editor-grid"><div className="editing-column card content-canvas">
      <ClassificationFields value={doc.classification} catalog={catalog} baseline={baseline.current} problem={classificationIssue} onChange={patch=>change({classification:{...doc.classification,...patch}})}/>
      {addressIssue&&<div className="classification-warning" role="alert"><p>{addressIssue}</p>{doc.classification.typeCode!=='RESTAURANT'&&<button onClick={()=>change({restaurant:{address:''}})}>주소를 비우고 유형 변경</button>}</div>}
      <div className="content-metadata"><label htmlFor={'content-category-'+doc.id}>기존 카테고리</label>
        <select id={'content-category-'+doc.id} value={doc.categoryId??''} onChange={e=>change({categoryId:e.target.value?Number(e.target.value):null})}>
          <option value="">미분류</option>{categories.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
        </select><label htmlFor={'content-title-'+doc.id}>{presentation.title}</label><input id={'content-title-'+doc.id} required maxLength={200} value={doc.title} onChange={e=>change({title:e.target.value})}/>
      </div>
      {doc.classification.typeCode==='RESTAURANT'&&<label className="restaurant-address">주소 <small>선택 입력 · 최대 500자</small><input aria-label="주소" maxLength={500} value={doc.restaurant?.address||''} onChange={e=>change({restaurant:{address:e.target.value}})}/></label>}
      <p className="content-field-label">{presentation.body}</p>
      <RichEditor key={epoch} document={doc.richContent} plain={doc.content} label={presentation.editorLabel} advanced
        onChange={(content,richContent)=>change({content,richContent})} onUploadState={uploadState} onMediaChange={onMediaChange}
        onFatalError={message=>{setFatal(true);setError(message);}}/>
      <div className="content-foot"><span>사진·첨부 최대 12개 · 파일당 5MB</span><span>초안 저장 시 기존 발행본 유지</span></div>
    </div><aside className="card preview-panel" aria-label="콘텐츠 미리보기"><header><div><span className="live-dot"/>실시간 미리보기</div>
      <div className="preview-toggle"><button aria-pressed={!mobile} onClick={()=>setMobile(false)}>PC</button><button aria-pressed={mobile} onClick={()=>setMobile(true)}>모바일</button></div></header>
      <p className="preview-caption">작성 내용 미리보기 · 실제 홈페이지 연결 전</p>
      <div className="classification-comparison">
        <section aria-label="초안 분류"><h3>{dirty?'작성 중 분류 · 미저장':'저장된 초안 분류'}</h3><ClassificationSummary value={draftClassification}/></section>
        <section className="published-classification" aria-label="발행본 분류"><h3>현재 발행본 분류</h3>
          {publicationLoading?<p className="muted">확인 중…</p>:publicationError?<p className="error-box" role="alert">{publicationError}</p>:published?<>
            <ClassificationSummary value={published}/><p className="classification-difference">{sameClassification(draftClassification,published)?'분류가 발행본과 같습니다.':'발행본과 다른 분류입니다. 초안 저장만으로 발행본은 바뀌지 않습니다.'}</p>
          </>:<p className="muted">현재 공개된 발행본이 없습니다.</p>}
          <button className="text-link" onClick={()=>setPublicationVersion(v=>v+1)}>발행본 분류 새로고침</button>
        </section>
        {(doc.classification.typeCode==='RESTAURANT'||publishedRestaurant)&&<section className="restaurant-comparison" aria-label="주소 초안과 발행본"><h3>주소</h3><p>현재 초안: {doc.classification.typeCode==='RESTAURANT'?(doc.restaurant?.address||'입력 없음'):'맛집 유형 아님'}</p><p>현재 발행본: {publicationLoading?'확인 중…':publicationError?'조회 실패':publishedRestaurant?(publishedRestaurant.address||'입력 없음'):'발행 주소 없음'}</p><small>주소는 다시 발행할 때 공개 내용에 반영됩니다.</small></section>}
      </div>
      {previewLoading&&<p className="preview-feedback" role="status">미리보기 갱신 중…</p>}{previewError&&<p className="error-box" role="alert">{previewError}</p>}
      <div className={'preview-scroll'+(mobile?' mobile':'')}><article className={'preview-paper'+(previewLoading||previewError?' preview-stale':'')} data-testid="content-preview">
        <p className="preview-category">{category}</p><h1>{preview?.title||presentation.title}</h1>{doc.classification.typeCode==='RESTAURANT'&&preview?.classification.typeCode==='RESTAURANT'&&<p className="preview-address">주소 · {preview.restaurant?.address||'입력 없음'}</p>}<div className="rich-content" dangerouslySetInnerHTML={{__html:preview?.bodyHtml||''}}/>
        {preview?.attachments.map(file=>file.mime.startsWith('image/')?<img key={file.id} src={'/admin/media/'+file.id+'/file'} alt={file.alt}/>:<a key={file.id} href={'/admin/media/'+file.id+'/file'} download>{file.name}</a>)}
      </article></div>
      {doc.status==='PUBLISHED'&&doc.publishedRevision!==null&&<a className="publication-link" href={'/admin/posts/'+doc.id+'/publication'} target="_blank" rel="noopener noreferrer">기존 발행본 확인 ↗</a>}
    </aside></div>
    {historyOpen&&<VersionHistoryDialog kind="posts" id={doc.id} active={active} onClose={()=>setHistoryOpen(false)} onRestored={restored} onBusy={setHistoryBusy}/>}
  </section>;
}
