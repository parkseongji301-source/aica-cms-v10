import {useEffect,useState} from 'react';
import type {ClassificationCatalog,PostList,SelectedPost} from './types';
import {addManualPost,moveManualPost,removeManualPost} from './manualPosts';
import {topicLabel,toggleId} from './classification';
import {BlockDialog} from './BlockDialog';
import {Feedback,Pager,Status,useRemote} from './ui';

const labels:Record<SelectedPost['status'],string>={PUBLISHED:'발행',UNPUBLISHED:'미발행',PRIVATE:'비공개',DELETED:'삭제됨',UNAVAILABLE:'사용 불가'};
export function ManualPostsFields({ids,max,catalog,catalogError,onChange}:{ids:number[];max:number;catalog:ClassificationCatalog|null;catalogError:string;onChange:(ids:number[])=>void}) {
 const [open,setOpen]=useState(false),[version,setVersion]=useState(0);
 const states=useRemote<SelectedPost[]>('/pages/selected-posts?'+new URLSearchParams({ids:ids.join(',')}),ids.length>0,version);
 useEffect(()=>{const refresh=()=>setVersion(v=>v+1);window.addEventListener('focus',refresh);return()=>window.removeEventListener('focus',refresh);},[]);
 return <section className="manual-posts" aria-label="직접 선택 콘텐츠">
  <header><h3>선택된 콘텐츠 <small>{ids.length} / {max}</small></h3><button type="button" onClick={()=>setOpen(true)} disabled={ids.length>=max}>콘텐츠 추가</button></header>
  <p className="muted">선택한 순서대로 표시합니다. 미발행·비공개·삭제된 콘텐츠는 공개 결과에서 제외됩니다.</p>
  <button type="button" className="text-link" onClick={()=>{setVersion(v=>v+1);}}>상태 새로고침</button>
  {ids.length>0&&<Feedback {...states}/>}
  {ids.length===0?<p className="empty-state">선택한 콘텐츠가 없습니다.</p>:<ol className="manual-post-list">{ids.map((id,index)=>{
   const post=states.data?.find(p=>p.id===id);
   return <li key={id} data-manual-post-id={id}><div><span className="manual-position">{index+1}</span><strong>{post?.title||`콘텐츠 #${id}`}</strong><small className="manual-post-status">{states.error?'상태 확인 실패':states.loading?'상태 확인 중':post?labels[post.status]:'상태 확인 중'} · #{id}</small>{post?.status==='PUBLISHED'&&post.publicationTitle!==post.title&&<small>발행 제목: {post.publicationTitle}</small>}</div><div className="manual-row-actions"><button type="button" aria-label={`콘텐츠 #${id} 위로 이동`} disabled={index===0} onClick={()=>onChange(moveManualPost(ids,id,-1))}>↑</button><button type="button" aria-label={`콘텐츠 #${id} 아래로 이동`} disabled={index===ids.length-1} onClick={()=>onChange(moveManualPost(ids,id,1))}>↓</button><button type="button" aria-label={`콘텐츠 #${id} 선택 해제`} onClick={()=>onChange(removeManualPost(ids,id))}>선택 해제</button></div></li>;
  })}</ol>}
  {open&&<ManualPostPicker ids={ids} max={max} catalog={catalog} catalogError={catalogError} onAdd={id=>onChange(addManualPost(ids,id,max))} onClose={()=>setOpen(false)}/>}
 </section>;
}
function ManualPostPicker({ids,max,catalog,catalogError,onAdd,onClose}:{ids:number[];max:number;catalog:ClassificationCatalog|null;catalogError:string;onAdd:(id:number)=>void;onClose:()=>void}) {
 const [q,setQ]=useState(''),[search,setSearch]=useState(''),[type,setType]=useState(''),[cohorts,setCohorts]=useState<number[]>([]),[topics,setTopics]=useState<number[]>([]),[page,setPage]=useState(0);
 const params=new URLSearchParams({q:search,page:String(page)});if(type)params.set('typeCodes',type);if(cohorts.length)params.set('cohortIds',cohorts.join(','));if(topics.length)params.set('topicIds',topics.join(','));
 const result=useRemote<PostList>('/posts?'+params,true);
 return <BlockDialog title="콘텐츠 추가" onClose={onClose}>
  <div className="manual-picker"><p className="muted">검색은 초안 제목·본문·분류 기준입니다. 공개에는 발행본을 사용합니다.</p>
   <form className="search-bar" onSubmit={e=>{e.preventDefault();setSearch(q);setPage(0);}}><input aria-label="선택할 콘텐츠 검색" placeholder="제목·본문 검색" maxLength={100} value={q} onChange={e=>setQ(e.target.value)}/><button type="submit">검색</button></form>
   <Feedback error={catalogError} loading={!catalog&&!catalogError}/>
   {catalog&&<><label>유형 필터<select value={type} onChange={e=>{setType(e.target.value);setPage(0);}}><option value="">전체 유형</option>{catalog.types.map(t=><option key={t.code} value={t.code}>{t.name}</option>)}</select></label>
    <details><summary>기수·주제 필터{cohorts.length+topics.length>0?` (${cohorts.length+topics.length})`:''}</summary><fieldset><legend>기수 필터</legend>{catalog.cohorts.map(t=><label className="query-choice" key={t.id}><input type="checkbox" checked={cohorts.includes(t.id)} onChange={()=>{setCohorts(toggleId(cohorts,t.id));setPage(0);}}/>{t.name}</label>)}</fieldset><fieldset><legend>주제 필터</legend>{catalog.topics.map(t=><label className="query-choice" key={t.id}><input type="checkbox" checked={topics.includes(t.id)} onChange={()=>{setTopics(toggleId(topics,t.id));setPage(0);}}/>{topicLabel(t.id,catalog)}</label>)}</fieldset></details>
    <button type="button" className="text-link" onClick={()=>{setType('');setCohorts([]);setTopics([]);setQ('');setSearch('');setPage(0);}}>검색·필터 초기화</button>
   </>}
   <Feedback {...result}/>{result.data&&!result.loading&&!result.error&&<><ul className="manual-picker-results">{result.data.items.map(post=><li key={post.id}><div><strong>{post.title}</strong><small>{post.classification.typeName} · #{post.id}</small><Status value={post.status} pending={post.pending}/></div><button type="button" aria-label={`콘텐츠 #${post.id} 선택`} disabled={ids.includes(post.id)||ids.length>=max} onClick={()=>onAdd(post.id)}>{ids.includes(post.id)?'선택됨':'선택'}</button></li>)}</ul>{!result.data.items.length&&<p className="empty-state">검색 결과가 없습니다.</p>}<Pager page={page} total={result.data.total} size={result.data.pageSize} onChange={setPage}/></>}
   <footer><span>{ids.length} / {max}개 선택</span><button type="button" className="primary" onClick={onClose}>선택 완료</button></footer>
  </div>
 </BlockDialog>;
}
