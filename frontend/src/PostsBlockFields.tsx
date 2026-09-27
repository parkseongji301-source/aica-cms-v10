import {useEffect,useState} from 'react';
import {getClassifications} from './api';
import {ManualPostsFields} from './ManualPostsFields';
import {postsSourcePatch} from './manualPosts';
import {allowedTopicIds,toggleId,topicLabel} from './classification';
import type {Category,ClassificationCatalog,ComponentDefinition,PostsQuery,PreviewDocument,Section} from './types';

export function PostsResult({result}:{result:Pick<PreviewDocument['sections'][number],'posts'|'total'>}) {
 return <div className="posts-query-results"><p>발행 콘텐츠 {result.total}건 · {result.posts.length}개 표시</p>{result.posts.length?<ol>{result.posts.map(post=><li key={post.id} data-post-id={post.id}><strong>{post.title}</strong><time>{post.publishedAt.slice(0,10)}</time></li>)}</ol>:<p className="empty-state">조건에 맞는 발행 콘텐츠가 없습니다.</p>}</div>;
}
export function PostsBlockFields({section,definition,categories,onChange,onContent,result,loading,error}:{section:Section;definition:ComponentDefinition;categories:Category[];onChange:(patch:Partial<Section>)=>void;onContent?:(categoryId:number|null)=>void;result?:PreviewDocument['sections'][number];loading:boolean;error:string}) {
 const [catalog,setCatalog]=useState<ClassificationCatalog|null>(null),[catalogError,setCatalogError]=useState('');
 useEffect(()=>{const abort=new AbortController();void getClassifications(abort.signal).then(setCatalog).catch(e=>{if(e.name!=='AbortError')setCatalogError(e.message);});return()=>abort.abort();},[]);
 const capability=definition.postsQuery;
 const mode=section.sourceMode??'category';
 const query=section.query??{typeCode:'GENERAL',cohortIds:[],topicIds:[],sort:'LATEST',limit:capability?.defaultLimit??6};
 const patch=(change:Partial<PostsQuery>)=>onChange({query:{...query,...change}});
 const allowed=catalog?allowedTopicIds(catalog,query.typeCode):[];
 const invalid=query.topicIds.filter(id=>!allowed.includes(id));
 return <div className="posts-query-fields">
  <label>콘텐츠 소스<select value={mode} onChange={e=>onChange(postsSourcePatch(section,e.target.value as 'category'|'query'|'manual',query))}><option value="category">기존 카테고리</option>{capability&&<><option value="query">조건으로 불러오기</option><option value="manual">직접 선택</option></>}</select></label>
  {mode==='category'?<label>표시할 콘텐츠<select value={section.categoryId??''} onChange={e=>onChange({categoryId:e.target.value?Number(e.target.value):null})}><option value="">전체 분류</option>{categories.map(c=><option value={c.id} key={c.id}>{c.name}</option>)}</select><small>발행된 콘텐츠를 최신순으로 6개 표시합니다.</small></label>:mode==='manual'?<ManualPostsFields ids={section.manual?.postIds??[]} max={capability?.maxManualItems??20} catalog={catalog} catalogError={catalogError} onChange={postIds=>onChange({manual:{postIds}})}/>:<>
   {catalogError&&<p role="alert">분류를 불러오지 못했습니다. {catalogError}</p>}
   {!catalog&&!catalogError&&<p role="status">분류 불러오는 중…</p>}
   {catalog&&<>
    <label>콘텐츠 유형<select value={query.typeCode} onChange={e=>patch({typeCode:e.target.value})}>{catalog.types.map(t=><option key={t.code} value={t.code}>{t.name}{t.active?'':' (비활성)'}</option>)}</select></label>
    <fieldset><legend>기수 <small>미선택 시 전체 · 복수 선택 가능</small></legend>{catalog.cohorts.map(t=><label className="query-choice" key={t.id}><input type="checkbox" checked={query.cohortIds.includes(t.id)} onChange={()=>patch({cohortIds:toggleId(query.cohortIds,t.id)})}/>{t.name}{t.active?'':' (비활성)'}</label>)}</fieldset>
    <fieldset><legend>주제 <small>미선택 시 전체 · 복수 선택 가능</small></legend>{catalog.topics.filter(t=>allowed.includes(t.id)).map(t=><label className="query-choice" key={t.id}><input type="checkbox" checked={query.topicIds.includes(t.id)} onChange={()=>patch({topicIds:toggleId(query.topicIds,t.id)})}/>{t.name}{t.active?'':' (비활성)'}</label>)}{allowed.length===0&&<small>이 유형에 등록된 주제가 없습니다.</small>}</fieldset>
    {invalid.length>0&&<div className="error-box" role="alert">유형에 맞지 않는 주제가 남아 있습니다. 직접 해제하거나 유형을 되돌려 주세요.{invalid.map(id=><label className="query-choice" key={id}><input type="checkbox" checked onChange={()=>patch({topicIds:toggleId(query.topicIds,id)})}/>{topicLabel(id,catalog)}</label>)}</div>}
    <div className="two-fields"><label>정렬<select value={query.sort} onChange={()=>patch({sort:'LATEST'})}><option value="LATEST">최신순</option></select></label><label>표시 개수<select value={query.limit} onChange={e=>patch({limit:Number(e.target.value)})}>{Array.from({length:capability?.maxLimit??20},(_,i)=>i+1).map(n=><option key={n} value={n}>{n}개</option>)}</select></label></div>
   </>}
  </>}
  {mode==='category'&&onContent&&<button type="button" onClick={()=>onContent(section.categoryId)}>연결된 콘텐츠 보기 →</button>}
  <section className="query-preview" aria-label="콘텐츠 조건 미리보기"><h3>{mode==='manual'?'선택 순서 미리보기':'조건 미리보기'}</h3><small>{mode==='manual'?'선택 순서 중 공개 가능한 발행본만 표시합니다.':'현재 블록 조건 × 콘텐츠 발행본'}</small>{!section.visible?<p>숨김 블록입니다. 표시를 켜면 결과를 확인할 수 있습니다.</p>:loading?<p role="status">발행 콘텐츠 조회 중…</p>:error?<p role="alert">{error}</p>:result?<PostsResult result={result}/>:null}</section>
 </div>;
}
