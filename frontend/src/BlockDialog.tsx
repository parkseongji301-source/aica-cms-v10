import {useEffect,useRef} from 'react';
import type {ReactNode} from 'react';

export function BlockDialog({title,onClose,children,active=true}:{title:string;onClose:()=>void;children:ReactNode;active?:boolean}) {
 const dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{const node=dialog.current!;if(active)node.showModal();else node.close();return()=>node.close();},[active]);
 return <dialog ref={dialog} className="block-dialog" aria-label={title} onCancel={e=>{e.preventDefault();onClose();}}>
  <header><h2>{title}</h2><button type="button" aria-label="닫기" onClick={onClose}>×</button></header>{children}
 </dialog>;
}
