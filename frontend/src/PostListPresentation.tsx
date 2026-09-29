import {useId,useRef,useState} from 'react';
import type {Bootstrap,ClassificationCatalog,Go,PostRow} from './types';
import type {ContentContext} from './contentNavigation';
import {contextualPostPath,filterValues} from './contentNavigation';
import {topicLabel} from './classification';
import {postFilterTopics} from './postListFilters';
import {date,LegacyLink} from './ui';

export const postStatusLabels:Record<string,string>={DRAFT:'임시보관',PUBLISHED:'게시됨',PRIVATE:'비공개'};

export function ContentListTable({items,categories,go,scope,filtered,onReset}:{items:PostRow[];categories:Bootstrap['categories'];go:Go;scope:ContentContext|null;filtered:boolean;onReset:()=>void}) {
  if(!items.length)return <div className="posts-empty"><strong>{filtered?'조건에 맞는 콘텐츠가 없습니다.':'아직 등록된 콘텐츠가 없습니다.'}</strong><p>{filtered?'검색어나 분류 조건을 바꿔 다시 찾아보세요.':'새 콘텐츠를 작성해 첫 이야기를 시작하세요.'}</p>{filtered&&<button onClick={onReset}>검색 조건 초기화</button>}</div>;
  return <div className="posts-table-wrap"><table className="data-table posts-table"><caption className="posts-sr-only">콘텐츠 목록 · 분류는 저장된 작성본 기준</caption>
    <thead><tr><th scope="col">{scope?.type==='FAQ'?'질문':scope?.type==='RESTAURANT'?'식당명':'제목'}</th><th scope="col">상태</th><th scope="col">유형</th><th scope="col">기수·주제</th><th scope="col">작성자</th><th scope="col">최근 수정</th><th scope="col">작업</th></tr></thead>
    <tbody>{items.map(post=>{
      const category=categories.find(c=>c.id===post.categoryId)?.name||'미분류';
      const cohorts=post.classification.cohorts.map(t=>t.name).join(', ')||'선택 없음';
      const topics=post.classification.topics.map(t=>t.name).join(', ')||'선택 없음';
      return <tr key={post.id} data-content-id={post.id}>
        <td className="post-title-cell"><button className="text-link content-title" data-content-id={post.id} onClick={()=>go(contextualPostPath(post.id,scope))}>{post.title}</button><small className="post-category">기존 카테고리 · {category}</small>
          <details className="post-mobile-details"><summary>분류·작성자 보기</summary><dl><dt>유형</dt><dd>{post.classification.typeName}</dd><dt>기수</dt><dd>{cohorts}</dd><dt>주제</dt><dd>{topics}</dd><dt>작성자</dt><dd>{post.authorName}</dd><dt>기존 카테고리</dt><dd>{category}</dd></dl></details>
        </td>
        <td className="post-state-cell"><span className={'post-status state-'+post.status.toLowerCase()}>{postStatusLabels[post.status]||post.status}</span>{post.pending&&<small className="post-pending">미게시 수정 있음</small>}</td>
        <td className="post-type-cell">{post.classification.typeName}</td>
        <td className="post-classification-cell"><span><span className="post-meta-label">기수</span>{cohorts}</span><span><span className="post-meta-label">주제</span>{topics}</span></td>
        <td className="post-author-cell">{post.authorName}</td>
        <td className="post-date-cell"><span className="post-mobile-label">최근 수정 </span><time dateTime={post.updatedAt}>{date(post.updatedAt)}</time></td>
        <td className="post-action-cell"><button aria-label={post.title+' 편집'} onClick={()=>go(contextualPostPath(post.id,scope))}>편집</button></td>
      </tr>;
    })}</tbody></table></div>;
}

type FilterProps={params:URLSearchParams;scope:ContentContext|null;catalog:ClassificationCatalog|null;categories:Bootstrap['categories'];canManageCategories:boolean;change:(values:Record<string,string>)=>void;selectTypes:(types:string[])=>void;toggle:(key:string,value:string)=>void};

export function PostListFilters({params,scope,catalog,categories,canManageCategories,change,selectTypes,toggle}:FilterProps) {
  const [open,setOpen]=useState<string|null>(null),id=useId(),root=useRef<HTMLDivElement>(null);
  const values=(key:string)=>filterValues(params,key),types=values('typeCodes'),cohorts=values('cohortIds'),topics=values('topicIds');
  const availableTopics=catalog?postFilterTopics(catalog,types):[];
  const category=params.get('categoryId')||'';
  const groups=[{key:'type',label:'유형',count:types.length},{key:'cohort',label:'기수',count:cohorts.length},{key:'topic',label:'주제',count:topics.length},{key:'category',label:'기존 카테고리',count:category?1:0}];
  const resetClassification=()=>change({typeCodes:scope?.type||'',cohortIds:'',topicIds:scope?.topicId?String(scope.topicId):''});
  return <div className="posts-filters" ref={root} onKeyDown={event=>{if(event.key==='Escape'&&open){root.current?.querySelector<HTMLButtonElement>('button[aria-expanded="true"]')?.focus();setOpen(null);}}}>
    <div className="posts-filter-buttons" aria-label="분류 필터">{groups.map(group=><button key={group.key} type="button" id={id+'-'+group.key+'-trigger'} aria-expanded={open===group.key} aria-controls={id+'-'+group.key} onClick={()=>setOpen(open===group.key?null:group.key)}>{group.label}{group.count>0&&<span className="filter-count">{group.count}</span>}<span aria-hidden="true">{open===group.key?'⌃':'⌄'}</span></button>)}</div>
    {groups.map(group=><section key={group.key} id={id+'-'+group.key} hidden={open!==group.key} className="posts-filter-panel" aria-labelledby={id+'-'+group.key+'-trigger'}>
      <div className="posts-filter-panel-heading"><strong>{group.label}</strong><span>{group.key==='category'?'하나 선택':'복수 선택 가능'}</span><button type="button" aria-label={group.label+' 필터 닫기'} onClick={()=>{root.current?.querySelector<HTMLButtonElement>('button[aria-expanded="true"]')?.focus();setOpen(null);}}>닫기</button></div>
      {group.key!=='category'&&!catalog&&<p className="muted">분류 정보를 불러오지 못했습니다. 목록 위의 안내를 확인해 주세요.</p>}
      {group.key==='type'&&catalog&&<><div className="posts-filter-options"><button type="button" aria-pressed={!types.length} disabled={!!scope} onClick={()=>selectTypes([])}>모든 유형</button>{catalog.types.map(type=><label key={type.code}><input type="checkbox" disabled={!!scope} checked={types.includes(type.code)} onChange={()=>selectTypes(types.includes(type.code)?types.filter(value=>value!==type.code):[...types,type.code])}/>{type.name}{!type.active?' · 비활성':''}</label>)}</div>{scope&&<p className="posts-filter-note">선택한 메뉴의 유형입니다. 다른 유형은 전체 콘텐츠에서 찾을 수 있습니다.</p>}</>}
      {group.key==='cohort'&&catalog&&<div className="posts-filter-options">{catalog.cohorts.map(cohort=><label key={cohort.id}><input type="checkbox" checked={cohorts.includes(String(cohort.id))} onChange={()=>toggle('cohortIds',String(cohort.id))}/>{cohort.name}{!cohort.active?' · 비활성':''}</label>)}{!catalog.cohorts.length&&<p className="muted">등록된 기수가 없습니다.</p>}</div>}
      {group.key==='topic'&&catalog&&<><div className="posts-filter-options">{availableTopics.map(topic=><label key={topic.id}><input type="checkbox" checked={topics.includes(String(topic.id))} disabled={scope?.topicId!=null} onChange={()=>toggle('topicIds',String(topic.id))}/>{topicLabel(topic.id,catalog)}{!topic.active?' · 비활성':''}</label>)}{!availableTopics.length&&<p className="muted">선택한 유형에는 등록된 주제가 없습니다.</p>}</div>{scope?.topicId!=null&&<p className="posts-filter-note">선택한 메뉴의 주제로 고정되어 있습니다.</p>}{topics.some(value=>!availableTopics.some(topic=>String(topic.id)===value))&&<p className="posts-filter-note">현재 유형에 없는 주제 조건이 남아 있습니다. 아래 적용 조건에서 해제할 수 있습니다.</p>}</>}
      {group.key==='category'&&<div className="posts-category-filter"><label>기존 카테고리<select value={category} onChange={event=>change({categoryId:event.target.value})}><option value="">전체 카테고리</option>{category&&!categories.some(item=>String(item.id)===category)&&<option value={category}>확인할 수 없는 카테고리</option>}{categories.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></label>{canManageCategories&&<LegacyLink href="/admin/categories">기존 카테고리 관리</LegacyLink>}</div>}
      {group.key!=='category'&&<footer className="posts-filter-panel-footer"><p>같은 항목에서는 하나 이상, 다른 항목끼리는 모두 일치하는 글을 찾습니다.</p><button type="button" onClick={resetClassification}>분류 초기화</button></footer>}
    </section>)}
  </div>;
}

export function AppliedPostFilters({params,scope,catalog,categories,change,selectTypes,toggle,onReset}:FilterProps&{onReset:()=>void}) {
  const chips:{key:string;label:string;locked?:boolean;remove:()=>void}[]=[];
  const q=params.get('q'),status=params.get('status'),category=params.get('categoryId');
  if(q)chips.push({key:'q',label:'검색: '+q,remove:()=>change({q:''})});
  if(status)chips.push({key:'status',label:postStatusLabels[status]||'확인할 수 없는 상태',remove:()=>change({status:''})});
  if(category)chips.push({key:'category',label:'카테고리: '+(categories.find(item=>String(item.id)===category)?.name||'확인할 수 없음'),remove:()=>change({categoryId:''})});
  const types=filterValues(params,'typeCodes');
  types.forEach(value=>chips.push({key:'type-'+value,label:catalog?.types.find(item=>item.code===value)?.name||'확인할 수 없는 유형',locked:!!scope,remove:()=>selectTypes(types.filter(type=>type!==value))}));
  filterValues(params,'cohortIds').forEach(value=>chips.push({key:'cohort-'+value,label:catalog?.cohorts.find(item=>String(item.id)===value)?.name||'확인할 수 없는 기수',remove:()=>toggle('cohortIds',value)}));
  filterValues(params,'topicIds').forEach(value=>chips.push({key:'topic-'+value,label:catalog?.topics.some(item=>String(item.id)===value)?topicLabel(Number(value),catalog):'확인할 수 없는 주제',locked:scope?.topicId!=null,remove:()=>toggle('topicIds',value)}));
  if(!chips.length)return null;
  return <div className="posts-applied" aria-label="적용 중인 검색 조건"><span className="posts-applied-label">적용 조건</span>{chips.map(chip=>chip.locked?<span key={chip.key} className="posts-filter-chip is-fixed">{chip.label}<small>고정</small></span>:<button key={chip.key} className="posts-filter-chip" aria-label={chip.label+' 필터 해제'} onClick={chip.remove}>{chip.label}<span aria-hidden="true">×</span></button>)}{chips.some(chip=>!chip.locked)&&<button className="posts-reset" onClick={onReset}>전체 초기화</button>}</div>;
}
