import {useEffect,useState} from 'react';
import {BlockDialog} from './BlockDialog';
import {getPublicationView} from './api';
import {messageOf} from './ui';
import type {PostPreview} from './types';

/** Read-only view of the current public copy; saved but unpublished edits are never shown here. */
export function PublicationViewDialog({id,active,onClose}:{id:number;active:boolean;onClose:()=>void}) {
 const [view,setView]=useState<PostPreview|null>(null),[error,setError]=useState('');
 useEffect(()=>{
  const controller=new AbortController();setView(null);setError('');
  void getPublicationView(id,controller.signal).then(value=>{if(!controller.signal.aborted)setView(value);}).catch(e=>{if(!controller.signal.aborted)setError(messageOf(e));});
  return()=>controller.abort();
 },[id]);
 return <BlockDialog active={active} title="현재 공개본" className="publication-view-dialog" onClose={onClose}>
  <p className="muted">방문자에게 공개된 내용입니다. 저장만 하고 다시 게시하지 않은 수정은 포함되지 않습니다.</p>
  {!view&&!error&&<p role="status">공개본을 불러오는 중…</p>}{error&&<p className="error-box" role="alert">{error}</p>}
  {view&&<article className="preview-paper" data-testid="publication-view"><h1>{view.title}</h1>
   {view.restaurant&&<p className="preview-address">주소 · {view.restaurant.address||'입력 없음'}</p>}
   <div className="rich-content" dangerouslySetInnerHTML={{__html:view.bodyHtml}}/>
   {view.attachments.map(file=>file.mime.startsWith('image/')?<img key={file.id} src={'/admin/media/'+file.id+'/file'} alt={file.alt}/>:<a key={file.id} href={'/admin/media/'+file.id+'/file'} download>{file.name}</a>)}
  </article>}
  <div className="dialog-actions"><button type="button" autoFocus onClick={onClose}>닫기</button></div>
 </BlockDialog>;
}
