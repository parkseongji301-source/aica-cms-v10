import type {ClassificationCatalog,Go} from './types';
import {contentSections,sectionPath} from './contentNavigation';
import type {SectionType} from './contentNavigation';

export function ContentTree({type,catalog,selected,go,error}:{type:SectionType;catalog:ClassificationCatalog|null;selected:string|null;go:Go;error:string}) {
  const section=contentSections[type];
  return <div className="review-navigation"><h2>{section.parent}</h2>
    {'group' in section&&<p className="structure-group">{section.group}</p>}
    <ul className={'structure-tree review-tree'+('group' in section?' nested-tree':'')}>{section.nodes.map(node=>{
      const path=sectionPath(type,node.key,catalog);
      return <li key={node.key} className={node.key==='all'?'':'review-child'}><button disabled={!path} className={selected===node.key?'selected':''} aria-current={selected===node.key?'page':undefined} onClick={()=>path&&go(path)}>
        <span className="tree-line">└</span><span>{node.label}{!path&&<small>사전 연결 전</small>}</span>
      </button></li>;
    })}</ul>{error&&<p className="nav-note" role="alert">{error}</p>}
  </div>;
}
