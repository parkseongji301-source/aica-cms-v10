import {useEffect,useRef,useState} from 'react';
import {BlockDialog} from './BlockDialog';
import {Feedback,messageOf,useUnsaved} from './ui';
import {deleteSequentially} from './deleteBatch';
import type {BatchFailure,DeleteTarget} from './deleteBatch';
import type {EditorGuard} from './editorGuard';
import './bulk-delete.css';

export function SelectionBox({checked,mixed=false,label,disabled=false,onChange}:{checked:boolean;mixed?:boolean;label:string;disabled?:boolean;onChange:()=>void}) {
  const input=useRef<HTMLInputElement>(null);
  useEffect(()=>{if(input.current)input.current.indeterminate=mixed;},[mixed]);
  return <input ref={input} className="bulk-checkbox" type="checkbox" aria-label={label} checked={checked} disabled={disabled} onChange={onChange}/>;
}

export function useBulkDelete<T extends {id:number}>({items,active,scope,allowed,disabled=false,label,prepare,remove,onDone,description,permanent=false,registerGuard,guardPath}:{
  items:T[];active:boolean;scope:string;allowed:boolean;disabled?:boolean;label:(item:T)=>string;
  prepare:(item:T)=>Promise<DeleteTarget>;remove:(target:DeleteTarget)=>Promise<unknown>;onDone:(ids:number[])=>void;
  description:string;permanent?:boolean;
  registerGuard?:(path:string,guard:EditorGuard|null)=>void;guardPath?:string;
}) {
  const [ids,setIds]=useState<number[]>([]),[targets,setTargets]=useState<DeleteTarget[]|null>(null);
  const [busy,setBusy]=useState(false),[preparing,setPreparing]=useState(false),[confirmed,setConfirmed]=useState(false);
  const [failures,setFailures]=useState<BatchFailure[]>([]),[message,setMessage]=useState(''),[progress,setProgress]=useState(0);
  const lock=useRef(false);
  useUnsaved(busy);
  useEffect(()=>{if(!registerGuard||!guardPath)return;registerGuard(guardPath,()=>lock.current?'busy':true);return()=>registerGuard(guardPath,null);},[registerGuard,guardPath]);
  useEffect(()=>{if(!active&&!lock.current)setTargets(null);},[active]);
  const itemsKey=JSON.stringify(items);
  useEffect(()=>{setIds([]);},[itemsKey,active,scope]);
  const selected=items.filter(item=>ids.includes(item.id));
  const blocked=disabled||busy||!allowed||!active;
  function toggle(id:number){if(!blocked)setIds(old=>old.includes(id)?old.filter(value=>value!==id):[...old,id]);}
  function toggleAll(){if(!blocked)setIds(selected.length===items.length?[]:items.map(item=>item.id));}
  async function choose(chosen:T[]){
    if(blocked||lock.current||!chosen.length)return;
    lock.current=true;setBusy(true);setPreparing(true);setTargets([]);setFailures([]);setMessage('');setConfirmed(false);setProgress(0);
    const ready:DeleteTarget[]=[],errors:BatchFailure[]=[];
    try{
      for(const item of chosen){
        try{ready.push(await prepare(item));}
        catch(error){errors.push({id:item.id,label:label(item),message:messageOf(error)});}
      }
      setTargets(ready);setFailures(errors);
    }finally{lock.current=false;setBusy(false);setPreparing(false);}
  }
  async function execute(){
    if(lock.current||!targets?.length||permanent&&!confirmed)return;
    lock.current=true;setBusy(true);setProgress(0);
    try{
      const result=await deleteSequentially(targets,remove,setProgress);
      setFailures(old=>[...old,...result.failed]);setTargets(null);setIds([]);
      setMessage(`${result.succeeded.length}개 ${permanent?'삭제':'휴지통 이동'} 완료${result.failed.length?' · '+result.failed.length+'개 미완료':''}`);
      onDone(result.succeeded);
    }finally{lock.current=false;setBusy(false);}
  }
  const all=items.length>0&&selected.length===items.length;
  const checkbox=(item:T)=>allowed?<SelectionBox checked={ids.includes(item.id)} label={label(item)+' 선택'} disabled={blocked} onChange={()=>toggle(item.id)}/>:null;
  const selectAll=allowed?<SelectionBox checked={all} mixed={selected.length>0&&!all} label="현재 목록 전체 선택" disabled={blocked||!items.length} onChange={toggleAll}/>:null;
  const rowButton=(item:T)=>allowed?<button type="button" className="danger-link" disabled={blocked} aria-label={label(item)+' 삭제'} onClick={()=>void choose([item])}>삭제</button>:null;
  const action=allowed?<button type="button" className="danger-link bulk-delete-action" disabled={blocked||!selected.length} onClick={()=>void choose(selected)}>{permanent?'선택 영구삭제':'선택 삭제'}{selected.length>0?` (${selected.length})`:''}</button>:null;
  const feedback=<><Feedback message={message}/>{targets===null&&failures.length>0&&<div className="error-box" role="alert"><strong>{failures.length}개 항목을 처리하지 못했습니다. 목록을 확인하고 다시 선택하세요.</strong><ul>{failures.map(item=><li key={item.id}>{item.label}: {item.message}</li>)}</ul></div>}</>;
  const dialog=targets!==null?<BlockDialog active={active} title={permanent?'선택 항목 영구삭제':'선택 콘텐츠 삭제'} onClose={()=>{if(!lock.current)setTargets(null);}}>
    <p>{description}</p>{preparing?<p role="status">삭제할 항목을 확인하고 있습니다…</p>:<><p><strong>{targets.length}개</strong>를 {permanent?'영구삭제':'휴지통으로 이동'}합니다.</p><ul className="bulk-targets">{targets.map(target=><li key={target.id}>{target.label}{target.details&&target.details.length>0&&<ul className="bulk-target-details">{target.details.map((line,i)=><li key={i}>{line}</li>)}</ul>}</li>)}</ul></>}
    {failures.length>0&&<div className="error-box"><strong>다음 항목은 제외됩니다.</strong><ul>{failures.map(item=><li key={item.id}>{item.label}: {item.message}</li>)}</ul></div>}
    {permanent&&targets.length>0&&<label className="check-inline"><input type="checkbox" disabled={busy} checked={confirmed} onChange={event=>setConfirmed(event.target.checked)}/>복구할 수 없음을 확인했습니다.</label>}
    {busy&&!preparing&&<p role="status">{targets.length}개 중 {progress}개 처리 중…</p>}
    <div className="dialog-actions"><button type="button" disabled={busy} onClick={()=>setTargets(null)}>취소</button><button type="button" className="danger" disabled={busy||!targets.length||permanent&&!confirmed} onClick={()=>void execute()}>{busy?'처리 중…':permanent?'영구삭제':'휴지통으로 이동'}</button></div>
  </BlockDialog>:null;
  return {checkbox,selectAll,rowButton,action,feedback,dialog,busy};
}
