import type {Go,PageTarget} from './types';
import {pageOverviewPath} from './navigation';
import {NavigationIcon} from './NavigationIcon';

export function PageStructureBranch({id,label,hidden=false,outline,activePage,activeBlock,go}:{id:number;label:string;hidden?:boolean;outline?:PageTarget;activePage:number|null;activeBlock:string|null;go:Go}) {
 return <li className="page-branch"><button data-page-id={id} className={activePage===id?'selected':''} aria-current={activePage===id&&!activeBlock?'page':undefined} onClick={()=>go(pageOverviewPath(id))}><NavigationIcon name="page"/><span className="nav-item-copy"><span>{label}</span>{hidden&&<small>메뉴 숨김</small>}</span></button>
  {outline?.issue&&<p className="nav-note">{outline.issue}</p>}
  {outline&&outline.blocks.length>0&&<ul className="block-tree">{outline.blocks.map((block,index)=><li key={block.blockId??`unlinked-${index}`}><button data-page-id={id} data-block-id={block.blockId??undefined} disabled={!block.blockId} className={block.blockId!==null&&activePage===id&&activeBlock===block.blockId?'selected':''} aria-current={block.blockId!==null&&activePage===id&&activeBlock===block.blockId?'location':undefined} aria-label={'블록: '+block.label+' · '+block.type+(block.visible?'':' · 숨김')+(block.blockId?'':' · ID 없음')} onClick={()=>{if(block.blockId)go(pageOverviewPath(id,block.blockId));}}><span className="nav-block-copy"><span className="nav-block-title" title={block.label}>{block.label}</span><small>{block.type}{!block.visible?' · 숨김':''}{!block.blockId?' · ID 없음':''}</small></span></button></li>)}</ul>}
 </li>;
}
