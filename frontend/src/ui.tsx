import {useEffect,useRef,useState} from 'react';
import type {ReactNode} from 'react';
import {get} from './api';

export function useRemote<T>(path:string,active:boolean,version=0) {
  const [data,setData]=useState<T|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(false),[retry,setRetry]=useState(0);
  useEffect(()=>{
    if(!active)return;
    const controller=new AbortController();setLoading(true);setError('');
    void get<T>(path,controller.signal).then(result=>{if(!controller.signal.aborted)setData(result);})
      .catch(e=>{if(!controller.signal.aborted)setError(e.message);})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    return()=>controller.abort();
  },[path,active,version,retry]);
  return {data,error,loading,reload:()=>setRetry(n=>n+1)};
}
export function useUnsaved(dirty:boolean) {
  const current=useRef(dirty);current.current=dirty;
  useEffect(()=>{const handler=(e:BeforeUnloadEvent)=>{if(current.current){e.preventDefault();e.returnValue='';}};
    window.addEventListener('beforeunload',handler);return()=>window.removeEventListener('beforeunload',handler);},[]);
}
export function Heading({title,note,actions,badge}:{title:string;note?:string;actions?:ReactNode;badge?:string}) {
  return <div className="editor-heading"><div><h1>{title} {badge&&<span className="scope-tag">{badge}</span>}</h1>{note&&<p className="target-caption">{note}</p>}</div><div className="heading-actions">{actions}</div></div>;
}
export function Feedback({error,loading,message,reload}:{error?:string;loading?:boolean;message?:string;reload?:()=>void}) {
  return <>{loading&&<p className="loading-line" role="status">불러오는 중…</p>}{error&&<div className="error-box" role="alert"><strong>처리하지 못했습니다.</strong><p>{error}</p>{reload&&<button disabled={loading} onClick={reload}>다시 불러오기</button>}</div>}{message&&<p className="success-line" role="status">{message}</p>}</>;
}
export function Empty({children='등록된 항목이 없습니다.'}:{children?:ReactNode}) {return <div className="empty-state">{children}</div>;}
export function Status({value,pending=false,pageWording=false}:{value:string;pending?:boolean;pageWording?:boolean}) {
  return <span className="table-status"><span className={'status-tag state-'+value.toLowerCase()}>{(pageWording?({DRAFT:'임시보관',PUBLISHED:'게시됨',PRIVATE:'비공개'} as Record<string,string>):({DRAFT:'임시저장',PUBLISHED:'발행',PRIVATE:'비공개'} as Record<string,string>))[value]||value}</span>{pending&&<small>{pageWording?'미게시 수정 있음':'미반영 수정'}</small>}</span>;
}
export function Pager({page,total,size,onChange}:{page:number;total:number;size:number;onChange:(page:number)=>void}) {
  const pages=Math.max(1,Math.ceil(total/size));return <div className="pagination"><span>{total}개 · {page+1} / {pages}</span><button disabled={page<=0} onClick={()=>onChange(page-1)}>이전</button><button disabled={page+1>=pages} onClick={()=>onChange(page+1)}>다음</button></div>;
}
let operatingZone='Asia/Seoul';
export const setOperatingZone=(zone?:string)=>{if(zone)operatingZone=zone;};
export const savedTime=()=>new Date().toLocaleTimeString('ko-KR',{timeZone:operatingZone});
export const date=(value:string)=>{if(!value)return '—';if(!/(Z|[+-]\d{2}:\d{2})$/.test(value))return value.replace('T',' ').slice(0,16);return new Intl.DateTimeFormat('ko-KR',{timeZone:operatingZone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value));};
export const messageOf=(error:unknown)=>error instanceof Error?error.message:'요청을 처리하지 못했습니다.';
