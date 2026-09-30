import type {EditorGuard} from './editorGuard';
import {useBulkDelete} from './BulkDelete';
import {ComponentCatalog} from './ComponentCatalog';
import {useEffect,useRef,useState} from 'react';
import type {Bootstrap,Go,ImageFile,LinkItem,Menu,Usage} from './types';
import {get,send,uploadMedia} from './api';
import {date,Empty,Feedback,Heading,messageOf,useRemote,useUnsaved} from './ui';
import {usageRoute} from './usageRoute';

type Props={registerGuard?:(path:string,guard:EditorGuard|null)=>void;active:boolean;version:number;data:Bootstrap;refresh:()=>void;go:Go;search:URLSearchParams};
type Item=Menu|LinkItem;
type Form={label:string;destination:string;url:string;visible:boolean};
const blank=():Form=>({label:'',destination:'',url:'',visible:true});
const serialized=(value:unknown)=>JSON.stringify(value);

export function LinkManager({active,version,data,refresh,go,search,type,registerGuard}:{type:'menus'|'links'}&Props) {
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
  const deletion=useBulkDelete({items,registerGuard,guardPath:type==="menus"?"/menus":"/settings/links",active,scope:type,allowed:!!data.permissions.structure,disabled:busy||dirty||result.loading||!!result.error,label:item=>item.label,permanent:true,
    prepare:async item=>({id:item.id,label:item.label}),remove:target=>send('/'+type+'/'+target.id,'DELETE'),
    onDone:ids=>{if(editId!==null&&ids.includes(editId)){setEditId(null);setForm(blank());setFormBase(serialized(blank()));}result.reload();refresh();},
    description:'선택한 메뉴 또는 외부 링크를 삭제합니다. 연결된 콘텐츠와 페이지는 유지됩니다. 삭제한 메뉴·링크 항목은 복구할 수 없습니다.'});
  const isMenu=type==='menus';
  // New menus link to a page or a URL. A menu that already links to a category keeps it until migration.
  const editingMenu=isMenu&&editId?items.find(i=>i.id===editId) as Menu|undefined:undefined;
  const legacyCategory=editingMenu?.kind==='CATEGORY'?data.categories.find(c=>c.id===editingMenu.targetId)??null:null;
  return <section className={isMenu?"menu-workspace operations-workspace":"links-workspace operations-workspace"}><Heading title={isMenu?'메뉴 관리':'SNS / 외부 링크'} note={isMenu?'방문자용 메뉴의 연결 대상·순서·노출을 관리합니다.':'홈페이지의 SNS·외부 연결 주소입니다. 항목과 순서를 각각 저장하세요.'}/><Feedback loading={result.loading} error={error||result.error} message={message}/>
    {isMenu&&<p className="operation-note">메뉴 이름·연결·표시는 항목별로 저장합니다. 화살표로 바꾼 순서는 ‘순서 저장’을 눌러 반영하세요.</p>}<div className="management-split"><section className="card"><header className="panel-header"><div className="list-inline-actions">{!isMenu&&deletion.selectAll}<h2>{isMenu?'홈페이지 메뉴':'등록된 링크'}</h2></div><div className="list-inline-actions"><span>{items.length}개</span>{!isMenu&&deletion.action}</div></header>{deletion.feedback}{deletion.dialog}{dirty&&<p className="operation-note">삭제 전에 입력 내용과 순서 변경을 저장하거나 취소하세요.</p>}<ul className="manage-list">{items.map((item,index)=>{
      const m=item as Menu;
      return <li key={item.id} className={editId===item.id?'editing-row':''}>{!isMenu&&deletion.checkbox(item)}<div className="order-buttons"><button aria-label={`${item.label} 위로`} disabled={index===0||busy} onClick={()=>move(index,-1)}>↑</button><button aria-label={`${item.label} 아래로`} disabled={index===items.length-1||busy} onClick={()=>move(index,1)}>↓</button></div><div className="item-name"><strong>{item.label}</strong><small>{isMenu?`${({PAGE:'페이지',CATEGORY:'기존 카테고리 · 이관 대상',LINK:'직접 링크'} as Record<string,string>)[m.kind]} · ${m.visible?'표시':'숨김'}`:item.url}</small>{isMenu&&<small className="menu-target">연결: {m.kind==='PAGE'?data.pages.find(p=>p.id===m.targetId)?.title||'연결된 페이지 확인 필요':m.kind==='CATEGORY'?data.categories.find(c=>c.id===m.targetId)?.name||'연결된 분류 확인 필요':m.url}</small>}</div><button className="text-link" disabled={busy} onClick={()=>choose(item)}>수정</button>{deletion.rowButton(item)}</li>;
    })}</ul>{!result.loading&&!result.error&&!items.length&&<Empty>{isMenu?'등록된 메뉴가 없습니다. 옆의 메뉴 추가에서 시작하세요.':'등록된 링크가 없습니다.'}</Empty>}<footer className="panel-actions">{isMenu&&<span className="save-hint" role="status">{orderDirty?'순서 변경사항 있음 · 저장 필요':'저장된 순서'}</span>}<button disabled={!orderDirty||busy} onClick={()=>void saveOrder()}>순서 저장</button><button disabled={busy} onClick={()=>void reloadOrder()}>다시 조회</button></footer></section>
    <form className="card panel-pad side-form" onSubmit={e=>{e.preventDefault();void save();}}><h2>{isMenu?'메뉴':'링크'} {editId?'수정':'추가'}</h2>{isMenu&&<p className="operation-note">{editId?items.find(i=>i.id===editId)?.label:'연결할 페이지 또는 직접 링크를 선택하세요.'}{orderDirty&&' · 순서 변경을 먼저 저장하거나 다시 조회하세요.'}</p>}<fieldset disabled={busy}>
      {isMenu&&<label>연결 대상<select required value={form.destination} onChange={e=>setForm({...form,destination:e.target.value})}><option value="">선택하세요</option><optgroup label="페이지">{data.pages.map(p=><option key={p.id} value={'PAGE:'+p.id}>{p.title}</option>)}</optgroup>{editingMenu?.kind==='CATEGORY'&&<optgroup label="기존 카테고리 연결 · 이관 대상"><option value={'CATEGORY:'+editingMenu.targetId}>{(legacyCategory?.name??'카테고리 #'+editingMenu.targetId)+' (이관 전까지 유지)'}</option></optgroup>}<option value="LINK">직접 링크</option></select></label>}
      {isMenu&&editingMenu?.kind==='CATEGORY'&&<p className="operation-note migration-note" role="note">이관 대상 메뉴입니다. 기존 카테고리 연결은 이관 전까지 그대로 유지됩니다. 새로 연결할 때는 페이지 또는 직접 링크를 선택하세요.</p>}
      {(!isMenu||form.destination==='LINK')&&<><label>{isMenu?'메뉴 이름':'링크 이름'}<input required maxLength={80} value={form.label} onChange={e=>setForm({...form,label:e.target.value})}/></label><label>연결 주소<input required maxLength={1000} placeholder="https:// 또는 /로 시작하는 주소" value={form.url} onChange={e=>setForm({...form,url:e.target.value})}/></label></>}
      {isMenu&&<label className="check-inline"><input type="checkbox" checked={form.visible} onChange={e=>setForm({...form,visible:e.target.checked})}/>메뉴에 표시</label>}
      <div className="panel-actions"><button className="primary" disabled={busy||!formDirty}>{isMenu?(editId?'메뉴 변경 저장':'메뉴 추가'):(editId?'저장':'추가')}</button>{editId&&<button type="button" onClick={()=>choose()}>새 항목</button>}</div>
    </fieldset></form></div></section>;
}

type Group='basic'|'style'|'components'|'system';
const settingsConfig:Record<Group,{title:string;keys:string[];note:string}>={
  basic:{title:'기본 정보',keys:['siteName','description','contactEmail','homePageId'],note:'사이트 전체에 사용하는 기본 정보입니다.'},
  style:{title:'공통 스타일',keys:['primaryColor','headerColor','radius'],note:'홈페이지에 적용되는 색상과 모서리입니다. 관리자 화면 디자인과는 별개입니다.'},
  components:{title:'공통 영역·블록 안내',keys:['logoId','headerNote','footerText'],note:'페이지에서 사용할 수 있는 블록과 사이트 공통 영역을 확인합니다.'},
  system:{title:'시스템 설정',keys:['postsPerPage'],note:'홈페이지 콘텐츠 목록에 표시할 글 수를 설정합니다.'}
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
  return <section className="settings-workspace"><Heading title={config.title} note={config.note}/><p className="operation-note">입력 후 ‘변경사항 저장’을 눌러 적용하세요. 자동저장되지 않습니다.</p>
    {group==='components'&&<><h2>로고·상단·하단</h2><p className="muted">로고와 상단·하단 문구는 사이트 전체에 적용됩니다.</p></>}
    <Feedback loading={result.loading} error={error||result.error} message={message}/>{form&&<form className="card panel-pad settings-panel" onSubmit={e=>{e.preventDefault();void save();}}><fieldset disabled={busy}>
      {group==='basic'&&<><label>사이트 이름<input required maxLength={80} value={form.siteName} onChange={e=>field('siteName',e.target.value)}/></label><label>사이트 소개<textarea maxLength={500} rows={4} value={form.description} onChange={e=>field('description',e.target.value)}/></label><label>문의 이메일<input type="email" maxLength={254} value={form.contactEmail} onChange={e=>field('contactEmail',e.target.value)}/></label><label>첫 화면<select value={form.homePageId} onChange={e=>field('homePageId',e.target.value)}><option value="">글 목록</option>{data.pages.filter(p=>p.status==='PUBLISHED').map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></label></>}
      {group==='style'&&<><div className="color-fields"><label>강조색<input type="color" value={form.primaryColor} onChange={e=>field('primaryColor',e.target.value)}/><span>{form.primaryColor}</span></label><label>상단 배경색<input type="color" value={form.headerColor} onChange={e=>field('headerColor',e.target.value)}/><span>{form.headerColor}</span></label></div><label>모서리<select value={form.radius} onChange={e=>field('radius',e.target.value)}>{[['0','직각'],['8','조금 둥글게'],['12','기본'],['20','둥글게']].map(([v,label])=><option key={v} value={v}>{label}</option>)}</select></label></>}
      {group==='components'&&<><label>로고<select value={form.logoId} onChange={e=>field('logoId',e.target.value)}><option value="">사이트 이름 사용</option>{data.images.map(m=><option key={m.id} value={m.id}>{m.name}</option>)}</select></label>{form.logoId&&<img className="logo-preview" src={'/admin/media/'+form.logoId+'/file'} alt="선택한 로고"/>}<label>상단 문구<input maxLength={120} value={form.headerNote} onChange={e=>field('headerNote',e.target.value)}/></label><label>하단 문구<textarea rows={4} maxLength={500} value={form.footerText} onChange={e=>field('footerText',e.target.value)}/></label></>}
      {group==='system'&&<label>목록당 글 수<select value={form.postsPerPage} onChange={e=>field('postsPerPage',e.target.value)}>{['6','12','24'].map(v=><option key={v} value={v}>{v}개</option>)}</select></label>}
      <div className="panel-actions"><button className="primary" disabled={!dirty||busy}>변경사항 저장</button><button type="button" onClick={()=>void reloadSettings()}>다시 조회</button><span className="settings-save-state" role="status">{busy?'저장·조회 중…':dirty?'저장하지 않은 변경사항이 있습니다.':'저장된 설정'}</span></div>
    </fieldset></form>}{group==='components'&&<ComponentCatalog active={active} version={version}/>}</section>;
}

export function MediaPanel({active,version,refresh,data,registerGuard,go}:Props) {
  const [q,setQ]=useState(''),[term,setTerm]=useState('');const result=useRemote<ImageFile[]>('/media?q='+encodeURIComponent(q),active,version);
  const [selected,setSelected]=useState<ImageFile|null>(null),[form,setForm]=useState({name:'',alt:''}),[base,setBase]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[uses,setUses]=useState<Usage[]>([]);
  const [usageLoading,setUsageLoading]=useState(false),[usageError,setUsageError]=useState('');
  const dirty=selected!==null&&serialized(form)!==base;useUnsaved(dirty||busy);
  useEffect(()=>{if(!selected||!active)return;const controller=new AbortController();setUsageLoading(true);setUsageError('');void get<Usage[]>('/media/'+selected.id+'/usage',controller.signal).then(r=>{if(!controller.signal.aborted)setUses(r);}).catch(e=>{if(!controller.signal.aborted)setUsageError(messageOf(e));}).finally(()=>{if(!controller.signal.aborted)setUsageLoading(false);});return()=>controller.abort();},[selected?.id,active]);
  function choose(item:ImageFile){if(dirty&&!window.confirm('파일 정보 입력을 버리고 다른 파일을 열까요?'))return;setSelected(item);const next={name:item.name,alt:item.alt};setForm(next);setBase(serialized(next));setError('');if(selected?.id!==item.id)setUses([]);}
  async function upload(file?:File){if(!file)return;setBusy(true);setError('');setMessage('');try{if(file.size>5*1024*1024)throw new Error('파일은 5MB 이하로 선택하세요.');await uploadMedia(file);result.reload();refresh();setMessage('업로드했습니다.');}catch(e){setError(messageOf(e));}finally{setBusy(false);}}
  async function save(){if(!selected)return;setBusy(true);setError('');try{const saved=await send<ImageFile>('/media/'+selected.id,'PUT',form);setSelected(saved);setBase(serialized(form));setMessage('파일 정보를 저장했습니다.');result.reload();refresh();}catch(e){setError(messageOf(e));}finally{setBusy(false);}}
  async function remove(){if(!selected)return;setBusy(true);setError('');try{const currentUses=await get<Usage[]>('/media/'+selected.id+'/usage');setUses(currentUses);if(currentUses.length){setError('사용 중인 파일은 영구 삭제할 수 없습니다.');return;}if(!window.confirm(`${selected.name}: 현재 참조 없음. 파일을 영구 삭제하면 복구할 수 없습니다. 계속할까요?`))return;await send('/media/'+selected.id,'DELETE');setSelected(null);setMessage('삭제했습니다.');result.reload();refresh();}catch(e){setError(messageOf(e));try{setUses(await get<Usage[]>('/media/'+selected.id+'/usage'));}catch{/* The original error remains visible. */}}finally{setBusy(false);}}
  const deletion=useBulkDelete({items:result.data||[],registerGuard,guardPath:"/media",active,scope:q,allowed:!!data.permissions.permanentDelete,disabled:busy||dirty||result.loading||!!result.error,label:item=>item.name,permanent:true,
    prepare:async item=>{const references=await get<Usage[]>('/media/'+item.id+'/usage');if(references.length)throw new Error('사용 중인 파일: '+references.map(usage=>usage.label).join(', '));return {id:item.id,label:item.name};},
    remove:target=>send('/media/'+target.id,'DELETE'),onDone:ids=>{if(selected&&ids.includes(selected.id))setSelected(null);result.reload();refresh();},
    description:'선택한 미디어 파일을 영구삭제합니다. 복구할 수 없습니다. 콘텐츠·페이지·버전 이력에서 사용 중인 파일은 제외됩니다.'});
  return <section className="media-workspace operations-workspace"><Heading title="미디어 관리" note="파일을 찾아 선택한 뒤 이름·대체 텍스트·사용 위치를 확인하세요." actions={<label className={'upload-button'+(busy?' disabled':'')}>＋ 파일 업로드<input type="file" disabled={busy} accept=".jpg,.jpeg,.png,.pdf,.txt,.docx,.xlsx,.pptx,.hwp" onChange={e=>{void upload(e.target.files?.[0]);e.target.value='';}}/></label>}/><Feedback loading={result.loading||busy} error={error||result.error} message={message}/><div className="management-split"><section className="card"><form className="search-bar" onSubmit={e=>{e.preventDefault();setQ(term);}}><input aria-label="미디어 검색" value={term} maxLength={100} onChange={e=>setTerm(e.target.value)} placeholder="파일 이름 검색"/><button>검색</button><button type="button" onClick={result.reload}>새로고침</button></form><div className="media-list-caption">{data.permissions.permanentDelete&&<label className="media-select-all">{deletion.selectAll}<span>전체 선택</span></label>}<p className="operation-note media-upload-note">파일당 최대 5MB · JPG, PNG, PDF, TXT, DOCX, XLSX, PPTX, HWP</p>{deletion.action}</div>{deletion.feedback}{deletion.dialog}{dirty&&<p className="operation-note">선택 삭제 전에 파일 정보 변경을 저장하세요.</p>}<div className="media-grid">{result.data?.map(item=><div className="media-select-card" key={item.id}><div className="media-select-actions">{deletion.checkbox(item)}{deletion.rowButton(item)}</div><button className={'media-tile'+(selected?.id===item.id?' selected':'')} aria-pressed={selected?.id===item.id} disabled={busy} onClick={()=>choose(item)}>{item.mime.startsWith('image/')?<img src={'/admin/media/'+item.id+'/file'} alt={item.alt||item.name}/>:<span className="file-icon">문서</span>}<strong>{item.name}</strong><small>{item.ownerName} · {Math.ceil((item.byteSize||0)/1024)} KB</small></button></div>)}</div>{!result.loading&&!result.error&&result.data?.length===0&&<Empty>{q?'검색 결과가 없습니다. 파일 이름을 바꿔 검색하세요.':'등록된 파일이 없습니다. 파일 업로드로 시작하세요.'}</Empty>}</section><aside className="card panel-pad side-form"><h2>파일 정보</h2>{selected?<form onSubmit={e=>{e.preventDefault();void save();}}><fieldset disabled={busy}><label>파일 이름<input required maxLength={200} value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></label><label>대체 텍스트<textarea maxLength={300} rows={3} value={form.alt} onChange={e=>setForm({...form,alt:e.target.value})}/></label><p className="operation-note">대체 텍스트는 이미지를 볼 수 없을 때 내용을 설명합니다.</p><dl className="file-facts"><dt>등록자</dt><dd>{selected.ownerName}</dd><dt>파일 크기</dt><dd>{Math.ceil((selected.byteSize||0)/1024)} KB</dd><dt>등록일</dt><dd>{date(selected.createdAt||'')}</dd></dl><a href={'/admin/media/'+selected.id+'/file'} target="_blank" rel="noopener">파일 열기 ↗</a><div className="panel-actions"><button className="primary" disabled={!dirty||busy}>파일 정보 저장</button>{data.permissions.permanentDelete&&<button type="button" className="danger-link" onClick={()=>void remove()}>영구 삭제</button>}</div></fieldset></form>:<p className="muted">목록에서 파일을 선택하세요.</p>}{selected&&<div className="usage-list"><h3>사용 위치</h3>{usageLoading?<p role="status">사용 위치 확인 중…</p>:usageError?<p role="alert" className="error-box">{usageError}</p>:!uses.length?<p className="operation-note">현재 확인된 사용 위치가 없습니다. 삭제 시 사용 여부를 다시 확인합니다.</p>:<p className="operation-note">사용 중인 파일은 삭제할 수 없습니다.</p>}{uses.map((u,i)=>{const route=u.href?usageRoute(u.href):null;return route?<button type="button" className="text-link usage-link" key={i} onClick={()=>go(route)}>{u.label}</button>:<p key={i}>{u.label}</p>;})}</div>}</aside></div></section>;
}
