import {useEffect,useRef,useState} from 'react';
import type Quill from 'quill';
import {BlockDialog} from './BlockDialog';
import {Feedback,messageOf,useRemote} from './ui';
import {getWritingTemplate,prepareWritingTemplate,writingTemplateBase,type WritingTemplate,type WritingTemplateDocument} from './writingTemplateApi';
import {composeWritingTemplate,isEmptyWritingDocument,type TemplateMode} from './writingTemplateDocument';
import {WritingTemplatePreview} from './WritingTemplatePreview';

export function PostTemplateTool({editor,active,disabled}:{editor:Quill|null;active:boolean;disabled:boolean}) {
  const [open,setOpen]=useState(false),[selected,setSelected]=useState(''),[value,setValue]=useState<WritingTemplateDocument|null>(null);
  const [loading,setLoading]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[mode,setMode]=useState<TemplateMode>('append'),[confirm,setConfirm]=useState(false),[notice,setNotice]=useState('');
  const [existing,setExisting]=useState(false),[query,setQuery]=useState('');
  const inFlight=useRef(false),allowed=useRef(false);allowed.current=active&&!disabled;
  const list=useRemote<WritingTemplate[]>(writingTemplateBase,open&&active);
  useEffect(()=>{if(!active)setOpen(false);},[active]);
  useEffect(()=>{setValue(null);setConfirm(false);setError('');if(!open||!selected)return;const c=new AbortController();setLoading(true);
    void getWritingTemplate(Number(selected),c.signal).then(v=>{if(!c.signal.aborted)setValue(v);}).catch(e=>{if(!c.signal.aborted)setError(messageOf(e));}).finally(()=>{if(!c.signal.aborted)setLoading(false);});return()=>c.abort();
  },[selected,open]);
  function close(){if(inFlight.current)return;setOpen(false);editor?.focus();}
  function show(){if(!editor||disabled)return;setSelected('');setValue(null);setMode('append');setConfirm(false);setError('');setNotice('');setQuery('');setExisting(!isEmptyWritingDocument(JSON.stringify(editor.getContents())));setOpen(true);}
  async function apply(){
    if(!editor||!value||inFlight.current||!allowed.current)return;
    if(mode==='replace'&&existing&&!confirm){setConfirm(true);return;}
    const expected=JSON.stringify(editor.getContents());inFlight.current=true;setBusy(true);setError('');
    try{
      const fresh=await prepareWritingTemplate(value.info.id,value.info.revision);
      if(!allowed.current)throw new Error('저장·업로드가 끝난 뒤 다시 적용해 주세요.');
      if(JSON.stringify(editor.getContents())!==expected)throw new Error('작성 중인 본문이 변경되었습니다. 현재 내용을 확인한 뒤 다시 적용해 주세요.');
      const composed=composeWritingTemplate(expected,fresh.richContent,mode);
      editor.history.cutoff();editor.setContents(composed.ops,'user');editor.history.cutoff();
      setOpen(false);setNotice('템플릿을 본문에 넣었습니다. 대괄호와 작성 팁을 실제 경험으로 바꿔 주세요. 실행 취소로 되돌릴 수 있습니다.');editor.focus();
    }catch(e){setError(messageOf(e));setConfirm(false);}finally{inFlight.current=false;setBusy(false);}
  }
  return <><button type="button" disabled={disabled||!editor} onClick={show}>템플릿</button>{notice&&<span className="template-insert-notice" role="status">{notice}</span>}
    {open&&active&&<BlockDialog active={active} title="글쓰기 템플릿 불러오기" className="writing-template-dialog" onClose={close}>
      <p className="muted">후기 본문에 사용할 양식을 선택하세요.</p><Feedback error={list.error} loading={list.loading}/>{list.error&&<button type="button" onClick={list.reload}>다시 불러오기</button>}
      <div className="writing-template-picker"><div><input aria-label="글쓰기 템플릿 검색" placeholder="이름·설명 검색" value={query} onChange={e=>setQuery(e.target.value)} disabled={busy}/><div className="writing-template-options" role="group" aria-label="글쓰기 템플릿 선택">{list.data?.filter(t=>(t.name+' '+t.description).includes(query)).map(t=><button type="button" key={t.id} aria-pressed={selected===String(t.id)} disabled={busy} onClick={()=>setSelected(String(t.id))}><strong>{t.name}</strong><span>{t.description}</span></button>)}</div>{!list.loading&&!list.error&&list.data&&!list.data.length&&<p className="empty-state">등록된 글쓰기 템플릿이 없습니다. 관리자에게 등록을 요청해 주세요.</p>}{!!list.data?.length&&!list.data.some(t=>(t.name+' '+t.description).includes(query))&&<p className="empty-state">검색 결과가 없습니다.</p>}</div><div><Feedback error={error} loading={loading}/>{value&&!loading?<WritingTemplatePreview value={value}/>:!loading&&<p className="empty-state">템플릿을 선택하면 미리 볼 수 있습니다.</p>}</div></div>
      {value&&existing&&<label className="template-mode">본문에 넣는 방식<select disabled={busy} value={mode} onChange={e=>{setMode(e.target.value as TemplateMode);setConfirm(false);}}><option value="append">기존 본문 뒤에 추가</option><option value="replace">본문 전체 교체</option></select></label>}
      {confirm&&<div className="template-confirm" role="alert"><strong>현재 본문을 템플릿으로 교체할까요?</strong><p>작성 중인 본문 전체가 바뀝니다. 제목과 분류는 유지됩니다.</p></div>}
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={close}>취소</button><button type="button" className="primary" disabled={!value||loading||busy||disabled} onClick={()=>void apply()}>{busy?'불러오는 중…':mode==='replace'&&existing?(confirm?'확인하고 본문 교체':'본문 교체 확인'):'본문에 넣기'}</button></div>
    </BlockDialog>}
  </>;
}
