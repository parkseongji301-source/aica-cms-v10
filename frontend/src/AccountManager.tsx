import {useEffect,useRef,useState} from 'react';
import type {FormEvent} from 'react';
import {BlockDialog} from './BlockDialog';
import {send} from './api';
import type {EditorGuard} from './editorGuard';
import type {AccountRow,Bootstrap} from './types';
import {date,Feedback,Heading,messageOf,useRemote} from './ui';

type RoleOption={code:string;label:string};
type Issued={accountId:number;email:string;temporaryPassword:string};
type Action={kind:'role'|'reset'|'deactivate';account:AccountRow};
const ROLES:RoleOption[]=[{code:'SUPER_ADMIN',label:'최상위 관리자'},{code:'ADMIN',label:'관리자'},{code:'SUPPORTER',label:'서포터즈'}];

/**
 * Account issue, role change, deactivation and password reset through AccountService.
 * A temporary password lives only in component state for the one dialog that shows it.
 */
export function AccountsPanel({active,version,data,registerGuard}:{active:boolean;version:number;data:Bootstrap;registerGuard?:(path:string,guard:EditorGuard|null)=>void}) {
  const [revision,setRevision]=useState(0);
  const result=useRemote<AccountRow[]>('/accounts',active,version+revision);
  const creatable=useRemote<RoleOption[]>('/accounts/creatable-roles',active);
  const [creating,setCreating]=useState(false),[form,setForm]=useState({email:'',displayName:'',role:''});
  const [action,setAction]=useState<Action|null>(null),[nextRole,setNextRole]=useState('');
  const [issued,setIssued]=useState<Issued|null>(null),[copied,setCopied]=useState(false);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const pending=useRef(false),live=useRef({busy:false,dirty:false});
  live.current={busy,dirty:!!issued||creating&&!!(form.email||form.displayName)};
  // Leaving while a temporary password is on screen would lose it, so navigation asks first.
  useEffect(()=>{registerGuard?.('/accounts',()=>live.current.busy?'busy':!live.current.dirty);return()=>registerGuard?.('/accounts',null);},[registerGuard]);
  useEffect(()=>{if(!active){setCreating(false);setAction(null);}},[active]);
  const refresh=()=>setRevision(n=>n+1);
  async function run<T>(work:()=>Promise<T>,done:(value:T)=>void){
    if(pending.current)return;pending.current=true;setBusy(true);setError('');setNotice('');
    try{done(await work());}catch(e){setError(messageOf(e));}finally{pending.current=false;setBusy(false);refresh();}
  }
  function issue(event:FormEvent){
    event.preventDefault();
    void run(()=>send<Issued>('/accounts','POST',form),value=>{setCreating(false);setForm({email:'',displayName:'',role:''});setCopied(false);setIssued(value);});
  }
  function confirm(){
    if(!action)return;const {kind,account}=action;
    if(kind==='role')void run(()=>send<AccountRow[]>(`/accounts/${account.id}/role`,'PUT',{role:nextRole}),()=>{setAction(null);setNotice(`${account.displayName} 계정의 역할을 바꿨습니다. 해당 계정은 다시 로그인해야 합니다.`);});
    if(kind==='deactivate')void run(()=>send<AccountRow[]>(`/accounts/${account.id}/deactivate`,'POST',{}),()=>{setAction(null);setNotice(`${account.displayName} 계정을 사용 중지했습니다.`);});
    if(kind==='reset')void run(()=>send<Issued>(`/accounts/${account.id}/reset-password`,'POST',{}),value=>{setAction(null);setCopied(false);setIssued(value);});
  }
  async function copy(){
    if(!issued)return;
    try{await navigator.clipboard.writeText(issued.temporaryPassword);setCopied(true);}catch{setError('복사하지 못했습니다. 비밀번호를 직접 선택해 복사하세요.');}
  }
  const roleOptions=creatable.data??[];
  return <section className="accounts-workspace admin-table-workspace">
    <Heading title="운영 계정 관리" note="계정 발급·역할 변경·비밀번호 초기화·사용 중지를 처리합니다. 역할 변경·초기화·사용 중지된 계정은 다시 로그인해야 합니다." actions={<button type="button" className="primary" disabled={busy} onClick={()=>{setError('');setCreating(true);}}>＋ 계정 발급</button>}/>
    <Feedback {...result} error={error||result.error} message={notice}/>
    <section className="card"><header className="panel-header"><h2>운영 계정</h2><div className="heading-actions"><span>{result.data?.length??0}개</span><button type="button" disabled={busy} onClick={refresh}>새로고침</button></div></header>
      <div className="table-scroll"><table className="data-table"><thead><tr><th>이름 / 아이디</th><th>역할</th><th>상태</th><th>발급일</th><th>작업</th></tr></thead><tbody>{result.data?.map(a=><tr key={a.id}>
        <td><strong>{a.displayName}</strong>{a.id===data.user.id&&<span className="scope-tag">나</span>}<small className="row-meta">{a.email}</small></td>
        <td>{a.roleLabel}</td>
        <td><span className={'account-state '+(a.active?'enabled':'disabled')}>{a.active?'사용 중':'사용 중지'}</span>{a.passwordChangeRequired&&<small className="row-meta">비밀번호 변경 필요</small>}</td>
        <td>{date(a.createdAt)}</td>
        <td>{a.id===data.user.id?<small className="muted">본인 계정은 변경할 수 없습니다</small>:a.active?<div className="page-row-actions">
          <button type="button" disabled={busy} onClick={()=>{setError('');setNextRole(a.role);setAction({kind:'role',account:a});}}>역할 변경</button>
          <button type="button" disabled={busy} onClick={()=>{setError('');setAction({kind:'reset',account:a});}}>비밀번호 초기화</button>
          <button type="button" className="danger-link" disabled={busy} onClick={()=>{setError('');setAction({kind:'deactivate',account:a});}}>사용 중지</button>
        </div>:<small className="muted">사용 중지된 계정</small>}</td>
      </tr>)}</tbody></table></div>
    </section>
    {creating&&<BlockDialog active={active} title="계정 발급" onClose={()=>{if(!pending.current)setCreating(false);}}>
      <form className="account-form" onSubmit={issue}><fieldset disabled={busy}>
        <label>아이디(이메일)<input type="email" required maxLength={120} value={form.email} onChange={e=>setForm({...form,email:e.target.value})} autoComplete="off"/></label>
        <label>이름<input required maxLength={80} value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})} autoComplete="off"/></label>
        <label>역할<select required value={form.role} onChange={e=>setForm({...form,role:e.target.value})}><option value="">선택하세요</option>{roleOptions.map(r=><option key={r.code} value={r.code}>{r.label}</option>)}</select></label>
        <p className="muted">새 계정은 관리자 또는 서포터즈로만 발급합니다. 발급하면 임시 비밀번호가 한 번만 표시되고, 첫 로그인 때 비밀번호를 바꿔야 합니다.</p>
        <Feedback error={error||creatable.error}/>
        <div className="dialog-actions"><button type="button" onClick={()=>setCreating(false)}>취소</button><button type="submit" className="primary" disabled={!form.email.trim()||!form.displayName.trim()||!form.role}>{busy?'발급 중…':'발급'}</button></div>
      </fieldset></form>
    </BlockDialog>}
    {action&&<BlockDialog active={active} title={action.kind==='role'?'역할 변경':action.kind==='reset'?'비밀번호 초기화':'계정 사용 중지'} onClose={()=>{if(!pending.current)setAction(null);}}>
      <p><strong>{action.account.displayName}</strong> <small className="muted">{action.account.email}</small></p>
      {action.kind==='role'&&<label>새 역할<select value={nextRole} onChange={e=>setNextRole(e.target.value)}>{ROLES.map(r=><option key={r.code} value={r.code}>{r.label}</option>)}</select></label>}
      <p>{action.kind==='role'?'역할을 바꾸면 해당 계정은 다시 로그인해야 합니다. 마지막 최상위 관리자는 다른 역할로 바꿀 수 없습니다.':action.kind==='reset'?'기존 비밀번호를 쓸 수 없게 되고 새 임시 비밀번호를 발급합니다. 해당 계정은 다시 로그인한 뒤 비밀번호를 바꿔야 합니다.':'이 계정으로 더 이상 로그인할 수 없습니다. 작성한 콘텐츠는 그대로 남습니다. 다시 사용하게 하는 기능은 없습니다.'}</p>
      <Feedback error={error}/>
      <div className="dialog-actions"><button type="button" disabled={busy} onClick={()=>setAction(null)}>취소</button><button type="button" className={action.kind==='deactivate'?'danger':'primary'} disabled={busy||action.kind==='role'&&nextRole===action.account.role} onClick={confirm}>{busy?'처리 중…':action.kind==='role'?'역할 변경':action.kind==='reset'?'초기화하고 임시 비밀번호 받기':'사용 중지'}</button></div>
    </BlockDialog>}
    {issued&&<BlockDialog active={active} title="임시 비밀번호" onClose={()=>setIssued(null)}>
      <p><strong>{issued.email}</strong></p>
      <p className="temporary-password"><code aria-label="임시 비밀번호">{issued.temporaryPassword}</code><button type="button" onClick={()=>void copy()}>{copied?'복사됨':'복사'}</button></p>
      <p role="alert">이 창을 닫으면 다시 볼 수 없습니다. 계정 사용자에게 안전한 방법으로 전달하세요. 첫 로그인 때 비밀번호를 바꿔야 합니다.</p>
      <div className="dialog-actions"><button type="button" className="primary" onClick={()=>setIssued(null)}>전달했습니다 · 닫기</button></div>
    </BlockDialog>}
  </section>;
}
