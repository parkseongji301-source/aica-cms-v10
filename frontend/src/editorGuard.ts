import {useEffect,useRef,useState} from 'react';
export function useDocumentVisible(){
 const [visible,setVisible]=useState(!document.hidden);
 useEffect(()=>{const changed=()=>setVisible(!document.hidden);document.addEventListener('visibilitychange',changed);return()=>document.removeEventListener('visibilitychange',changed);},[]);
 return visible;
}
export type EditorGuard=()=>boolean|'busy';
export type GuardRegistration=(guard:EditorGuard|null)=>void;
/** Metadata dialogs share the same navigation guard as document editors. */
export function useEditorGuard(dirty:boolean,busy:boolean,onGuard?:GuardRegistration){
 const live=useRef({dirty,busy});live.current={dirty,busy};
 useEffect(()=>{onGuard?.(()=>live.current.busy?'busy':!live.current.dirty);return()=>onGuard?.(null);},[onGuard]);
 useEffect(()=>{const leave=(e:BeforeUnloadEvent)=>{if(live.current.dirty||live.current.busy){e.preventDefault();e.returnValue='';}};window.addEventListener('beforeunload',leave);return()=>window.removeEventListener('beforeunload',leave);},[]);
}
