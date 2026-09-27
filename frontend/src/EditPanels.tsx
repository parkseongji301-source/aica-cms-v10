import {ComponentCatalog} from './ComponentCatalog';
import {useEffect,useRef,useState} from 'react';
import type {Bootstrap,Go,ImageFile,LinkItem,Menu,Usage} from './types';
import {get,send,uploadMedia} from './api';
import {date,Empty,Feedback,Heading,LegacyLink,messageOf,useRemote,useUnsaved} from './ui';

type Props={active:boolean;version:number;data:Bootstrap;refresh:()=>void;go:Go;search:URLSearchParams};
type Item=Menu|LinkItem;
type Form={label:string;destination:string;url:string;visible:boolean};
const blank=():Form=>({label:'',destination:'',url:'',visible:true});
const serialized=(value:unknown)=>JSON.stringify(value);

export function LinkManager({active,version,data,refresh,go,search,type}:{type:'menus'|'links'}&Props) {
  const result=useRemote<Item[]>('/'+type,active,version);
  const [items,setItems]=useState<Item[]>([]),[orderBase,setOrderBase]=useState('[]');
  const [editId,setEditId]=useState<number|null>(null),[form,setForm]=useState<Form>(blank),[formBase,setFormBase]=useState(serialized(blank()));
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
  const orderDirty=serialized(items.map(i=>i.id))!==orderBase,formDirty=serialized(form)!==formBase;
  const dirty=orderDirty||formDirty,dirtyRef=useRef(orderDirty);dirtyRef.current=orderDirty;
  const requested=useRef('');useUnsaved(dirty||busy);
  useEffect(()=>{if(result.data&&!dirtyRef.current){setItems(result.data);setOrderBase(serialized(result.data.map(i=>i.id)));}},[result.data]);
  function choose(item?:Item) {
    if(formDirty&&!window.confirm('작성 중인 메뉴/링크 입력을 버리고 다른 항목을 열까요?'))return false;
    const menu=item as Menu;
    const next=item?{label:item.label,url:item.url,visible:menu.visible??true,destination:type==='menus'?(menu.kind==='LINK'?'LINK':`${menu.kind}:${menu.targetId}`):''}:blank();
    setEditId(item?.id??null);setForm(next);setFormBase(serialized(next));setError('');setMessage('');return true;
  }
  const editParam=search.get('edit')||'';
  useEffect(()=>{
    if(!active||type!=='menus'||!result.data||!editParam||requested.current===editParam)return;
    requested.current=editParam;const item=result.data.find(i=>String(i.id)===editParam);
    if(item&&!choose(item))go('/menus'+(editId?'?edit='+editId:''));
  },[active,editParam,result.data]);
  function accepted(next:Item[]) {setItems(next);setOrderBase(serialized(next.map(i=>i.id)));refresh();}
  async function save() {
    if(orderDirty){setError('변경한 순서를 먼저 저장하거나 다시 조회하세요.');return;}
    setBusy(true);setError('');setMessage('');
    try{
      const [kind,id]=form.destination.split(':');
      const body=type==='menus'?{label:form.label,kind,targetId:id?Number(id):null,url:form.url,visible:form.visible}:{label:form.label,url:form.url};
      accepted(await send<Item[]>('/'+type+(editId===null?'':'/'+editId),editId===null?'POST':'PUT',body));
      setForm(blank());setFormBase(serialized(blank()));setEditId(null);setMessage('저장했습니다.');
    }catch(e){setError(messageOf(e));}finally{setBusy(false);}
  }
  async function remove(item:Item) {
    if(orderDirty){setError('변경한 순서를 먼저 저장하거나 다시 조회하세요.');return;}
    if(!window.confirm(`${item.label} ${type==='menus'?'메뉴':'링크'}를 삭제할까요? 연결된 콘텐츠와 페이지는 삭제하지 않습니다.`))return;
    setBusy(true);setError('');setMessage('');
    try{accepted(await send<Item[]>('/'+type+'/'+item.id,'DELETE'));if(editId===item.id){setEditId(null);setForm(blank());setFormBase(serialized(blank()));}setMessage('삭제했습니다.');}
    catch(e){setError(messageOf(e));}finally{setBusy(false);}
  }
  function move(index:number,step:number) {const next=[...items];[next[index],next[index+step]]=[next[index+step],next[index]];setItems(next);setMessage('순서 변경사항 있음');}
  async function saveOrder() {setBusy(true);setError('');try{accepted(await send<Item[]>('/'+type+'/order','PUT',{ids:items.map(i=>i.id)}));setMessage('순서를 저장했습니다.');}catch(e){setError(messageOf(e));}finally{setBusy(false);}}
  async function reloadOrder() {
    if(orderDirty&&!window.confirm('변경한 순서를 버리고 다시 조회할까요?'))return;
    setBusy(true);setError('');
    try{accepted(await get<Item[]>('/'+type));setMessage('저장된 순서를 다시 불러왔습니다.');}
    catch(e){setError(messageOf(e));}finally{setBusy(false);}
  }
  const isMenu=type==='menus';
  return <section><Heading title={isMenu?'메뉴 관리':'SNS / 외부 링크'} note={isMenu?'방문자용 메뉴의 연결 대상·순서·노출을 관리합니다.':'사이트에서 사용하는 SNS와 외부 링크를 관리합니다.'}/><Feedback loading={result.loading} error={error||result.error} message={message}/>
    <div className="management-split"><section className="card"><header className="panel-header"><h2>{isMenu?'홈페이지 메뉴':'등록된 링크'}</h2><span>{items.length}개</span></header><ul className="manage-list">{items.map((item,index)=>{
      const m=item as Menu;
      return <li key={item.id} className={editId===item.id?'editing-row':''}><div className="order-buttons"><button aria-label={`${item.label} 위로`} disabled={index===0||busy} onClick={()=>move(index,-1)}>↑</button><button aria-label={`${item.label} 아래로`} disabled={index===items.length-1||busy} onClick={()=>move(index,1)}>↓</button></div><div className="item-name"><strong>{item.label}</strong><small>{isMenu?`${({PAGE:'페이지',CATEGORY:'콘텐츠 분류',LINK:'직접 링크'} as Record<string,string>)[m.kind]} · ${m.visible?'표시':'숨김'}`:item.url}</small>{isMenu&&m.kind==='LINK'&&<small>{m.url}</small>}</div><button className="text-link" disabled={busy} onClick={()=>choose(item)}>수정</button><button className="danger-link" disabled={busy} onClick={()=>void remove(item)}>삭제</button></li>;
    })}</ul>{!items.length&&<Empty/>}<footer className="panel-actions"><button disabled={!orderDirty||busy} onClick={()=>void saveOrder()}>순서 저장</button><button disabled={busy} onClick={()=>void reloadOrder()}>다시 조회</button></footer></section>
    <form className="card panel-pad side-form" onSubmit={e=>{e.preventDefault();void save();}}><h2>{isMenu?'메뉴':'링크'} {editId?'수정':'추가'}</h2><fieldset disabled={busy}>
      {isMenu&&<label>연결 대상<select required value={form.destination} onChange={e=>setForm({...form,destination:e.target.value})}><option value="">선택하세요</option><optgroup label="페이지">{data.pages.map(p=><option key={p.id} value={'PAGE:'+p.id}>{p.title}</option>)}</optgroup><optgroup label="콘텐츠 분류">{data.categories.map(c=><option key={c.id} value={'CATEGORY:'+c.id}>{c.name}</option>)}</optgroup><option value="LINK">직접 링크</option></select></label>}
      {(!isMenu||form.destination==='LINK')&&<><label>{isMenu?'메뉴 이름':'링크 이름'}<input required maxLength={80} value={form.label} onChange={e=>setForm({...form,label:e.target.value})}/></label><label>연결 주소<input required maxLength={1000} placeholder="https:// 또는 /로 시작하는 주소" value={form.url} onChange={e=>setForm({...form,url:e.target.value})}/></label></>}
      {isMenu&&<label className="check-inline"><input type="checkbox" checked={form.visible} onChange={e=>setForm({...form,visible:e.target.checked})}/>메뉴에 표시</label>}
      <div className="panel-actions"><button className="primary" disabled={busy||!formDirty}>{editId?'저장':'추가'}</button>{editId&&<button type="button" onClick={()=>choose()}>새 항목</button>}</div>
    </fieldset></form></div></section>;
}

type Group='basic'|'style'|'components'|'system';
const settingsConfig:Record<Group,{title:string;keys:string[];note:string}>={
  basic:{title:'기본 정보',keys:['siteName','description','contactEmail','homePageId'],note:'사이트 전체에 사용하는 기본 정보입니다.'},
  style:{title:'공통 스타일',keys:['primaryColor','headerColor','radius'],note:'여러 페이지에서 함께 사용하는 색상과 모양입니다.'},
  components:{title:'공통 컴포넌트',keys:['logoId','headerNote','footerText'],note:'페이지에서 사용할 수 있는 블록과 사이트 공통 영역을 확인합니다.'},
  system:{title:'시스템 설정',keys:['postsPerPage'],note:'현재는 목록당 글 수를 설정할 수 있습니다.'}
};
export function SettingsPanel({active,version,data,refresh,group}:{group:Group}&Props) {
  const config=settingsConfig[group],result=useRemote<Record<string,string>>('/settings/'+group,active,version);
  const [form,setForm]=useState<Record<string,string>|null>(null),[base,setBase]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
  const dirty=form!==null&&serialized(form)!==base,protect=useRef(false);protect.current=dirty||busy;useUnsaved(dirty||busy);
  const pick=(value:Record<string,string>)=>Object.fromEntries(config.keys.map(k=>[k,value[k]||'']));
  useEffect(()=>{if(result.data&&!protect.current){const next=pick(result.data);setForm(next);setBase(serialized(next));}},[result.data]);
  const field=(key:string,value:string)=>{setForm(current=>({...current,[key]:value}));setMessage('');};
  async function save(){if(!form)return;setBusy(true);setError('');setMessage('');try{const next=pick(await send<Record<string,string>>('/settings/'+group,'PUT',form));setForm(next);setBase(serialized(next));setMessage('공통 설정을 저장했습니다.');refresh();}catch(e){setError(messageOf(e));}finally{setBusy(false);}}
  async function reloadSettings() {
    if(dirty&&!window.confirm('입력한 설정을 버리고 다시 조회할까요?'))return;
    setBusy(true);setError('');setMessage('');
    try{const next=pick(await get<Record<string,string>>('/settings/'+group));setForm(next);setBase(serialized(next));setMessage('저장된 설정을 다시 불러왔습니다.');}
    catch(e){setError(messageOf(e));}finally{setBusy(false);}
  }
  return <section><Heading title={config.title} note={config.note} badge={group==='system'?'일부 구현':undefined}/>
    {group==='components'&&<><ComponentCatalog active={active} version={version}/><h2>사이트 공통 영역</h2><p className="muted">로고와 상단·하단 문구는 사이트 전체에 적용됩니다.</p></>}
    <Feedback loading={result.loading} error={error||result.error} message={message}/>{form&&<form className="card panel-pad settings-panel" onSubmit={e=>{e.preventDefault();void save();}}><fieldset disabled={busy}>
      {group==='basic'&&<><label>사이트 이름<input required maxLength={80} value={form.siteName} onChange={e=>field('siteName',e.target.value)}/></label><label>사이트 소개<textarea maxLength={500} rows={4} value={form.description} onChange={e=>field('description',e.target.value)}/></label><label>문의 이메일<input type="email" maxLength={254} value={form.contactEmail} onChange={e=>field('contactEmail',e.target.value)}/></label><label>첫 화면<select value={form.homePageId} onChange={e=>field('homePageId',e.target.value)}><option value="">글 목록</option>{data.pages.filter(p=>p.status==='PUBLISHED').map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></label></>}
      {group==='style'&&<><div className="color-fields"><label>강조색<input type="color" value={form.primaryColor} onChange={e=>field('primaryColor',e.target.value)}/><span>{form.primaryColor}</span></label><label>상단 배경색<input type="color" value={form.headerColor} onChange={e=>field('headerColor',e.target.value)}/><span>{form.headerColor}</span></label></div><label>모서리<select value={form.radius} onChange={e=>field('radius',e.target.value)}>{[['0','직각'],['8','조금 둥글게'],['12','기본'],['20','둥글게']].map(([v,label])=><option key={v} value={v}>{label}</option>)}</select></label></>}
      {group==='components'&&<><label>로고<select value={form.logoId} onChange={e=>field('logoId',e.target.value)}><option value="">사이트 이름 사용</option>{data.images.map(m=><option key={m.id} value={m.id}>{m.name}</option>)}</select></label>{form.logoId&&<img className="logo-preview" src={'/admin/media/'+form.logoId+'/file'} alt="선택한 로고"/>}<label>상단 문구<input maxLength={120} value={form.headerNote} onChange={e=>field('headerNote',e.target.value)}/></label><label>하단 문구<textarea rows={4} maxLength={500} value={form.footerText} onChange={e=>field('footerText',e.target.value)}/></label></>}
      {group==='system'&&<label>목록당 글 수<select value={form.postsPerPage} onChange={e=>field('postsPerPage',e.target.value)}>{['6','12','24'].map(v=><option key={v} value={v}>{v}개</option>)}</select></label>}
      <div className="panel-actions"><button className="primary" disabled={!dirty||busy}>변경사항 저장</button><button type="button" onClick={()=>void reloadSettings()}>다시 조회</button><span className="muted">사이트 공통 설정</span></div>
    </fieldset></form>}</section>;
}

export function MediaPanel({active,version,refresh,data}:Props) {
  const [q,setQ]=useState(''),[term,setTerm]=useState('');const result=useRemote<ImageFile[]>('/media?q='+encodeURIComponent(q),active,version);
  const [selected,setSelected]=useState<ImageFile|null>(null),[form,setForm]=useState({name:'',alt:''}),[base,setBase]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[uses,setUses]=useState<Usage[]>([]);
  const dirty=selected!==null&&serialized(form)!==base;useUnsaved(dirty||busy);
  useEffect(()=>{if(!selected||!active)return;const controller=new AbortController();void get<Usage[]>('/media/'+selected.id+'/usage',controller.signal).then(setUses).catch(e=>{if(!controller.signal.aborted)setError(messageOf(e));});return()=>controller.abort();},[selected?.id,active]);
  function choose(item:ImageFile){if(dirty&&!window.confirm('파일 정보 입력을 버리고 다른 파일을 열까요?'))return;setSelected(item);const next={name:item.name,alt:item.alt};setForm(next);setBase(serialized(next));setError('');setUses([]);}
  async function upload(file?:File){if(!file)return;setBusy(true);setError('');setMessage('');try{if(file.size>5*1024*1024)throw new Error('파일은 5MB 이하로 선택하세요.');await uploadMedia(file);result.reload();refresh();setMessage('업로드했습니다.');}catch(e){setError(messageOf(e));}finally{setBusy(false);}}
  async function save(){if(!selected)return;setBusy(true);setError('');try{const saved=await send<ImageFile>('/media/'+selected.id,'PUT',form);setSelected(saved);setBase(serialized(form));setMessage('파일 정보를 저장했습니다.');result.reload();refresh();}catch(e){setError(messageOf(e));}finally{setBusy(false);}}
  async function remove(){if(!selected)return;setBusy(true);setError('');try{const currentUses=await get<Usage[]>('/media/'+selected.id+'/usage');setUses(currentUses);if(currentUses.length){setError('사용 중인 파일은 영구 삭제할 수 없습니다.');return;}if(!window.confirm(`${selected.name}: 현재 참조 없음. 파일을 영구 삭제하면 복구할 수 없습니다. 계속할까요?`))return;await send('/media/'+selected.id,'DELETE');setSelected(null);setMessage('삭제했습니다.');result.reload();refresh();}catch(e){setError(messageOf(e));try{setUses(await get<Usage[]>('/media/'+selected.id+'/usage'));}catch{/* The original error remains visible. */}}finally{setBusy(false);}}
  return <section><Heading title="미디어 관리" note="기존 이미지·문서 저장소를 사용합니다." actions={<label className={'upload-button'+(busy?' disabled':'')}>＋ 파일 업로드<input type="file" disabled={busy} accept=".jpg,.jpeg,.png,.pdf,.txt,.docx,.xlsx,.pptx,.hwp" onChange={e=>{void upload(e.target.files?.[0]);e.target.value='';}}/></label>}/><Feedback loading={result.loading||busy} error={error||result.error} message={message}/><div className="management-split"><section className="card"><form className="search-bar" onSubmit={e=>{e.preventDefault();setQ(term);}}><input aria-label="미디어 검색" value={term} maxLength={100} onChange={e=>setTerm(e.target.value)} placeholder="파일 이름 검색"/><button>검색</button><button type="button" onClick={result.reload}>새로고침</button></form><div className="media-grid">{result.data?.map(item=><button className={'media-tile'+(selected?.id===item.id?' selected':'')} key={item.id} disabled={busy} onClick={()=>choose(item)}>{item.mime.startsWith('image/')?<img src={'/admin/media/'+item.id+'/file'} alt={item.alt||item.name}/>:<span className="file-icon">문서</span>}<strong>{item.name}</strong><small>{item.ownerName} · {Math.ceil((item.byteSize||0)/1024)} KB</small></button>)}</div>{result.data?.length===0&&<Empty/>}</section><aside className="card panel-pad side-form"><h2>파일 정보</h2>{selected?<form onSubmit={e=>{e.preventDefault();void save();}}><fieldset disabled={busy}><label>파일 이름<input required maxLength={200} value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></label><label>대체 텍스트<textarea maxLength={300} rows={3} value={form.alt} onChange={e=>setForm({...form,alt:e.target.value})}/></label><p className="muted">{date(selected.createdAt||'')}</p><a href={'/admin/media/'+selected.id+'/file'} target="_blank" rel="noopener">파일 열기 ↗</a><div className="panel-actions"><button className="primary" disabled={!dirty||busy}>저장</button>{data.permissions.permanentDelete&&<button type="button" className="danger-link" onClick={()=>void remove()}>영구 삭제</button>}</div></fieldset></form>:<p className="muted">목록에서 파일을 선택하세요.</p>}{uses.length>0&&<div className="usage-list"><h3>사용 중인 위치</h3>{uses.map((u,i)=>u.href?<a href={u.href} target="_blank" rel="noopener noreferrer" key={i}>{u.label} ↗</a>:<p key={i}>{u.label}</p>)}</div>}</aside></div></section>;
}
