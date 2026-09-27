import type {Go,PageTarget} from './types';
import {pagePath} from './navigation';

export function PageStructureBranch({id,label,hidden=false,outline,activePage,activeBlock,go}:{id:number;label:string;hidden?:boolean;outline?:PageTarget;activePage:number|null;activeBlock:string|null;go:Go}) {
 return <li className="page-branch"><button data-page-id={id} className={activePage===id?'selected':''} onClick={()=>go(pagePath(id))}><span className="tree-line">└</span><span>{label}{hidden&&<small>메뉴 숨김</small>}</span></button>
  {outline?.issue&&<p className="nav-note">{outline.issue}</p>}
  {outline&&outline.blocks.length>0&&<ul className="block-tree">{outline.blocks.map((block,index)=><li key={block.blockId??`unlinked-${index}`}><button data-page-id={id} data-block-id={block.blockId??undefined} disabled={!block.blockId} className={block.blockId!==null&&activePage===id&&activeBlock===block.blockId?'selected':''} aria-current={block.blockId!==null&&activePage===id&&activeBlock===block.blockId?'location':undefined} aria-label={'블록: '+block.label+' · '+block.type+(block.visible?'':' · 숨김')+(block.blockId?'':' · ID 없음')} onClick={()=>{if(block.blockId)go(pagePath(id,block.blockId));}}><span>{block.label}<small>{block.type}{!block.visible?' · 숨김':''}{!block.blockId?' · ID 없음':''}</small></span></button></li>)}</ul>}
 </li>;
}
