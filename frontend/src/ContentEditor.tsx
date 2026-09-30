import {useDocumentVisible} from './editorGuard';
import {savedTime} from './ui';
import {VersionHistoryDialog} from './VersionHistoryDialog';
import {BlockDialog} from './BlockDialog';
import {historyRequested,type RestoreResult} from './versionHistory';
import {useEffect,useRef,useState} from 'react';
import {useEditorGuard,type GuardRegistration} from './editorGuard';
import type {Category,PostDocument,PostPreview,Classification,ClassificationCatalog,RestaurantDetails} from './types';
import {ApiError,bootstrap,getPost,getPublication,previewPost,savePost,publishPost,send} from './api';
import {classificationProblem,classificationView,sameClassification} from './classification';
import {ClassificationFields,ClassificationSummary} from './ClassificationFields';
import {editablePost,postFingerprint as fingerprint} from './contentDocument';
import {contentPresentation} from './contentPresentation';
import {restaurantProblem} from './restaurantFields';
import {RichEditor} from './RichEditor';
import {date,messageOf} from './ui';
import './content-editor.css';

type Props={onGuard:GuardRegistration;canPublish:boolean;canDelete:boolean;onTrashed:(id:number)=>void;initial:PostDocument;catalog:ClassificationCatalog;categories:Category[];active:boolean;onList:()=>void;
  onSaved:(post:PostDocument)=>void;onMediaChange:()=>void};

export function ContentEditor({initial,catalog,categories,active,onList,onSaved,onMediaChange,onGuard,canPublish,canDelete,onTrashed}:Props) {
  const documentVisible=useDocumentVisible();
 const [historyOpen,setHistoryOpen]=useState(()=>historyRequested()),[historyBusy,setHistoryBusy]=useState(false);
 const [doc,setDoc]=useState(()=>editablePost(initial));
  const [saved,setSaved]=useState(()=>fingerprint(editablePost(initial)));
  const [busy,setBusy]=useState(false),[uploading,setUploading]=useState(false),[blocked,setBlocked]=useState(false);
  const [publishing,setPublishing]=useState(false);
  const [publishError,setPublishError]=useState('');
  const [previewOpen,setPreviewOpen]=useState(false);
  const [trashOpen,setTrashOpen]=useState(false),[trashing,setTrashing]=useState(false);
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
  async function save(automatic=false,publish=false) {
    if(trashOpen||historyOpen||historyBusy||publish&&!canPublish||automatic&&(!active||document.hidden))return;
    const current=live.current;
    if(inFlight.current||uploadPending.current||blocked||fatal)return;
    if(classificationProblem(current.classification,catalog,baseline.current)||restaurantProblem(current))return;
    if(!current.title.trim()){if(!automatic)setError(contentPresentation(current.classification.typeCode).missingTitle);return;}
    if(automatic&&(fingerprint(current)===savedRef.current||fingerprint(current)===failed.current))return;
    const submitted=version.current;inFlight.current=true;setBusy(true);setPublishing(publish);setError('');
    if(publish)setPublishError('');
    try {
      const result=editablePost(await (publish?publishPost(current):savePost(current,automatic?'AUTOSAVE':'MANUAL_DRAFT')));
      failed.current='';savedRef.current=fingerprint(result);setSaved(savedRef.current);
      baseline.current=result.classification;
      live.current=submitted===version.current?result:{...live.current,revision:result.revision,status:result.status,
        publishedRevision:result.publishedRevision,pending:result.pending,attachments:result.attachments,
        mediaIds:result.mediaIds,updatedAt:result.updatedAt};
      setDoc(live.current);onSaved(result);setAuthError(false);
      if(publish)setPublicationVersion(v=>v+1);
      const changed=submitted!==version.current;
      setMessage(publish
        ? '게시 완료 · '+savedTime()+(changed?' · 게시 중 입력한 변경사항은 초안으로 자동 저장됩니다.':'')
        : (automatic?'자동저장 완료 · ':'임시보관 완료 · 버전 저장 · ')+savedTime());
    } catch(e) {
      if(!publish)failed.current=fingerprint(current);
      if(publish)setPublishError(messageOf(e));else setError(messageOf(e));
      setMessage(publish?'게시를 완료하지 못했습니다. 게시 상태를 확인한 뒤 다시 시도하세요.':'저장되지 않음');
      if(e instanceof ApiError&&[401,403,404,409].includes(e.status)){setBlocked(true);setAuthError([401,403].includes(e.status));}
    } finally {inFlight.current=false;setBusy(false);setPublishing(false);}
  }
  useEffect(()=>{
    if(trashOpen||historyOpen||!active||!documentVisible||!dirty||busy||uploading||blocked||fatal||problem)return;
    const timer=setTimeout(()=>void save(true),1800);return()=>clearTimeout(timer);
  },[trashOpen,historyOpen,active,documentVisible,doc,dirty,busy,uploading,blocked,fatal,problem]);
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
      setBlocked(false);setFatal(false);setAuthError(false);setError('');setPublishError('');setMessage('저장된 내용을 다시 불러왔습니다.');onSaved(result);
    }catch(e){setError(messageOf(e));}finally{inFlight.current=false;setBusy(false);}
  }
  async function restored(_result:RestoreResult){
    const value=editablePost(await getPost(initial.id));version.current++;live.current=value;savedRef.current=fingerprint(value);
    setDoc(value);setSaved(savedRef.current);baseline.current=value.classification;failed.current='';setEpoch(n=>n+1);
    setBlocked(false);setFatal(false);setAuthError(false);setError('');setPublishError('');setMessage('새 초안으로 복구했습니다. 발행본은 유지됩니다.');onSaved(value);
  }
  function uploadState(value:boolean){uploadPending.current=value;setUploading(value);}
  async function trash() {
    if(!canDelete||inFlight.current||uploadPending.current||historyBusy||blocked||fingerprint(live.current)!==savedRef.current)return;
    inFlight.current=true;setBusy(true);setTrashing(true);setError('');let moved=false;
    try {await send(`/posts/${doc.id}/trash`,'POST',{revision:live.current.revision,confirmed:true});moved=true;}
    catch(e){setError(messageOf(e));if(e instanceof ApiError&&[401,403,404,409].includes(e.status)){setBlocked(true);setAuthError([401,403].includes(e.status));}}
    finally{inFlight.current=false;setBusy(false);setTrashing(false);setTrashOpen(false);}
    if(moved)onTrashed(doc.id);
  }
  const category=categories.find(c=>c.id===doc.categoryId)?.name||'미분류';

  const unpublished=doc.pending||doc.status==='PUBLISHED'&&dirty;
  return <section className={'content-editor writing-workspace'+(previewOpen?' with-preview':'')} data-testid="content-editor" data-content-id={doc.id}>
    <header className="writer-heading"><div><h1>{presentation.heading}</h1><p>{doc.authorName} · 최근 수정 {date(doc.updatedAt)}</p></div><button onClick={onList}>← 목록으로</button></header>
    <div className="writer-actionbar">
      <div className="writer-save-state"><span className={'writer-save-dot'+(error||publishError||blocked?' is-error':dirty||busy||uploading?' is-pending':'')} aria-hidden="true"/><span role="status">{publishing?'게시 중…':busy?'저장·조회 중…':uploading?'파일 업로드 중…':problem?'입력 확인 필요 · 저장 대기':!doc.title.trim()?presentation.title+' 입력 필요 · 저장 대기':message}</span></div>
      <div className="writer-actions"><button aria-expanded={previewOpen} aria-controls={'writer-preview-'+doc.id} onClick={()=>setPreviewOpen(value=>!value)}>{previewOpen?'미리보기 닫기':'미리보기'}</button><button className={canPublish?undefined:'primary'} disabled={busy||uploading||historyBusy||blocked||fatal||!!problem} onClick={()=>void save()} title="현재 내용을 저장하고 버전 이력에 남깁니다.">임시보관</button>{canPublish&&<button className="primary" disabled={busy||uploading||historyBusy||blocked||fatal||!!problem||!doc.title.trim()||doc.status==='PUBLISHED'&&!doc.pending&&!dirty} onClick={()=>void save(false,true)}>{publishing?'게시 중…':doc.status==='PUBLISHED'?'수정 내용 게시':'게시'}</button>}
        <details className="writer-more"><summary>더보기</summary><div className="writer-more-panel"><button disabled={busy||uploading||historyBusy} onClick={()=>{if(dirty)setError('초안을 저장한 뒤 버전 이력을 열어 주세요.');else setHistoryOpen(true);}}>버전 이력</button><button disabled={busy||uploading||historyBusy} onClick={()=>void reload()}>다시 조회</button>{canDelete&&<button className="danger-link" disabled={dirty||busy||uploading||historyBusy||blocked} title={dirty?'변경사항을 저장한 뒤 이동할 수 있습니다.':undefined} onClick={()=>{setError('');setTrashOpen(true);}}>휴지통으로 이동</button>}<a aria-disabled={dirty||busy||uploading||blocked} href={dirty||busy||uploading||blocked?undefined:'/admin/posts/'+doc.id+'/edit'} target="_blank" rel="noopener noreferrer">{canPublish?'공개 중단 등 상세 관리':'기존 편집 화면'} <span>기존 화면 ↗</span></a></div></details>
      </div>
    </div>
    <div className={'writer-publication-state'+(unpublished?' has-changes':'')}>
      <div><div className="writer-state-labels"><strong>{doc.status==='PUBLISHED'?'게시됨':doc.status==='PRIVATE'?'비공개':'임시보관'}</strong>{unpublished&&<span>미게시 수정 있음</span>}</div>
        <p>{doc.status==='PUBLISHED'?(unpublished?'현재 공개본은 유지됩니다. 수정한 내용은 게시 권한이 있는 관리자가 다시 게시해야 공개됩니다.':'현재 공개본과 저장된 작성 내용이 같습니다.'):doc.status==='PRIVATE'?'현재 비공개 상태입니다. 저장만으로 공개되지 않습니다.':'아직 게시되지 않은 콘텐츠입니다. 저장만으로 공개되지 않습니다.'}</p>
      </div>{doc.status==='PUBLISHED'&&doc.publishedRevision!==null&&<a href={'/admin/posts/'+doc.id+'/publication'} target="_blank" rel="noopener noreferrer">현재 공개본 보기 ↗</a>}
    </div>
    {(error||publishError)&&<div className="error-box" role="alert">{publishError&&<p>게시 실패: {publishError}</p>}{error}{authError&&<div><a href="/login" target="_blank" rel="noopener">새 탭에서 로그인</a><button onClick={()=>void bootstrap().then(()=>{setBlocked(false);setAuthError(false);setError('');}).catch(e=>setError(messageOf(e)))}>로그인 상태 다시 확인</button></div>}</div>}
    <div className="writer-layout"><div className="card content-canvas writer-canvas">
      <div className="writer-title-field"><label htmlFor={'content-title-'+doc.id}>{presentation.title}</label><input id={'content-title-'+doc.id} required maxLength={200} value={doc.title} onChange={e=>change({title:e.target.value})}/></div>

      <p className="content-field-label">{presentation.body}</p>
      <RichEditor key={epoch} document={doc.richContent} plain={doc.content} label={presentation.editorLabel} advanced focusedLayout
        writingTemplates={doc.classification.typeCode==='REVIEW'} active={active} templatesDisabled={busy||uploading||historyBusy||blocked||fatal}
        onChange={(content,richContent)=>change({content,richContent})} onUploadState={uploadState} onMediaChange={onMediaChange}
        onFatalError={message=>{setFatal(true);setError(message);}}/>
      <div className="content-foot"><span>사진·첨부 최대 12개 · 파일당 5MB</span><span>임시보관으로 현재 공개본이 바뀌지 않습니다.</span></div>
    </div><aside className="card writer-properties" aria-label="콘텐츠 속성"><h2>콘텐츠 속성</h2><p className="writer-properties-note">유형과 분류를 설정합니다.</p>
      <ClassificationFields value={doc.classification} catalog={catalog} baseline={baseline.current} problem={classificationIssue} onChange={patch=>change({classification:{...doc.classification,...patch}})}/>
      {addressIssue&&<div className="classification-warning" role="alert"><p>{addressIssue}</p>{doc.classification.typeCode!=='RESTAURANT'&&<button onClick={()=>change({restaurant:{address:''}})}>주소를 비우고 유형 변경</button>}</div>}
      <div className="writer-category"><label htmlFor={'content-category-'+doc.id}>기존 카테고리</label>
        <select id={'content-category-'+doc.id} value={doc.categoryId??''} onChange={e=>change({categoryId:e.target.value?Number(e.target.value):null})}>
          <option value="">미분류</option>{categories.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
        </select></div>
      {doc.classification.typeCode==='RESTAURANT'&&<label className="restaurant-address">주소 <small>선택 입력 · 최대 500자</small><input aria-label="주소" maxLength={500} value={doc.restaurant?.address||''} onChange={e=>change({restaurant:{address:e.target.value}})}/></label>}
      <p className="writer-save-help">입력 내용은 자동저장됩니다. ‘임시보관’을 누르면 버전 이력에도 남습니다.</p>
    </aside><aside id={'writer-preview-'+doc.id} hidden={!previewOpen} className="card preview-panel writer-preview" aria-label="작성 내용 미리보기"><header><div>작성 내용 미리보기</div>
      <div className="preview-toggle"><button aria-pressed={!mobile} onClick={()=>setMobile(false)}>PC</button><button aria-pressed={mobile} onClick={()=>setMobile(true)}>모바일</button></div></header>
      <p className="preview-caption">현재 입력한 내용의 미리보기입니다. 실제 홈페이지 연결 전입니다.</p>
      <details className="writer-comparison"><summary>작성본·공개본 분류 비교</summary><div className="classification-comparison">
        <section aria-label="초안 분류"><h3>{dirty?'작성 중 분류 · 미저장':'저장된 작성본 분류'}</h3><ClassificationSummary value={draftClassification}/></section>
        <section className="published-classification" aria-label="발행본 분류"><h3>현재 공개본 분류</h3>
          {publicationLoading?<p className="muted">확인 중…</p>:publicationError?<p className="error-box" role="alert">{publicationError}</p>:published?<>
            <ClassificationSummary value={published}/><p className="classification-difference">{sameClassification(draftClassification,published)?'분류가 공개본과 같습니다.':'공개본과 다른 분류입니다. 임시보관만으로 공개본은 바뀌지 않습니다.'}</p>
          </>:<p className="muted">현재 공개된 발행본이 없습니다.</p>}
          <button className="text-link" onClick={()=>setPublicationVersion(v=>v+1)}>공개본 분류 새로고침</button>
        </section>
        {(doc.classification.typeCode==='RESTAURANT'||publishedRestaurant)&&<section className="restaurant-comparison" aria-label="주소 초안과 발행본"><h3>주소</h3><p>현재 초안: {doc.classification.typeCode==='RESTAURANT'?(doc.restaurant?.address||'입력 없음'):'맛집 유형 아님'}</p><p>현재 발행본: {publicationLoading?'확인 중…':publicationError?'조회 실패':publishedRestaurant?(publishedRestaurant.address||'입력 없음'):'발행 주소 없음'}</p><small>주소는 다시 발행할 때 공개 내용에 반영됩니다.</small></section>}
      </div></details>
      {previewLoading&&<p className="preview-feedback" role="status">미리보기 갱신 중…</p>}{previewError&&<p className="error-box" role="alert">{previewError}</p>}
      <div className={'preview-scroll'+(mobile?' mobile':'')}><article className={'preview-paper'+(previewLoading||previewError?' preview-stale':'')} data-testid="content-preview">
        <p className="preview-category">{category}</p><h1>{preview?.title||presentation.title}</h1>{doc.classification.typeCode==='RESTAURANT'&&preview?.classification.typeCode==='RESTAURANT'&&<p className="preview-address">주소 · {preview.restaurant?.address||'입력 없음'}</p>}<div className="rich-content" dangerouslySetInnerHTML={{__html:preview?.bodyHtml||''}}/>
        {preview?.attachments.map(file=>file.mime.startsWith('image/')?<img key={file.id} src={'/admin/media/'+file.id+'/file'} alt={file.alt}/>:<a key={file.id} href={'/admin/media/'+file.id+'/file'} download>{file.name}</a>)}
      </article></div>
    </aside></div>
    {historyOpen&&<VersionHistoryDialog kind="posts" id={doc.id} active={active} onClose={()=>setHistoryOpen(false)} onRestored={restored} onBusy={setHistoryBusy}/>}
    {trashOpen&&<BlockDialog active={active} title="휴지통으로 이동" onClose={()=>{if(!inFlight.current)setTrashOpen(false);}}><p><strong>{doc.title}</strong></p><p>게시물이 목록과 공개 화면에서 사라집니다. 본문·첨부·분류·버전 이력은 보관하며 휴지통에서 임시보관으로 복원할 수 있습니다.</p><div className="dialog-actions"><button disabled={trashing} onClick={()=>setTrashOpen(false)}>취소</button><button className="danger" disabled={trashing} onClick={()=>void trash()}>{trashing?'이동 중…':'휴지통으로 이동'}</button></div></BlockDialog>}
  </section>;
}
