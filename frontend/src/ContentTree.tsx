import type {ClassificationCatalog,Go} from './types';
import {contentSections,sectionPath} from './contentNavigation';
import type {SectionType} from './contentNavigation';
import {NavigationIcon} from './NavigationIcon';

export function ContentTree({type,catalog,selected,go,error}:{type:SectionType;catalog:ClassificationCatalog|null;selected:string|null;go:Go;error:string}) {
  const section=contentSections[type];
  const parentPath=sectionPath(type,'all',catalog);
  return <div className="review-navigation"><h2>{section.parent}</h2>
    {'group' in section&&<p className="structure-group">{section.group}</p>}
    <ul className={'structure-tree review-tree'+('group' in section?' nested-tree':'')}><li className={'tree-parent'+(selected&&selected!=='all'?' has-selected-child':'')}>
      <button disabled={!parentPath} className={selected==='all'?'selected':''} aria-current={selected==='all'?'page':undefined} onClick={()=>parentPath&&go(parentPath)}><NavigationIcon name="content"/><span className="nav-item-copy"><span>{section.label}</span>{!parentPath&&<small className="nav-unavailable">{catalog?'분류 연결 필요':'불러오는 중'}</small>}</span></button>
      {section.nodes.length>1&&<ul className="review-children">{section.nodes.filter(node=>node.key!=='all').map(node=>{
      const path=sectionPath(type,node.key,catalog);
      return <li key={node.key}><button disabled={!path} className={selected===node.key?'selected':''} aria-current={selected===node.key?'page':undefined} onClick={()=>path&&go(path)}>
        <span className="nav-item-copy"><span>{node.label}</span>{!path&&<small className="nav-unavailable">{catalog?'분류 연결 필요':'불러오는 중'}</small>}</span>
      </button></li>;
    })}</ul>}</li></ul>{error&&<p className="nav-note" role="alert">{error}</p>}
  </div>;
}
