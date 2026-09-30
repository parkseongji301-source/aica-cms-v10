import type {EditorGuard} from './editorGuard';
import {useEffect,useState} from 'react';
import {useBulkDelete} from './BulkDelete';
import {getPost,send,deletePage} from './api';
import type {AccountRow,ActivityList,Bootstrap,Dashboard,Go,PostList,PostRow,RoleRow,ClassificationCatalog} from './types';
import {postTypeFilterChange} from './postListFilters';
import {AppliedPostFilters,ContentListTable,PostListFilters,postStatusLabels} from './PostListPresentation';
import {contextualPostPath,filterValues,contentContext,draftSelection,scopedPostParams,contentSections,contentLocationLabel} from './contentNavigation';
import type {ContentContext} from './contentNavigation';
import {CreateContentDraft} from './CreateContentDraft';
import {listDraftSelection} from './draftCreation';
import './classification.css';
import {contentPath,pagePath} from './navigation';
import {date,Empty,Feedback,Heading,LegacyLink,Pager,Status,useRemote} from './ui';

type Props={registerGuard?:(path:string,guard:EditorGuard|null)=>void;active:boolean;version:number;data:Bootstrap;go:Go;search:URLSearchParams};
const emptyPosts:PostRow[]=[];
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
export function PostsPanel({active,version,data,go,search,onChanged,registerGuard}:Props&{onChanged:(id:number)=>void}) {
  const q=search.get('q')||'',status=search.get('status')||'',category=search.get('categoryId')||'',page=Math.max(0,Number(search.get('page')||0)||0);
  const [term,setTerm]=useState(q);useEffect(()=>setTerm(q),[q]);
  const catalog=useRemote<ClassificationCatalog>('/classifications',active,version);
  const scope=contentContext(search,catalog.data),section=scope?contentSections[scope.type]:null;
  const params=scopedPostParams(search,scope);
  const multi=(key:string)=>filterValues(params,key);
  const selectedTypes=multi('typeCodes');
  const result=useRemote<PostList>('/posts?'+params,active&&(!scope||!scope.error),version);
  const deletion=useBulkDelete({items:result.data?.items||emptyPosts,registerGuard,guardPath:"/posts",active,scope:params.toString(),allowed:!!data.permissions.permanentDelete,disabled:result.loading||!!result.error||!!scope?.error,label:(post)=>post.title,
    prepare:async(post)=>{const current=await getPost(post.id);return {id:current.id,label:current.title,revision:current.revision};},
    remove:target=>send(`/posts/${target.id}/trash`,'POST',{revision:target.revision,confirmed:true}),
    onDone:ids=>{ids.forEach(onChanged);if(page>0&&ids.length===result.data?.items.length)change({page:String(page-1)});else result.reload();},
    description:'선택한 콘텐츠가 목록과 공개 화면에서 사라집니다. 본문·첨부·분류·버전 이력은 보관하며 휴지통에서 복원할 수 있습니다.'});
  const [creating,setCreating]=useState(false);
  const change=(values:Record<string,string>)=>{const p=new URLSearchParams(params);Object.entries({page:'0',...values}).forEach(([k,v])=>v?p.set(k,v):p.delete(k));if(scope)p.set(scope.param,scope.key);go('/posts?'+p);};
  const toggle=(key:string,value:string)=>change({[key]:(multi(key).includes(value)?multi(key).filter(v=>v!==value):[...multi(key),value]).join(',')});
  const selectTypes=(types:string[])=>{if(catalog.data&&!scope)change(postTypeFilterChange(params,types,catalog.data));};
  const categoryName=data.categories.find(c=>String(c.id)===category)?.name;
  const listTitle=scope?contentLocationLabel(scope):categoryName?`콘텐츠 목록 · ${categoryName}`:'전체 콘텐츠';
  const resetFilters=()=>{setTerm('');change({q:'',status:'',categoryId:'',typeCodes:scope?.type||'',cohortIds:'',topicIds:scope?.topicId?String(scope.topicId):''});};
  const filtered=!!(q||status||!scope&&category||!scope&&selectedTypes.length||multi('cohortIds').length||scope?.topicId==null&&multi('topicIds').length);
  const filters={params,scope,catalog:catalog.data,categories:data.categories,canManageCategories:!!data.permissions.structure,change,selectTypes,toggle};
  return <section className="content-workspace posts-workspace"><Heading title={listTitle} note={scope?`유형: ${section!.label}${scope.key==='all'?'':` · 주제: ${scope.label}`} · 저장된 작성본 기준`:'필요한 글을 찾고, 이어 쓰고, 게시하세요.'} actions={<button className="primary" disabled={!!scope?.error||!catalog.data||!!catalog.error||catalog.loading} onClick={()=>setCreating(true)}>＋ 새 {scope?listTitle:'콘텐츠'} 작성</button>}/>
    {active&&creating&&catalog.data&&!scope?.error&&<CreateContentDraft contextLabel={scope?listTitle:undefined} selection={scope?draftSelection(scope):listDraftSelection(selectedTypes,catalog.data)} catalog={catalog.data} allowTypeSelection={!scope} categoryId={scope?null:category?Number(category):null} onClose={()=>setCreating(false)} onCreated={post=>{setCreating(false);result.reload();go(contextualPostPath(post.id,scope));}}/>}
    {scope?.error&&<Feedback error={catalog.error||scope.error}/>}
    <section className="card posts-list-card">
      <div className="posts-status-bar"><div className="status-tabs" role="group" aria-label="게시 상태">{['','DRAFT','PUBLISHED','PRIVATE'].map(value=><button key={value} aria-pressed={status===value} onClick={()=>change({status:value})}>{value?postStatusLabels[value]:'전체'}</button>)}</div><button className="text-link posts-refresh" onClick={result.reload}>새로고침</button></div>
      <div className="posts-search-area"><form className="posts-search" role="search" onSubmit={event=>{event.preventDefault();change({q:term});}}><label className="posts-sr-only" htmlFor="posts-search-input">콘텐츠 검색</label><input id="posts-search-input" type="search" maxLength={100} value={term} onChange={event=>setTerm(event.target.value)} placeholder={scope?.type==='FAQ'?'질문·답변 검색':scope?.type==='RESTAURANT'?'식당명·소개 검색':'제목·본문으로 검색'}/><button type="submit">검색</button></form>
        {!scope&&<><PostListFilters {...filters}/><AppliedPostFilters {...filters} onReset={resetFilters}/></>}
        <Feedback {...catalog}/>{catalog.error&&<button onClick={catalog.reload}>분류 다시 불러오기</button>}
      </div>
      <div className="posts-results-heading"><div className="list-inline-actions"><span className="bulk-mobile-select">{deletion.selectAll}</span><span role="status">{result.loading?'콘텐츠를 불러오는 중…':result.error||scope?.error?'목록을 확인할 수 없습니다.':result.data?<>콘텐츠 <strong>{result.data.total.toLocaleString()}</strong>개</>:''}</span></div><div className="list-inline-actions"><span>분류는 저장된 작성본 기준</span>{deletion.action}</div></div>
      {deletion.feedback}{deletion.dialog}
      {result.error&&<div className="posts-list-feedback"><Feedback error={result.error}/><button onClick={result.reload}>다시 시도</button></div>}
      {result.loading&&<div className="posts-loading" aria-hidden="true">{[1,2,3].map(row=><div key={row}><span/><span/><span/></div>)}</div>}
      {result.data&&!result.loading&&!result.error&&!scope?.error&&<><ContentListTable items={result.data.items} categories={data.categories} go={go} scope={scope} filtered={filtered} onReset={resetFilters} selection={data.permissions.permanentDelete?deletion:undefined}/><Pager page={page} total={result.data.total} size={result.data.pageSize} onChange={p=>change({page:String(p)})}/></>}
    </section>
  </section>;
}
export function PagesPanel({active,data,go,onOverview,refresh,registerGuard}:Props&{onOverview:(id:number)=>void;refresh:()=>void}) {
  const [q,setQ]=useState(''),[status,setStatus]=useState('');const items=data.pages.filter(p=>p.title.toLowerCase().includes(q.toLowerCase())&&(!status||p.status===status));
  const deletion=useBulkDelete({items,registerGuard,guardPath:"/pages",active,scope:q+':'+status,allowed:!!data.permissions.permanentDelete,label:page=>page.title,permanent:true,
    prepare:async page=>{if(data.menus.some(menu=>menu.kind==='PAGE'&&menu.targetId===page.id))throw new Error('메뉴에 연결되어 있습니다. 연결을 해제하세요.');return {id:page.id,label:page.title,revision:page.revision};},
    remove:target=>deletePage(target.id,target.revision!),onDone:()=>refresh(),
    description:'선택한 페이지와 구성·발행본·버전 이력을 영구삭제합니다. 복구할 수 없습니다. 메뉴나 홈페이지 첫 화면에서 사용 중인 페이지는 삭제되지 않습니다. 미디어 파일은 유지됩니다.'});
  return <section className="pages-workspace"><Heading title="전체 페이지 현황" note="페이지 상태와 연결된 메뉴를 확인하고 내용을 편집합니다." actions={<LegacyLink href="/admin/pages">{data.permissions.structure?'페이지 추가·게시 관리':'페이지 게시 관리'}</LegacyLink>}/><section className="card"><div className="search-bar"><input aria-label="페이지 검색" value={q} onChange={e=>setQ(e.target.value)} placeholder="페이지 이름 검색"/><select aria-label="페이지 상태" value={status} onChange={e=>setStatus(e.target.value)}><option value="">전체 상태</option><option value="DRAFT">임시보관</option><option value="PUBLISHED">게시됨</option><option value="PRIVATE">비공개</option></select><span>{items.length}개</span></div>{deletion.feedback}{deletion.dialog}<div className="table-scroll"><table className="data-table pages-table"><thead><tr><th>페이지</th><th>상태</th><th>연결된 메뉴</th><th>최근 수정</th><th>작업</th></tr></thead><tbody>{items.map(p=>{const links=data.menus.filter(m=>m.kind==='PAGE'&&m.targetId===p.id);return <tr key={p.id}><td><button className="text-link" data-page-id={p.id} onClick={()=>go(pagePath(p.id))}>{p.title}</button><small className="row-meta">/{p.slug}</small></td><td><Status value={p.status} pending={p.pending} pageWording/></td><td>{links.length?links.map(m=><span className="page-menu-label" key={m.id}>{m.label}{!m.visible&&<small> · 메뉴 숨김</small>}</span>):<span className="muted">메뉴 미연결</span>}</td><td>{date(p.updatedAt)}</td><td><div className="page-row-actions"><button onClick={()=>go(pagePath(p.id))}>편집</button><button onClick={()=>onOverview(p.id)}>구조 보기</button>{deletion.rowButton(p)}</div></td></tr>;})}</tbody></table>{!items.length&&<Empty>{q||status?'조건에 맞는 페이지가 없습니다. 검색어나 상태를 바꿔보세요.':'등록된 페이지가 없습니다. 페이지 관리에서 추가할 수 있습니다.'}</Empty>}</div></section><p className="muted page-list-note">메뉴 연결과 게시 상태를 함께 확인하세요. 내용 저장만으로 현재 공개본이 바뀌지는 않습니다.</p></section>;
}
export function AccountsPanel({active,version,data}:Props) {
  const result=useRemote<AccountRow[]>('/accounts',active,version);
  return <section className="accounts-workspace admin-table-workspace"><Heading title="운영 계정 관리" note="계정 발급·역할 변경·비밀번호 초기화는 기존 계정 관리에서 처리합니다." actions={<LegacyLink href="/admin/accounts/new">계정 발급</LegacyLink>}/><Feedback {...result}/><section className="card"><header className="panel-header"><h2>운영 계정</h2><div className="heading-actions"><button onClick={result.reload}>새로고침</button><LegacyLink href="/admin/accounts">계정 상세 관리</LegacyLink></div></header><div className="table-scroll"><table className="data-table"><thead><tr><th>이름 / 아이디</th><th>역할</th><th>상태</th><th>발급일</th><th>작업</th></tr></thead><tbody>{result.data?.map(a=><tr key={a.id}><td><strong>{a.displayName}</strong>{a.id===data.user.id&&<span className="scope-tag">나</span>}<small className="row-meta">{a.email}</small></td><td>{a.roleLabel}</td><td><span className={"account-state "+(a.active?'enabled':'disabled')}>{a.active?'사용 중':'사용 중지'}</span>{a.passwordChangeRequired&&<small className="row-meta">비밀번호 변경 필요</small>}</td><td>{date(a.createdAt)}</td><td>{a.id!==data.user.id&&<LegacyLink href={`/admin/accounts/${a.id}/role`}>역할 변경</LegacyLink>}</td></tr>)}</tbody></table></div></section></section>;
}
export function RolesPanel({active,version}:Props) {
  const result=useRemote<RoleRow[]>('/roles',active,version);
  return <section className="roles-workspace"><Heading title="역할·권한 안내" note="역할별로 가능한 업무를 확인하세요. 계정의 역할 변경은 기존 계정 관리에서 진행합니다." badge="조회" actions={<LegacyLink href="/admin/accounts">계정별 역할 변경</LegacyLink>}/><Feedback {...result}/><div className="roles-grid">{result.data?.map(role=><article className="card panel-pad" key={role.code}><span className="eyebrow">{role.code}</span><h2>{role.label}</h2><ul className="plain-list"><li>로그인 · 본인 비밀번호 변경</li><li>{role.allPosts?'전체 콘텐츠 관리':'본인 콘텐츠 관리'}</li><li>{role.allPosts?'전체 미디어 관리':'본인이 올린 미디어 관리'}</li>{role.allPosts&&<li>기존 페이지 내용·블록 편집 및 게시</li>}{role.publish?<li>콘텐츠 게시·공개 중단</li>:<li>본인 콘텐츠 작성·수정 (게시 불가)</li>}{role.structure&&<li>새 페이지·URL · 메뉴 · 디자인 · 사이트 설정 · 페이지 템플릿</li>}{role.permanentDelete&&<li>영구 삭제</li>}{role.manageAccounts&&<><li>운영 계정 · 역할 변경</li><li>활동 이력 조회</li></>}</ul></article>)}</div><p className="muted">서포터즈는 작성본을 준비하고 관리자 또는 최상위 관리자가 게시합니다. 별도의 승인·반려 절차는 없습니다.</p></section>;
}
export function ActivityPanel({active,version}:Props) {
  const [q,setQ]=useState(''),[term,setTerm]=useState(''),[drafts,setDrafts]=useState(false),[page,setPage]=useState(0);
  const result=useRemote<ActivityList>('/activity?'+new URLSearchParams({q,drafts:String(drafts),page:String(page)}),active,version);
  return <section className="activity-workspace admin-table-workspace"><Heading title="활동 이력" note="누가 언제 어떤 작업을 했는지 확인합니다. 과거 내용의 복구는 각 편집 화면의 버전 이력에서 진행하세요."/><section className="card"><form className="search-bar" onSubmit={e=>{e.preventDefault();setQ(term);setPage(0);}}><input aria-label="활동 이력 검색" maxLength={100} placeholder="이름·작업·대상 검색" value={term} onChange={e=>setTerm(e.target.value)}/><button>검색</button><label className="check-inline"><input type="checkbox" checked={drafts} onChange={e=>{setDrafts(e.target.checked);setPage(0);}}/>임시보관 기록 포함</label><button type="button" onClick={result.reload}>새로고침</button></form><Feedback {...result}/><div className="table-scroll"><table className="data-table"><thead><tr><th>일시</th><th>운영자</th><th>작업</th><th>대상 / 내용</th></tr></thead><tbody>{result.data?.items.map(a=><tr key={a.id}><td>{date(a.createdAt)}</td><td>{a.actorName}</td><td>{a.action}</td><td>{a.target}<small className="row-meta">{a.detail}</small></td></tr>)}</tbody></table>{!result.loading&&!result.error&&result.data?.items.length===0&&<Empty>{q?'검색 결과가 없습니다. 이름·작업·대상을 바꿔 검색하세요.':'표시할 활동 기록이 없습니다.'}</Empty>}</div>{result.data&&<Pager page={page} total={result.data.total} size={30} onChange={setPage}/>}</section></section>;
}
