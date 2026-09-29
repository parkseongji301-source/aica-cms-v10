import {useCallback,useEffect,useRef,useState} from 'react';
import type {Bootstrap,Go,PostDocument,TrashList,TrashRow} from './types';
import type {EditorGuard} from './editorGuard';
import {useEditorGuard} from './editorGuard';
import {send} from './api';
import {BlockDialog} from './BlockDialog';
import {date,Empty,Feedback,Heading,messageOf,Pager,Status,useRemote} from './ui';

export function TrashPanel({active,version,data,go,search,onChanged,registerGuard}:{
  active:boolean;version:number;data:Bootstrap;go:Go;search:URLSearchParams;onChanged:(id:number)=>void;
  registerGuard:(path:string,guard:EditorGuard|null)=>void;
}) {
  const q=search.get('q')||'',page=Math.max(0,Number(search.get('page'))||0);
  const result=useRemote<TrashList>('/posts/trash?'+new URLSearchParams({q,page:String(page)}),active,version);
  const [term,setTerm]=useState(q),[selection,setSelection]=useState<{post:TrashRow;action:'restore'|'purge'}|null>(null);
  const [confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
  const [restoredId,setRestoredId]=useState<number|null>(null),inFlight=useRef(false);
  useEffect(()=>setTerm(q),[q]);
  const onGuard=useCallback((guard:EditorGuard|null)=>registerGuard('/trash',guard),[registerGuard]);
  useEditorGuard(false,busy,onGuard);
  const change=(nextPage:number,query=q)=>go('/trash?'+new URLSearchParams({q:query,page:String(nextPage)}));
  const choose=(post:TrashRow,action:'restore'|'purge')=>{setSelection({post,action});setConfirmed(false);setError('');setMessage('');setRestoredId(null);};
  const close=()=>{if(!inFlight.current){setSelection(null);setError('');}};
  async function execute() {
    if(!selection||inFlight.current||selection.action==='purge'&&!confirmed)return;
    const {post,action}=selection;inFlight.current=true;setBusy(true);setError('');
    try {
      await send<PostDocument|{id:number}>(`/posts/${post.id}/${action==='restore'?'restore':'trash'}`,action==='restore'?'POST':'DELETE',{revision:post.revision,confirmed:true});
      setSelection(null);setRestoredId(action==='restore'?post.id:null);
      setMessage(action==='restore'?`“${post.title}”을 임시보관으로 복원했습니다. 다시 게시하기 전에는 공개되지 않습니다.`:`“${post.title}”을 영구삭제했습니다.`);
      onChanged(post.id);
      if(page>0&&result.data?.items.length===1)change(page-1);else result.reload();
    }catch(e){setSelection(null);setError(messageOf(e)+' 목록을 확인한 뒤 다시 선택하세요.');result.reload();}
    finally{inFlight.current=false;setBusy(false);}
  }
  return <section><Heading title="휴지통" note="게시물을 복원하면 임시보관으로 돌아옵니다. 영구삭제 전까지 본문·첨부·분류·버전 이력을 보관합니다."/>
    <Feedback loading={result.loading} error={selection?result.error:error||result.error} message={message}/>
    {restoredId!==null&&<button onClick={()=>go(`/posts/${restoredId}/edit`)}>복원한 글 편집</button>}
    <section className="card"><form className="search-bar" onSubmit={e=>{e.preventDefault();if(!busy)change(0,term);}}>
      <input aria-label="휴지통 검색" maxLength={100} placeholder="제목·본문 검색" value={term} disabled={busy} onChange={e=>setTerm(e.target.value)}/>
      <button disabled={busy}>검색</button><button type="button" disabled={busy} onClick={result.reload}>새로고침</button>
    </form><div className="table-scroll"><table className="data-table"><thead><tr><th>제목</th><th>카테고리</th><th>보관 전 상태</th><th>작성자</th><th>삭제일</th><th>작업</th></tr></thead>
      <tbody>{result.data?.items.map(post=><tr key={post.id}><td>{post.title}</td><td>{data.categories.find(c=>c.id===post.categoryId)?.name||'미분류'}</td><td><Status value={post.status}/></td><td>{post.authorName}</td><td>{date(post.deletedAt)}</td>
        <td><div className="heading-actions"><button disabled={busy||result.loading||!!result.error} onClick={()=>choose(post,'restore')}>복원</button><button className="danger-link" disabled={busy||result.loading||!!result.error} onClick={()=>choose(post,'purge')}>영구삭제</button></div></td></tr>)}</tbody>
    </table>{!result.loading&&!result.error&&result.data?.items.length===0&&<Empty>{q?'검색 결과가 없습니다.':'휴지통이 비어 있습니다.'}</Empty>}</div>
    {result.data&&<Pager page={page} total={result.data.total} size={result.data.pageSize} onChange={p=>{if(!busy)change(p);}}/>}</section>
    {selection&&<BlockDialog active={active} title={selection.action==='restore'?'임시보관으로 복원':'게시물 영구삭제'} onClose={close}>
      <p><strong>{selection.post.title}</strong></p>
      <p>{selection.action==='restore'?'본문·첨부·분류와 버전 이력을 유지해 복원합니다. 다시 게시하기 전에는 공개되지 않습니다.':'본문·분류·첨부 연결·발행본·버전 이력을 영구삭제합니다. 복구할 수 없습니다. 원본 미디어 파일은 유지됩니다.'}</p>
      {selection.action==='purge'&&<label className="check-inline"><input type="checkbox" disabled={busy} checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>복구할 수 없음을 확인했습니다.</label>}
      <Feedback error={error}/><div className="dialog-actions"><button disabled={busy} onClick={close}>취소</button><button className={selection.action==='purge'?'danger':'primary'} disabled={busy||selection.action==='purge'&&!confirmed} onClick={()=>void execute()}>{busy?'처리 중…':selection.action==='restore'?'임시보관으로 복원':'영구삭제'}</button></div>
    </BlockDialog>}
  </section>;
}
