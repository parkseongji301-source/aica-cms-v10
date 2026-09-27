import {useEffect,useState} from 'react';
import type {AccountRow,ActivityList,Bootstrap,Dashboard,Go,PostList,PostRow,RoleRow,ClassificationCatalog} from './types';
import {topicLabel} from './classification';
import {allowedTopicIds} from './classification';
import {contextualPostPath,filterValues,contentContext,draftSelection,scopedPostParams,contentSections} from './contentNavigation';
import type {ContentContext} from './contentNavigation';
import {CreateContentDraft} from './CreateContentDraft';
import './classification.css';
import {contentPath,pagePath} from './navigation';
import {date,Empty,Feedback,Heading,LegacyLink,Pager,Status,useRemote} from './ui';

type Props={active:boolean;version:number;data:Bootstrap;go:Go;search:URLSearchParams};
function PostTable({items,categories,go,scope=null}:{items:PostRow[];categories:Bootstrap['categories'];go:Go;scope?:ContentContext|null}) {
  return <div className="table-scroll"><table className="data-table"><thead><tr><th>{scope?.type==='FAQ'?'질문':scope?.type==='RESTAURANT'?'식당명':'콘텐츠'}</th><th>초안 분류</th><th>기존 카테고리</th><th>상태</th><th>작성자</th><th>최근 수정</th></tr></thead><tbody>{items.map(p=><tr key={p.id} data-content-id={p.id}><td><button className="text-link content-title" data-content-id={p.id} onClick={()=>go(contextualPostPath(p.id,scope))}>{p.title}</button></td><td className="classification-cell"><strong>{p.classification.typeName}</strong><small>기수: {p.classification.cohorts.map(t=>t.name).join(', ')||'없음'}</small><small>주제: {p.classification.topics.map(t=>t.name).join(', ')||'없음'}</small></td><td>{categories.find(c=>c.id===p.categoryId)?.name||'미분류'}</td><td><Status value={p.status} pending={p.pending}/></td><td>{p.authorName}</td><td>{date(p.updatedAt)}</td></tr>)}</tbody></table>{items.length===0&&<Empty/>}</div>;
}
export function DashboardPanel({active,version,data,go}:Props) {
  const result=useRemote<Dashboard>('/dashboard',active,version),d=result.data;
  return <section><Heading title="대시보드" note={data.permissions.site?'사이트 콘텐츠 현황':'내 콘텐츠 현황'} actions={<button onClick={result.reload}>새로고침</button>}/><Feedback {...result}/>{d&&<>
    <div className="metric-grid">{[['전체 콘텐츠',d.total],['오늘 작성',d.todayCount],['최근 7일 작성',d.weekCount]].map(([label,value])=><article className="card metric" key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>
    <div className="overview-grid"><section className="card panel-pad"><h2>최근 7일 콘텐츠 등록</h2><div className="daily-chart">{d.days.map(day=><div key={day.day}><span>{day.day.slice(5)}</span><progress max={Math.max(1,...d.days.map(v=>v.count))} value={day.count}/><strong>{day.count}</strong></div>)}</div></section><section className="card panel-pad"><span className="scope-tag">연동 전</span><h2>방문자 통계</h2><p className="muted">실제 홈페이지의 방문 데이터가 연결되지 않았습니다.</p><p className="muted">실측 통계는 연동 후 표시합니다.</p></section></div>
    <section className="card"><header className="panel-header"><h2>최근 콘텐츠</h2><button className="text-link" onClick={()=>go('/posts')}>전체 보기 →</button></header><PostTable items={d.recentPosts} categories={data.categories} go={go}/></section>
  </>}</section>;
}
export function PostsPanel({active,version,data,go,search}:Props) {
  const q=search.get('q')||'',status=search.get('status')||'',category=search.get('categoryId')||'',page=Math.max(0,Number(search.get('page')||0)||0);
  const [term,setTerm]=useState(q);useEffect(()=>setTerm(q),[q]);
  const catalog=useRemote<ClassificationCatalog>('/classifications',active,version);
  const scope=contentContext(search,catalog.data),section=scope?contentSections[scope.type]:null;
  const params=scopedPostParams(search,scope);
  const multi=(key:string)=>filterValues(params,key);
  const result=useRemote<PostList>('/posts?'+params,active&&(!scope||!scope.error),version);
  const [creating,setCreating]=useState(false);
  const change=(values:Record<string,string>)=>{const p=new URLSearchParams(params);Object.entries({page:'0',...values}).forEach(([k,v])=>v?p.set(k,v):p.delete(k));if(scope)p.set(scope.param,scope.key);go('/posts?'+p);};
  const toggle=(key:string,value:string)=>change({[key]:(multi(key).includes(value)?multi(key).filter(v=>v!==value):[...multi(key),value]).join(',')});
  const categoryName=data.categories.find(c=>String(c.id)===category)?.name;
  const listTitle=scope?(scope.key==='all'?section!.label:`${section!.label} · ${scope.label}`):categoryName?`콘텐츠 목록 · ${categoryName}`:'콘텐츠 목록';
  return <section><Heading title={listTitle} note={scope?`${section!.parent} / ${section!.label} · 초안 기준`:'기존 콘텐츠를 선택해 편집합니다. 새 콘텐츠 작성·발행은 기존 관리자에서 처리합니다.'} actions={scope?<button className="primary" disabled={!!scope.error||!!catalog.error||catalog.loading} onClick={()=>setCreating(true)}>＋ 새 {scope.type==='RESTAURANT'?'맛집':section!.label}</button>:<LegacyLink href={'/admin/posts/new'+(category?'?categoryId='+category:'')}>＋ 새 콘텐츠</LegacyLink>}/>
    {creating&&scope&&!scope.error&&<CreateContentDraft selection={draftSelection(scope)} catalog={catalog.data!} onClose={()=>setCreating(false)} onCreated={post=>{setCreating(false);result.reload();go(contextualPostPath(post.id,scope));}}/>}
    {scope?.error&&<Feedback error={catalog.error||scope.error}/>}
    <section className="card"><div className="filter-bar"><div className="status-tabs">{[['','전체'],['DRAFT','임시저장'],['PUBLISHED','발행'],['PRIVATE','비공개']].map(([value,label])=><button key={value} aria-pressed={status===value} onClick={()=>change({status:value})}>{label}</button>)}</div><button className="text-link" onClick={result.reload}>새로고침</button></div>
    <form className="search-bar" onSubmit={e=>{e.preventDefault();change({q:term});}}><select aria-label="기존 카테고리 필터" value={category} onChange={e=>change({categoryId:e.target.value})}><option value="">전체 카테고리</option>{data.categories.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select><input aria-label="콘텐츠 검색" maxLength={100} value={term} onChange={e=>setTerm(e.target.value)} placeholder={scope?.type==='FAQ'?'질문·답변 검색':scope?.type==='RESTAURANT'?'식당명·소개 검색':'제목·본문 검색'}/><button>검색</button>{data.permissions.structure&&<LegacyLink href="/admin/categories">기존 카테고리 관리</LegacyLink>}</form>
    <div className="classification-filters"><div className="filter-caption"><p>분류 필터는 초안 기준 · 항목 간 AND, 같은 항목 내 OR</p><button onClick={()=>change({typeCodes:scope?scope.type:'',cohortIds:'',topicIds:scope?.topicId?String(scope.topicId):''})}>분류 필터 초기화</button></div>
      <Feedback {...catalog}/>{catalog.data&&<>
        <fieldset><legend>유형 필터</legend><div className="classification-options">{catalog.data.types.map(t=><label key={t.code}><input type="checkbox" checked={multi('typeCodes').includes(t.code)} disabled={!!scope} onChange={()=>toggle('typeCodes',t.code)}/>{t.name}{!t.active?' · 비활성':''}</label>)}</div></fieldset>
        <fieldset><legend>기수 필터</legend><div className="classification-options">{catalog.data.cohorts.map(t=><label key={t.id}><input type="checkbox" checked={multi('cohortIds').includes(String(t.id))} onChange={()=>toggle('cohortIds',String(t.id))}/>{t.name}{!t.active?' · 비활성':''}</label>)}{!catalog.data.cohorts.length&&<span className="muted">등록된 기수 없음</span>}</div></fieldset>
        {scope?.type!=='RESTAURANT'&&<fieldset><legend>주제 필터</legend><div className="classification-options">{catalog.data.topics.filter(t=>!scope||allowedTopicIds(catalog.data!,scope.type).includes(t.id)).map(t=><label key={t.id}><input type="checkbox" checked={multi('topicIds').includes(String(t.id))} disabled={scope?.topicId!=null} onChange={()=>toggle('topicIds',String(t.id))}/>{topicLabel(t.id,catalog.data!)}{!t.active?' · 비활성':''}</label>)}{!catalog.data.topics.length&&<span className="muted">등록된 주제 없음</span>}</div></fieldset>}
      </>}
    </div>
    <Feedback {...result}/>{result.data&&!result.loading&&!result.error&&!scope?.error&&<><PostTable items={result.data.items} categories={data.categories} go={go} scope={scope}/><Pager page={page} total={result.data.total} size={result.data.pageSize} onChange={p=>change({page:String(p)})}/></>}</section>
  </section>;
}
export function PagesPanel({data,go}:Props) {
  const [q,setQ]=useState(''),[status,setStatus]=useState('');const items=data.pages.filter(p=>p.title.toLowerCase().includes(q.toLowerCase())&&(!status||p.status===status));
  return <section><Heading title="전체 페이지 현황" note="사이트 구조와 같은 페이지 원본을 편집합니다." actions={<LegacyLink href="/admin/pages">{data.permissions.structure?'발행·페이지 추가 등 관리':'기존 페이지 발행 관리'}</LegacyLink>}/><section className="card"><div className="search-bar"><input aria-label="페이지 검색" value={q} onChange={e=>setQ(e.target.value)} placeholder="페이지 이름 검색"/><select aria-label="페이지 상태" value={status} onChange={e=>setStatus(e.target.value)}><option value="">전체 상태</option><option value="DRAFT">임시저장</option><option value="PUBLISHED">발행</option><option value="PRIVATE">비공개</option></select><span>{items.length}개</span></div><div className="table-scroll"><table className="data-table"><thead><tr><th>페이지</th><th>상태</th><th>최근 수정</th><th>작업</th></tr></thead><tbody>{items.map(p=><tr key={p.id}><td><button className="text-link" data-page-id={p.id} onClick={()=>go(pagePath(p.id))}>{p.title}</button><small className="row-meta">/{p.slug}</small></td><td><Status value={p.status} pending={p.pending}/></td><td>{date(p.updatedAt)}</td><td><button onClick={()=>go(pagePath(p.id))}>편집</button></td></tr>)}</tbody></table>{!items.length&&<Empty/>}</div></section></section>;
}
export function AccountsPanel({active,version,data}:Props) {
  const result=useRemote<AccountRow[]>('/accounts',active,version);
  return <section><Heading title="운영 계정 관리" note="계정 발급·역할 변경·비밀번호 초기화는 기존 계정 관리에서 처리합니다." actions={<LegacyLink href="/admin/accounts/new">계정 발급</LegacyLink>}/><Feedback {...result}/><section className="card"><header className="panel-header"><h2>운영 계정</h2><div className="heading-actions"><button onClick={result.reload}>새로고침</button><LegacyLink href="/admin/accounts">계정 상세 관리</LegacyLink></div></header><div className="table-scroll"><table className="data-table"><thead><tr><th>이름 / 아이디</th><th>역할</th><th>상태</th><th>발급일</th><th>작업</th></tr></thead><tbody>{result.data?.map(a=><tr key={a.id}><td>{a.displayName}{a.id===data.user.id&&<span className="scope-tag">나</span>}<small className="row-meta">{a.email}</small></td><td>{a.roleLabel}</td><td>{a.active?'활성':'비활성'}{a.passwordChangeRequired&&<small className="row-meta">비밀번호 변경 필요</small>}</td><td>{date(a.createdAt)}</td><td>{a.id!==data.user.id&&<LegacyLink href={`/admin/accounts/${a.id}/role`}>역할 변경</LegacyLink>}</td></tr>)}</tbody></table></div></section></section>;
}
export function RolesPanel({active,version}:Props) {
  const result=useRemote<RoleRow[]>('/roles',active,version);
  return <section><Heading title="역할 / 권한 관리" note="현재 적용된 권한 정의를 조회합니다. 역할 정책은 변경하지 않습니다." badge="조회" actions={<LegacyLink href="/admin/accounts">계정별 역할 변경</LegacyLink>}/><Feedback {...result}/><div className="roles-grid">{result.data?.map(role=><article className="card panel-pad" key={role.code}><span className="eyebrow">{role.code}</span><h2>{role.label}</h2><ul className="plain-list"><li>로그인 · 본인 비밀번호 변경</li><li>{role.allPosts?'전체 콘텐츠 관리':'본인 콘텐츠 관리'}</li><li>{role.allPosts?'전체 미디어 관리':'본인이 올린 미디어 관리'}</li>{role.allPosts&&<li>기존 페이지 내용·블록 편집 및 발행</li>}{role.publish?<li>콘텐츠 발행·공개 중단</li>:<li>본인 콘텐츠 초안 작성·수정 (발행 불가)</li>}{role.structure&&<li>새 페이지·URL · 메뉴 · 디자인 · 사이트 설정 · 공용 템플릿</li>}{role.permanentDelete&&<li>영구 삭제</li>}{role.manageAccounts&&<><li>운영 계정 · 역할 변경</li><li>활동 이력 조회</li></>}</ul></article>)}</div><p className="muted">SUPPORTER는 초안을 작성하고 ADMIN 또는 SUPER_ADMIN이 직접 발행합니다. 승인·반려 절차는 없습니다.</p></section>;
}
export function ActivityPanel({active,version}:Props) {
  const [q,setQ]=useState(''),[term,setTerm]=useState(''),[drafts,setDrafts]=useState(false),[page,setPage]=useState(0);
  const result=useRemote<ActivityList>('/activity?'+new URLSearchParams({q,drafts:String(drafts),page:String(page)}),active,version);
  return <section><Heading title="활동 이력" note="기존 저장·변경 이력을 조회합니다. 콘텐츠 버전 복원 기능은 제공하지 않습니다."/><section className="card"><form className="search-bar" onSubmit={e=>{e.preventDefault();setQ(term);setPage(0);}}><input aria-label="활동 이력 검색" maxLength={100} placeholder="이름·작업·대상 검색" value={term} onChange={e=>setTerm(e.target.value)}/><button>검색</button><label className="check-inline"><input type="checkbox" checked={drafts} onChange={e=>{setDrafts(e.target.checked);setPage(0);}}/>임시저장 포함</label><button type="button" onClick={result.reload}>새로고침</button></form><Feedback {...result}/><div className="table-scroll"><table className="data-table"><thead><tr><th>일시</th><th>운영자</th><th>작업</th><th>대상 / 내용</th></tr></thead><tbody>{result.data?.items.map(a=><tr key={a.id}><td>{date(a.createdAt)}</td><td>{a.actorName}</td><td>{a.action}</td><td>{a.target}<small className="row-meta">{a.detail}</small></td></tr>)}</tbody></table>{result.data?.items.length===0&&<Empty/>}</div>{result.data&&<Pager page={page} total={result.data.total} size={30} onChange={setPage}/>}</section></section>;
}
