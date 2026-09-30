import {useDocumentVisible} from './editorGuard';
import {savedTime} from './ui';
import {VersionHistoryDialog} from './VersionHistoryDialog';
import {historyRequested,type RestoreResult} from './versionHistory';
import type {EditorGuard,GuardRegistration} from './editorGuard';
import {useCallback,useEffect,useRef,useState} from 'react';
import type {Bootstrap,PageDocument,PreviewDocument,Section,ImageFile,ComponentDefinition,PageTarget,ViewMode} from './types';
import {ApiError,bootstrap,getPage,previewPage,publishPage,savePage,unpublishPage,uploadImage} from './api';
import {RichEditor} from './RichEditor';
import {newSection,changeSection,moveSection,insertDuplicate,removeSection} from './pageBlocks';
import {addressableBlock,selectedBlockId,pageOutline} from './blockNavigation';
import {pagePath} from './navigation';
import {PageTemplateDialog} from './PageTemplates';
import {applyTemplateBlocks} from './templateBlocks';
import type {TemplateMode} from './templateBlocks';
import {BlockDialog} from './BlockDialog';
import {PostsBlockFields,PostsResult} from './PostsBlockFields';
import './page-editor.css';
import '../../src/main/resources/static/css/page-blocks.css';
const fingerprint=(d:PageDocument)=>JSON.stringify({title:d.title,sections:d.sections});

export function PageEditor({initial,definitions,categories,images:initialImages,onTitle,onContent,active=true,blockId,viewMode,onSelectBlock,onOutline,onGuard,templateUse=false,templateManage=false}:{templateUse?:boolean;templateManage?:boolean;initial:PageDocument;definitions:ComponentDefinition[];categories:Bootstrap['categories'];images:ImageFile[];onTitle:(title:string)=>void;onContent?:(categoryId:number|null)=>void;active?:boolean;blockId:string|null;viewMode:ViewMode;onSelectBlock:(id:string|null,replace?:boolean)=>void;onOutline:(outline:PageTarget)=>void;onGuard:GuardRegistration}) {
 const [historyOpen,setHistoryOpen]=useState(()=>historyRequested()),[historyBusy,setHistoryBusy]=useState(false);
 const historyBusyRef=useRef(false);historyBusyRef.current=historyBusy;
 const dialogGuard=useRef<EditorGuard|null>(null);
 const templateGuard=useCallback((guard:EditorGuard|null)=>{dialogGuard.current=guard;},[]);
 const documentVisible=useDocumentVisible();
 const [doc,setDoc]=useState(initial),[saved,setSaved]=useState(fingerprint(initial)),[busy,setBusy]=useState(false),[uploading,setUploading]=useState(false),[message,setMessage]=useState('저장된 내용을 불러왔습니다.'),[error,setError]=useState(''),[blocked,setBlocked]=useState(false),[authError,setAuthError]=useState(false);
 const [preview,setPreview]=useState<PreviewDocument|null>(null),[previewError,setPreviewError]=useState(''),[previewLoading,setPreviewLoading]=useState(true),[mobile,setMobile]=useState(false),[images,setImages]=useState(initialImages),[epoch,setEpoch]=useState(0);
 const [templateDialog,setTemplateDialog]=useState<'load'|'save'|null>(null);
 const [previewOpen,setPreviewOpen]=useState(false);
 const [adding,setAdding]=useState(false),[removing,setRemoving]=useState<string|null>(null);
 const selectedId=selectedBlockId(doc.sections,blockId),missingTarget=blockId!==null&&selectedId===null;
 const identityIssue=doc.sections.some(s=>!addressableBlock(doc.sections,s.id));
 const inspectorRef=useRef<HTMLDivElement>(null);
 const selection=selectedId===null?undefined:doc.sections.find(s=>s.id===selectedId),definition=definitions.find(d=>d.type===selection?.type),selectedIndex=doc.sections.findIndex(s=>s.id===selectedId);
 const definitionFor=(type:Section['type'])=>definitions.find(d=>d.type===type);
 const previewRef=useRef<HTMLElement>(null);
 useEffect(()=>{onOutline(pageOutline(doc,definitions));},[doc.sections,doc.title,definitions,onOutline]);
 useEffect(()=>{if(active&&blockId===null&&selectedId)onSelectBlock(selectedId,true);},[active,blockId,selectedId]);
 useEffect(()=>{if(!active)return;const frame=requestAnimationFrame(()=>{inspectorRef.current?.scrollIntoView({block:'start'});inspectorRef.current?.focus({preventScroll:true});});return()=>cancelAnimationFrame(frame);},[active,selectedId,blockId]);
 const live=useRef(doc);live.current=doc;const inFlight=useRef(false);const stamp=useRef(0);const savedRef=useRef(saved);savedRef.current=saved;const previewVersion=useRef(0);const pendingUpload=useRef(0);
 useEffect(()=>{setImages(initialImages);},[initialImages]);
 useEffect(()=>{onGuard(()=>{if(inFlight.current||pendingUpload.current||historyBusyRef.current)return 'busy';const modal=dialogGuard.current?.()??true;return modal===true?fingerprint(live.current)===savedRef.current:modal;});return()=>onGuard(null);},[onGuard]);
 const failed=useRef('');
 const dirty=fingerprint(doc)!==saved;
 const change=(next:PageDocument)=>{stamp.current++;live.current=next;setDoc(next);if(!blocked)setError('');setMessage('변경사항 있음');};
 const sectionChange=(id:string,patch:Partial<Section>)=>change({...live.current,sections:changeSection(live.current.sections,id,patch)});
 const [unpublishOpen,setUnpublishOpen]=useState(false);
 // Withdrawal hides the public copy only; the saved draft and history stay (PageService.unpublish).
 async function unpublish() {
  if(inFlight.current||pendingUpload.current||blocked||fingerprint(live.current)!==savedRef.current)return;
  inFlight.current=true;setBusy(true);setError('');
  try {const result=await unpublishPage(live.current.id,live.current.revision);stamp.current++;live.current=result;savedRef.current=fingerprint(result);setDoc(result);setSaved(savedRef.current);setMessage('공개를 중단했습니다 · 작성 내용과 이력은 유지됩니다');setAuthError(false);}
  catch(e){const failure=e as ApiError;setError(failure.message);if([401,403,409].includes(failure.status)){setBlocked(true);setAuthError(failure.status!==409);}}
  finally{inFlight.current=false;setBusy(false);setUnpublishOpen(false);}
 }
 async function save(automatic=false,publish=false) {
    if(historyOpen||automatic&&(!active||document.hidden)||automatic&&publish)return;
  if(live.current.sections.some(s=>!addressableBlock(live.current.sections,s.id))||inFlight.current||pendingUpload.current||blocked||automatic&&fingerprint(live.current)===failed.current)return;
  if(!live.current.title.trim()){if(!automatic)setError('페이지 제목을 입력하세요.');return;}
  if(automatic&&fingerprint(live.current)===savedRef.current)return;
  const snapshot=live.current,submitted=stamp.current;inFlight.current=true;setBusy(true);setError('');
  try {const result=await (publish?publishPage(snapshot):savePage(snapshot,automatic?'AUTOSAVE':'MANUAL_DRAFT'));failed.current='';setSaved(fingerprint(result));
   live.current=submitted===stamp.current?result:{...live.current,revision:result.revision,status:result.status,publishedRevision:result.publishedRevision,pending:result.pending};setDoc(live.current);
   onTitle(result.title);setMessage((publish?'게시 완료 · 공개본에 반영 · ':automatic?'자동저장 완료 · ':'임시보관 완료 · 버전 저장 · ')+savedTime());setAuthError(false);
  }catch(e){failed.current=fingerprint(snapshot);const failure=e as ApiError;setError(failure.message);setMessage('저장되지 않음');if([401,403,409].includes(failure.status)){setBlocked(true);setAuthError(failure.status!==409);}}
  finally{inFlight.current=false;setBusy(false);}
 }
 useEffect(()=>{if(historyOpen||!active||!documentVisible||!dirty||busy||uploading||blocked)return;const timer=setTimeout(()=>void save(true),1800);return()=>clearTimeout(timer);},[historyOpen,active,documentVisible,doc,dirty,busy,uploading,blocked]);
 useEffect(()=>{const listener=(e:BeforeUnloadEvent)=>{if(fingerprint(live.current)!==savedRef.current||inFlight.current||pendingUpload.current||historyBusyRef.current){e.preventDefault();e.returnValue='';}};window.addEventListener('beforeunload',listener);return()=>window.removeEventListener('beforeunload',listener);},[]);
 useEffect(()=>{const listener=(e:KeyboardEvent)=>{if(active&&(e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='s'){e.preventDefault();void save();}};document.addEventListener('keydown',listener);return()=>document.removeEventListener('keydown',listener);});
 useEffect(()=>{const controller=new AbortController(),version=++previewVersion.current;setPreviewLoading(true);setPreviewError('');
  const timer=setTimeout(()=>previewPage(doc,controller.signal).then(result=>{if(version===previewVersion.current){setPreview(result);setPreviewLoading(false);}}).catch(e=>{if(e.name!=='AbortError'&&version===previewVersion.current){setPreviewError(e.message);setPreviewLoading(false);}}),250);
  return()=>{clearTimeout(timer);controller.abort();};},[doc.title,doc.sections]);
 async function reload(){
  if(inFlight.current||pendingUpload.current)return;
  if(dirty&&!window.confirm('저장되지 않은 입력을 버리고 저장된 내용을 다시 조회할까요?'))return;
  const requested=stamp.current;inFlight.current=true;setBusy(true);
  try{
   const result=await getPage(initial.id);
   if(requested!==stamp.current){setMessage('다시 조회 중 입력이 변경되어 현재 내용을 유지했습니다.');return;}
   live.current=result;setDoc(result);setSaved(fingerprint(result));failed.current='';setEpoch(e=>e+1);setBlocked(false);setAuthError(false);setError('');setMessage('저장된 내용을 다시 불러왔습니다.');onTitle(result.title);
  }catch(e){setError((e as Error).message);}finally{inFlight.current=false;setBusy(false);}
 }
 async function restored(result:RestoreResult){
  const value=await getPage(initial.id);stamp.current++;live.current=value;savedRef.current=fingerprint(value);setDoc(value);setSaved(savedRef.current);
  failed.current='';setEpoch(n=>n+1);setBlocked(false);setAuthError(false);setError('');onTitle(value.title);
  if(blockId&&result.blockIds[blockId])onSelectBlock(result.blockIds[blockId],true);
  setMessage('새 초안으로 복구했습니다. 발행본과 페이지 주소는 유지됩니다.');
 }
 async function imageUpload(id:string,file?:File){if(!file)return;if(file.size>5*1024*1024){setError('파일은 5MB 이하로 선택하세요.');return;}pendingUpload.current++;setUploading(true);try{const image=await uploadImage(file);setImages(current=>[...current,image]);if(live.current.sections.some(s=>s.id===id))sectionChange(id,{imageId:image.id});}catch(e){setError((e as Error).message);}finally{pendingUpload.current--;setUploading(pendingUpload.current>0);}}
 function move(index:number,offset:number){change({...live.current,sections:moveSection(live.current.sections,index,offset)});}
 function add(definition:ComponentDefinition){if(live.current.sections.length>=30)return;const section=newSection(definition.type,definition);change({...live.current,sections:[...live.current.sections,section]});onSelectBlock(section.id);setAdding(false);}
 function duplicate(){if(!selectedId||live.current.sections.length>=30)return;const next=insertDuplicate(live.current.sections,selectedId);change({...live.current,sections:next.sections});onSelectBlock(next.selectedId);}
 function remove(){if(!removing)return;const next=removeSection(live.current.sections,removing);change({...live.current,sections:next.sections});setRemoving(null);}
 function applyTemplate(copies:Section[],mode:TemplateMode,expected:string){
  if(JSON.stringify(live.current.sections)!==expected||inFlight.current||pendingUpload.current||blocked)throw new Error('페이지 내용 또는 저장 상태가 바뀌었습니다. 다시 확인한 뒤 적용하세요.');
  const next=applyTemplateBlocks(live.current.sections,copies,mode);change({...live.current,sections:next});if(copies[0])onSelectBlock(copies[0].id);else if(mode==='replace')onSelectBlock(null,true);setMessage('템플릿을 초안에 적용했습니다. 발행본은 유지됩니다.');
 }
 const controlsDisabled=busy||uploading||blocked||identityIssue;
 return <section className={'page-editor block-editor pages-editor-workspace'+(previewOpen?' with-page-preview':'')} data-testid="page-editor" data-page-id={doc.id}>
  <div className="editor-heading"><div><p className="eyebrow">페이지 편집</p><h1>{doc.title.trim()||initial.title}</h1><p className="target-caption">블록을 선택해 이 페이지의 내용과 배치를 편집하세요.</p></div></div>
  <div className="editor-actions"><div><span className="status-tag">{doc.status==='PUBLISHED'?'게시됨':doc.status==='PRIVATE'?'비공개':'임시보관'}</span>{(doc.pending||doc.status==='PUBLISHED'&&dirty)&&<span className="pending-tag">미게시 수정 있음</span>}<span className="save-message" role="status">{busy?'저장 중…':uploading?'이미지 업로드 중…':message}</span></div><div className="action-buttons"><button disabled={busy||uploading} onClick={()=>{if(dirty)setError('임시보관한 뒤 버전 이력을 열어 주세요.');else setHistoryOpen(true);}}>버전 이력</button><button type="button" className="secondary" disabled={busy||uploading} onClick={()=>void reload()}>다시 불러오기</button><button type="button" aria-expanded={previewOpen} aria-controls={'page-preview-'+doc.id} onClick={()=>setPreviewOpen(value=>!value)}>{previewOpen?'미리보기 닫기':'미리보기'}</button><button type="button" className="primary" disabled={controlsDisabled} onClick={()=>void save()}>임시보관</button>{doc.status==='PUBLISHED'&&<button type="button" className="secondary" disabled={controlsDisabled||dirty} title={dirty?'변경사항을 저장한 뒤 공개를 중단할 수 있습니다.':undefined} onClick={()=>{setError('');setUnpublishOpen(true);}}>공개 중단</button>}<button type="button" className="primary" disabled={controlsDisabled||!doc.title.trim()||doc.status==='PUBLISHED'&&!doc.pending&&!dirty} onClick={()=>void save(false,true)}>{doc.status==='PUBLISHED'?'수정 내용 게시':'게시'}</button></div></div>
  <p className="page-save-explainer">입력은 자동저장됩니다. 임시보관을 누르면 버전 이력에도 남습니다. 게시를 누르면 현재 내용을 저장하고 공개본에 반영합니다. 페이지 주소는 바뀌지 않습니다.</p>
  {unpublishOpen&&<BlockDialog active={active} title="페이지 공개 중단" onClose={()=>{if(!inFlight.current)setUnpublishOpen(false);}}><p><strong>{doc.title}</strong></p><p>방문자에게 보이는 공개본을 내리고 비공개로 바꿉니다. 작성 내용과 버전 이력은 그대로 남으며 다시 게시할 수 있습니다.</p><div className="dialog-actions"><button type="button" disabled={busy} onClick={()=>setUnpublishOpen(false)}>취소</button><button type="button" className="danger" disabled={busy} onClick={()=>void unpublish()}>{busy?'공개 중단 중…':'공개 중단'}</button></div></BlockDialog>}
  {error&&<div className="error-box" role="alert">{error}{authError&&<div><a href="/login" target="_blank" rel="noopener">새 탭에서 로그인</a><button type="button" onClick={()=>void bootstrap().then(()=>{setBlocked(false);setAuthError(false);setError('');}).catch(e=>setError(e.message))}>로그인 상태 다시 확인</button></div>}</div>}
  <section className="card title-card"><label htmlFor={'page-title-'+initial.id}>페이지 제목</label><input id={'page-title-'+initial.id} value={doc.title} maxLength={200} required onChange={e=>change({...live.current,title:e.target.value})}/></section>
  {(templateUse||templateManage)&&<div className="template-actions">{templateUse&&<button type="button" disabled={controlsDisabled} onClick={()=>setTemplateDialog('load')}>템플릿 불러오기</button>}{templateManage&&<button type="button" disabled={controlsDisabled} onClick={()=>setTemplateDialog('save')}>현재 구성을 템플릿으로 저장</button>}</div>}
  <div className="block-layout">
   <nav className="card block-navigator" aria-label="페이지 블록 목록"><header><div><h2>블록 <span>{doc.sections.length}</span></h2><small>화면에 표시되는 순서</small></div><button type="button" disabled={doc.sections.length>=30||controlsDisabled} onClick={()=>setAdding(true)}>＋ 블록 추가</button></header>
    {doc.sections.length===0?<p className="empty-state">첫 블록을 추가하세요.</p>:<ol>{doc.sections.map((section,index)=><li key={section.id||`unlinked-${index}`}><button type="button" data-block-id={section.id} aria-current={selectedId===section.id?'true':undefined} aria-label={(index+1)+'. '+(section.heading||definitionFor(section.type)?.label||section.type)+' · '+section.type+(section.visible?' · 표시':' · 숨김')} disabled={!addressableBlock(doc.sections,section.id)} onClick={()=>onSelectBlock(section.id)}><span className="block-number">{String(index+1).padStart(2,'0')}</span><span className="block-item-copy"><strong>{section.heading||definitionFor(section.type)?.label||section.type}</strong><small>{definitionFor(section.type)?.label||section.type} · {definitionFor(section.type)?.variations.find(v=>v.value===section.variation)?.label||section.variation}</small></span><span className={'block-visibility'+(section.visible?'':' is-hidden')}>{section.visible?'표시':'숨김'}</span></button></li>)}</ol>}
   </nav>
   <div className="block-inspector" ref={inspectorRef} tabIndex={-1} data-testid="block-inspector">
    {identityIssue&&<p className="error-box" role="alert">블록 ID가 없거나 중복되어 저장할 수 없습니다. 기존 데이터 변환 상태를 확인하세요.</p>}
    {missingTarget&&<p className="error-box" role="status">연결된 블록을 찾을 수 없습니다. 현재 페이지의 블록 목록에서 직접 선택하세요.</p>}
    {selection&&definition?<section className="card section-card" key={epoch+'-'+selection.id} data-block-id={selection.id} aria-label="선택한 블록 편집"><header><div><p className="eyebrow">{selectedIndex+1}번째 블록 · 설정</p><h2>{definition.label}</h2><a className="block-permalink" href={'/admin-next'+pagePath(doc.id,selection.id)+'&view='+viewMode} target="_blank" rel="noopener noreferrer">현재 블록 링크 ↗</a>{!selection.visible&&<span className="block-hidden-note">숨김 블록</span>}</div><label className="block-visible-label"><input type="checkbox" checked={selection.visible} onChange={e=>sectionChange(selection.id,{visible:e.target.checked})}/>표시</label></header>
     <div className="block-operations"><button type="button" aria-label="블록 위로 이동" disabled={selectedIndex<=0||controlsDisabled} onClick={()=>move(selectedIndex,-1)}>↑ 위로</button><button type="button" aria-label="블록 아래로 이동" disabled={selectedIndex===doc.sections.length-1||controlsDisabled} onClick={()=>move(selectedIndex,1)}>↓ 아래로</button><button type="button" disabled={doc.sections.length>=30||controlsDisabled} onClick={duplicate}>복제</button><button type="button" className="danger" disabled={controlsDisabled} onClick={()=>setRemoving(selection.id)}>삭제</button></div>
     <div className="section-fields">
      <label>블록 배치<select aria-label="블록 배치" value={selection.variation} onChange={e=>sectionChange(selection.id,{variation:e.target.value})}>{definition.variations.map(v=><option key={v.value} value={v.value}>{v.label}</option>)}</select><small>{definition.variations.find(v=>v.value===selection.variation)?.description}</small></label>
      {definition.fields.includes('heading')&&<label>블록 제목<input value={selection.heading} maxLength={200} onChange={e=>sectionChange(selection.id,{heading:e.target.value})}/></label>}
      {definition.fields.includes('body')&&<RichEditor document={selection.bodyDoc} plain={selection.body} label="블록 본문" onChange={(body,bodyDoc)=>sectionChange(selection.id,{body,bodyDoc})} onFatalError={message=>{setError(message);setBlocked(true);}}/>}
      {definition.fields.includes('imageId')&&<div className="image-fields"><label>이미지<select value={selection.imageId??''} onChange={e=>sectionChange(selection.id,{imageId:e.target.value?Number(e.target.value):null})}><option value="">선택하세요</option>{images.map(image=><option key={image.id} value={image.id}>{image.name}</option>)}</select></label><label className="upload-field">이미지 업로드<input type="file" accept=".jpg,.jpeg,.png" disabled={uploading} onChange={e=>{void imageUpload(selection.id,e.target.files?.[0]);e.target.value='';}}/></label>{selection.imageId&&<img className="selected-image" src={'/admin/media/'+selection.imageId+'/file'} alt={selection.heading}/>}</div>}
      {definition.fields.includes('categoryId')&&selection.type!=='POSTS'&&<><label>표시할 콘텐츠<select value={selection.categoryId??''} onChange={e=>sectionChange(selection.id,{categoryId:e.target.value?Number(e.target.value):null})}><option value="">전체 분류</option>{categories.map(c=><option value={c.id} key={c.id}>{c.name}</option>)}</select></label>{onContent&&<button type="button" onClick={()=>onContent(selection.categoryId)}>연결된 콘텐츠 보기 →</button>}</>}
      {selection.type==='POSTS'&&<PostsBlockFields section={selection} definition={definition} categories={categories} onContent={onContent} onChange={patch=>sectionChange(selection.id,patch)} result={previewError||previewLoading?undefined:preview?.sections.find(s=>s.id===selection.id)} loading={previewLoading} error={previewError}/> }
      {definition.fields.includes('link')&&<div className="two-fields"><label>버튼 이름<input value={selection.label} maxLength={80} onChange={e=>sectionChange(selection.id,{label:e.target.value})}/></label><label>연결 주소<input value={selection.link} maxLength={1000} onChange={e=>sectionChange(selection.id,{link:e.target.value})}/></label></div>}
     </div></section>:<div className="card empty-state">{selection?'지원하지 않는 블록입니다. 저장하지 말고 등록 정의를 확인하세요.':'편집할 블록을 선택하거나 추가하세요.'}</div>}
   </div>
   <aside hidden={!previewOpen} id={'page-preview-'+doc.id} ref={previewRef} tabIndex={-1} className="card preview-panel" aria-label="페이지 미리보기"><header><div><span className="live-dot"/>작성 내용 미리보기</div><div className="preview-toggle"><button type="button" aria-pressed={!mobile} onClick={()=>setMobile(false)}>PC</button><button type="button" aria-pressed={mobile} onClick={()=>setMobile(true)}>모바일</button></div></header><p className="preview-caption">페이지는 작성 중인 내용, 연결된 글 목록은 현재 공개본을 보여줍니다.</p>{previewLoading&&<p className="preview-feedback" role="status">미리보기 갱신 중…</p>}{previewError&&<p className="error-box" role="alert">미리보기 갱신 실패 · {previewError}</p>}
    <div className={'preview-scroll'+(mobile?' mobile':'')}><article className={'preview-paper'+(previewLoading||previewError?' preview-stale':'')} data-testid="page-preview" onClick={e=>{if((e.target as HTMLElement).closest('a'))e.preventDefault();}}><h1>{preview?.title||'페이지 제목'}</h1>{preview?.sections.map(section=><section className={'preview-section type-'+section.type.toLowerCase()+' variation-'+section.variation} key={section.id} data-block-id={section.id} data-selected={section.id===selectedId?'true':undefined}><h2>{section.heading}</h2><div className="rich-content" dangerouslySetInnerHTML={{__html:section.bodyHtml}}/>{section.imageId&&<img src={'/admin/media/'+section.imageId+'/file'} alt={section.heading}/>} {section.type==='POSTS'&&<PostsResult result={section}/>}{section.label&&<span className="preview-button">{section.label} ↗</span>}</section>)}</article></div>
   </aside>
  </div>
  {historyOpen&&<VersionHistoryDialog kind="pages" id={doc.id} active={active} onClose={()=>setHistoryOpen(false)} onRestored={restored} onBusy={setHistoryBusy}/>}
  {templateDialog&&<PageTemplateDialog onGuard={templateGuard} active={active} categories={categories} kind={templateDialog} sections={doc.sections} onClose={()=>setTemplateDialog(null)} onApply={applyTemplate}/>}
  {adding&&<BlockDialog title="블록 추가" onClose={()=>setAdding(false)}><p className="muted">추가할 블록을 선택하세요.</p><div className="component-picker">{definitions.map(d=><button type="button" key={d.type} onClick={()=>add(d)}><strong>{d.label}</strong><small>{d.description}</small></button>)}</div></BlockDialog>}
  {removing&&<BlockDialog title="블록 삭제" onClose={()=>setRemoving(null)}><p><strong>{doc.sections.find(s=>s.id===removing)?.heading||'선택한 블록'}</strong>을 초안에서 삭제할까요?</p><p className="muted">발행본은 다시 발행할 때 변경됩니다. 원본 이미지·파일은 유지됩니다.</p><div className="dialog-actions"><button type="button" autoFocus onClick={()=>setRemoving(null)}>취소</button><button type="button" className="danger" disabled={controlsDisabled} onClick={remove}>블록 삭제</button></div></BlockDialog>}
 </section>;
}
