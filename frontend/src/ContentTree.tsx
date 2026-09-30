import type {ClassificationCatalog,ContentArea,Go} from './types';
import {contentNodes,sectionPath} from './contentNavigation';
import {NavigationIcon} from './NavigationIcon';
import {pagePath} from './navigation';

/** One representative work area: ancestor titles as headings, the area, then the sub-navigation the operator saved for it (V16). */
export function ContentTree({area,areas,catalog,selected,go,error}:{area:ContentArea;areas:ContentArea[];catalog:ClassificationCatalog|null;selected:string|null;go:Go;error:string}) {
  const nodes=contentNodes(area),children=nodes.filter(node=>node.key!=='all');
  // A page without a content type is a plain entry: it opens the page itself (V17).
  const parentPath=area.typeCode?sectionPath(area,'all',catalog,areas):pagePath(area.pageId);
  const [heading,...groups]=area.groups;
  return <div className="review-navigation">{heading&&<h2>{heading}</h2>}
    {groups.length>0&&<p className="structure-group">{groups.join(' › ')}</p>}
    <ul className={'structure-tree review-tree'+(groups.length?' nested-tree':'')}><li className={'tree-parent'+(selected&&selected!=='all'?' has-selected-child':'')}>
      <button disabled={!parentPath} className={selected==='all'?'selected':''} aria-current={selected==='all'?'page':undefined} onClick={()=>parentPath&&go(parentPath)}><NavigationIcon name="content"/><span className="nav-item-copy"><span>{area.label}</span>{!parentPath&&<small className="nav-unavailable">{catalog?'글 종류 확인 필요':'불러오는 중'}</small>}</span></button>
      {children.length>0&&<ul className="review-children" aria-label={`${area.label} 하위 항목`}>{children.map(node=>{
      const path=sectionPath(area,node.key,catalog,areas);
      return <li key={node.key}><button disabled={!path} className={selected===node.key?'selected':''} aria-current={selected===node.key?'page':undefined} onClick={()=>path&&go(path)}>
        <span className="nav-item-copy"><span>{node.label}</span>{!path&&<small className="nav-unavailable">{catalog?'주제 확인 필요':'불러오는 중'}</small>}</span>
      </button></li>;
    })}</ul>}</li></ul>{error&&<p className="nav-note" role="alert">{error}</p>}
  </div>;
}
