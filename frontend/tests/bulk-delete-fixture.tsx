// Isolated, synthetic data only. Every fetch is intercepted here; no server writes.
import {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {PostsPanel,PagesPanel} from '../src/ReadPanels';
import {TrashPanel} from '../src/TrashPanel';
import {MediaPanel,LinkManager} from '../src/EditPanels';
import type {Bootstrap,PostRow} from '../src/types';
import '../src/styles.css';import '../src/workspace.css';import '../src/design.css';import '../src/posts-list.css';import '../src/calm-shell.css';import '../src/bulk-delete.css';
const classification={typeCode:'GENERAL',typeName:'일반',cohortIds:[],topicIds:[],cohorts:[],topics:[]};
let posts:PostRow[]=[],trash:any[]=[],requests:string[]=[],simulateFailure=false;
const makePost=(id:number)=>({id,title:['테스트 공지','테스트 후기','테스트 안내'][id-1],authorId:1,authorName:'검증 사용자',categoryId:null,status:id===1?'PUBLISHED':'DRAFT',pending:false,updatedAt:'2026-09-30T10:00:00',classification});
let menus=[{id:1,label:'테스트 메뉴',kind:'PAGE',targetId:1,url:'',sortOrder:0,visible:true},{id:2,label:'외부 메뉴',kind:'LINK',targetId:null,url:'https://example.com',sortOrder:1,visible:true}];
let links=[{id:1,label:'테스트 링크',url:'https://example.com',sortOrder:0}];
let media=[{id:1,name:'사용 중인 문서',alt:'',mime:'text/plain'},{id:2,name:'미사용 문서',alt:'',mime:'text/plain'}];
let pages=[{id:1,title:'메뉴에 연결된 페이지',slug:'linked',status:'DRAFT',revision:1,pending:false,updatedAt:''},{id:2,title:'연결 없는 페이지',slug:'free',status:'DRAFT',revision:2,pending:false,updatedAt:''}];
function reset(){posts=[1,2,3].map(makePost);trash=[{...makePost(10),title:'휴지통 예시',revision:5,deletedAt:'2026-09-30T10:00:00'}];requests=[];}
reset();
function response(data:unknown,status=200,url?:string){const result=new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});if(url)Object.defineProperty(result,'url',{value:url});return result;}
window.fetch=async(input,options={})=>{
 const url=new URL(String(input),location.origin),path=url.pathname.replace('/api/admin/next','');
 if(options.method&&options.method!=='GET')requests.push(`${options.method} ${path} ${options.body||''}`);
 if(path==='/classifications')return response({types:[{code:'GENERAL',name:'일반',active:true}],cohorts:[],topics:[],allowedTopics:[]});
 if(path==='/posts')return response({items:posts.filter(p=>!url.searchParams.get('status')||p.status===url.searchParams.get('status')),total:posts.length,page:0,pageSize:30});
 if(path==='/posts/trash')return response({items:trash,total:trash.length,page:0,pageSize:30});
 const post=/^\/posts\/(\d+)(\/trash)?$/.exec(path);
 if(post){const id=Number(post[1]);if(options.method){if(simulateFailure&&id===2)return response({message:'다른 사용자가 수정했습니다.'},409);if(options.method==='POST'){posts=posts.filter(p=>p.id!==id);}else trash=trash.filter(p=>p.id!==id);return response({id});}return response({...posts.find(p=>p.id===id),revision:10+id});}
 const page=/^\/admin\/pages\/(\d+)\/delete$/.exec(path);
 if(page){pages=pages.filter(p=>p.id!==Number(page[1]));return response({},200,location.origin+'/admin/pages');}
 if(path==='/menus'){return response(menus);}
 if(path.startsWith('/menus/')){menus=menus.filter(m=>m.id!==Number(path.split('/')[2]));return response(menus);}
 if(path==='/links')return response(links);
 if(path.startsWith('/links/')){links=links.filter(m=>m.id!==Number(path.split('/')[2]));return response(links);}
 if(path==='/media')return response(media);
 if(path.endsWith('/usage'))return response(path.includes('/1/')?[{label:'홈페이지에서 사용 중',href:null}]:[]);
 if(path.startsWith('/media/')){media=media.filter(m=>m.id!==Number(path.split('/')[2]));return response({ok:true});}
 return response({message:'Fixture route not defined: '+path},404);
};
function Fixture(){
 const [panel,setPanel]=useState('posts'),[version,setVersion]=useState(0),[query,setQuery]=useState(''),[restricted,setRestricted]=useState(false),[failure,setFailure]=useState(false);
 const refresh=()=>setVersion(v=>v+1);
 const data={user:{id:1,name:'검증 사용자',role:'SUPER_ADMIN',roleLabel:'최상위 관리자'},permissions:{site:true,structure:!restricted,permanentDelete:!restricted},csrf:{headerName:'X-CSRF-TOKEN',token:'fixture'},pages,menus,categories:[],images:[]} as Bootstrap;
 const props={active:true,version,data,refresh,go:(path:string)=>setQuery(path.split('?')[1]||''),search:new URLSearchParams(query)};
 return <div className="phase-two" style={{padding:24}}><h1>예시 데이터 전용 검증</h1><p>실제 서버 연결 및 업무 데이터 변경 없음</p><nav>{['posts','trash','pages','media','menus','links'].map(name=><button key={name} onClick={()=>{setPanel(name);setQuery('');}}>{name}</button>)}<button onClick={()=>{reset();refresh();}}>예시 초기화</button><label><input type="checkbox" checked={restricted} onChange={e=>setRestricted(e.target.checked)}/>삭제 권한 없음</label><label><input type="checkbox" checked={failure} onChange={e=>{simulateFailure=e.target.checked;setFailure(e.target.checked);}}/>두 번째 항목 충돌</label></nav>
 {panel==='posts'?<PostsPanel {...props} onChanged={refresh}/>:panel==='trash'?<TrashPanel {...props} onChanged={refresh} registerGuard={()=>{}}/>:panel==='pages'?<PagesPanel {...props} onOverview={()=>{}}/>:panel==='media'?<MediaPanel {...props}/>:<LinkManager key={panel} {...props} type={panel as 'menus'|'links'}/>}
 <details open><summary>검증 요청 기록</summary><pre>{requests.join('\n')}</pre></details></div>;
}
createRoot(document.getElementById('root')!).render(<Fixture/>);
