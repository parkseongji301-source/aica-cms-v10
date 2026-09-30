import {useCallback,useRef,useState} from 'react';
import {BlockDialog} from './BlockDialog';
import {RichEditor} from './RichEditor';
import {WritingTemplatePreview} from './WritingTemplatePreview';
import {useEditorGuard,type EditorGuard} from './editorGuard';
import {ApiError} from './api';
import {date,Feedback,Heading,messageOf,useRemote} from './ui';
import {getWritingTemplate,saveWritingTemplate,deleteWritingTemplate,writingTemplateBase,type WritingTemplate,type WritingTemplateDocument} from './writingTemplateApi';

const emptyBody='{"ops":[{"insert":"\\n"}]}';
export function WritingTemplatesPanel({active,version,registerGuard}:{active:boolean;version:number;registerGuard:(path:string,guard:EditorGuard|null)=>void}) {
  const [revision,setRevision]=useState(0),[query,setQuery]=useState(''),[document,setDocument]=useState<WritingTemplateDocument|null>(null),[editing,setEditing]=useState(false);
  const [name,setName]=useState(''),[description,setDescription]=useState(''),[body,setBody]=useState(emptyBody),[plain,setPlain]=useState('');
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[dialogError,setDialogError]=useState(''),[discard,setDiscard]=useState(false),[uncertain,setUncertain]=useState(false);
  const [deleting,setDeleting]=useState<WritingTemplate|null>(null),[preview,setPreview]=useState<WritingTemplateDocument|null>(null);
  const pending=useRef(false),list=useRemote<WritingTemplate[]>(writingTemplateBase,active,version+revision);
  const dirty=editing&&(name!==(document?.info.name??'')||description!==(document?.info.description??'')||body!==(document?.richContent??emptyBody));
  const onGuard=useCallback((guard:EditorGuard|null)=>registerGuard('/design/writing-templates',guard),[registerGuard]);useEditorGuard(dirty,busy,onGuard);
  function close(){if(pending.current)return;if(dirty)setDiscard(true);else setEditing(false);}
  async function open(id:number|null,readOnly=false){
    if(pending.current)return;pending.current=true;setBusy(true);setError('');setDialogError('');
    try{const value=id===null?null:await getWritingTemplate(id);if(readOnly){setPreview(value);return;}
      setDocument(value);setName(value?.info.name??'');setDescription(value?.info.description??'');setBody(value?.richContent??emptyBody);setPlain(value?.content??'');setDiscard(false);setUncertain(false);setEditing(true);
    }catch(e){setError(messageOf(e));}finally{pending.current=false;setBusy(false);}
  }
  async function save(){
    if(pending.current||!name.trim()||!plain.trim()||uncertain)return;pending.current=true;setBusy(true);setDialogError('');
    try{await saveWritingTemplate(document?.info.id??null,{name,description,richContent:body,...(document?{revision:document.info.revision}:{})});setEditing(false);setRevision(n=>n+1);}
    catch(e){const unknown=!(e instanceof ApiError&&e.status>=400&&e.status<500);setUncertain(unknown);setDialogError(unknown?'저장 결과를 확인하지 못했습니다. 창을 닫고 목록을 새로고침하여 저장 여부를 먼저 확인해 주세요.':messageOf(e));}
    finally{pending.current=false;setBusy(false);}
  }
  async function remove(){
    if(!deleting||pending.current)return;pending.current=true;setBusy(true);setDialogError('');
    try{await deleteWritingTemplate(deleting.id,deleting.revision);setDeleting(null);setRevision(n=>n+1);}
    catch(e){setDialogError(messageOf(e));}finally{pending.current=false;setBusy(false);}
  }
  const filtered=list.data?.filter(t=>(t.name+' '+t.description).toLowerCase().includes(query.toLowerCase()));
  return <section className="writing-templates-workspace"><Heading title="글쓰기 템플릿" note="후기 작성자가 본문에 불러올 양식을 관리합니다. 추가·수정·삭제는 이 화면에서 진행합니다." actions={<button type="button" className="primary" disabled={busy} onClick={()=>void open(null)}>＋ 템플릿 추가</button>}/><Feedback error={error||list.error} loading={list.loading}/>
    <section className="card"><div className="search-bar"><input aria-label="글쓰기 템플릿 검색" placeholder="이름·설명 검색" value={query} onChange={e=>setQuery(e.target.value)}/><span>{filtered?.length??0}개</span><button type="button" disabled={busy} onClick={list.reload}>새로고침</button></div>
      {!list.loading&&!list.error&&<div className="table-scroll"><table className="data-table"><thead><tr><th>템플릿</th><th>적용 유형</th><th>최근 수정</th><th>작업</th></tr></thead><tbody>{filtered?.map(t=><tr key={t.id}><td><button type="button" className="text-link" disabled={busy} onClick={()=>void open(t.id,true)}>{t.name}</button><small className="row-meta">{t.description}</small></td><td><span className="scope-tag">후기</span></td><td>{date(t.updatedAt)}<small className="row-meta">{t.updaterName}</small></td><td><div className="page-row-actions"><button type="button" disabled={busy} onClick={()=>void open(t.id)}>수정</button><button type="button" className="text-link" disabled={busy} onClick={()=>{setDialogError('');setDeleting(t);}}>삭제</button></div></td></tr>)}</tbody></table>{!filtered?.length&&<p className="empty-state">{query?'검색 결과가 없습니다.':'등록된 글쓰기 템플릿이 없습니다. 새 양식을 추가해 주세요.'}</p>}</div>}
    </section><p className="muted page-list-note">템플릿을 수정하거나 삭제해도 이미 본문에 넣은 후기는 바뀌지 않습니다.</p>
    {preview&&<BlockDialog active={active} title="글쓰기 템플릿 미리보기" className="writing-template-dialog" onClose={()=>setPreview(null)}><WritingTemplatePreview value={preview}/><div className="dialog-actions"><button type="button" onClick={()=>setPreview(null)}>닫기</button></div></BlockDialog>}
    {editing&&<BlockDialog active={active} title={document?'글쓰기 템플릿 수정':'글쓰기 템플릿 추가'} className="writing-template-dialog" onClose={close}>
      {discard&&<div className="template-confirm" role="alert"><p>저장하지 않은 변경을 버리고 닫을까요?</p><button type="button" onClick={()=>setDiscard(false)}>계속 수정</button><button type="button" onClick={()=>{setEditing(false);setDiscard(false);}}>변경 버리고 닫기</button></div>}
      <fieldset className="writing-template-fields" disabled={busy||uncertain}><label>템플릿 이름<input maxLength={150} required value={name} onChange={e=>setName(e.target.value)} placeholder="예: 프로젝트 후기"/></label><label>설명<textarea maxLength={1000} value={description} onChange={e=>setDescription(e.target.value)} placeholder="언제 사용하면 좋은 양식인지 알려 주세요."/></label><p className="muted">적용 유형: 후기 · 본문 양식과 서식을 저장합니다. 사진·첨부는 실제 후기 작성 시 넣습니다.</p>
      <RichEditor document={document?.richContent??null} plain={document?.content??''} label="템플릿 본문" disabled={busy||uncertain} onChange={(text,rich)=>{setPlain(text);setBody(rich);}}/>
      </fieldset><Feedback error={dialogError}/><div className="dialog-actions"><button type="button" disabled={busy} onClick={close}>취소</button><button type="button" className="primary" disabled={busy||uncertain||!name.trim()||!plain.trim()} onClick={()=>void save()}>{busy?'저장 중…':'템플릿 저장'}</button></div>
    </BlockDialog>}
    {deleting&&<BlockDialog active={active} title="글쓰기 템플릿 삭제" onClose={()=>{if(!pending.current)setDeleting(null);}}><p><strong>{deleting.name}</strong>을 삭제할까요?</p><p>앞으로 불러올 수 없게 됩니다. 이미 작성한 후기 본문은 유지됩니다.</p><Feedback error={dialogError}/><div className="dialog-actions"><button type="button" disabled={busy} onClick={()=>setDeleting(null)}>취소</button><button type="button" className="danger" disabled={busy} onClick={()=>void remove()}>{busy?'삭제 중…':'템플릿 삭제'}</button></div></BlockDialog>}
  </section>;
}
