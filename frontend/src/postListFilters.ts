import type {ClassificationCatalog,ContentArea} from './types';
import {contentContext,filterValues} from './contentNavigation.ts';

// List controls only: the stored classification and editor validation are unchanged.
export function postFilterTopics(catalog:ClassificationCatalog,typeCodes:string[]) {
  if(!typeCodes.length)return catalog.topics;
  const allowed=new Set(catalog.allowedTopics.filter(t=>typeCodes.includes(t.typeCode)).map(t=>t.topicId));
  return catalog.topics.filter(t=>allowed.has(t.id));
}

// The choices use saved work-item names and order; the dictionary only validates their connections.
export function postFilterWorkTopics(catalog:ClassificationCatalog,areas:ContentArea[],typeCodes:string[]=[]) {
  return areas.filter(area=>area.typeCode&&(!typeCodes.length||typeCodes.includes(area.typeCode))).flatMap(area=>
    area.nodes.filter(node=>node.topicId!==null).map(node=>({
      id:node.id,name:node.name,topicId:node.topicId!,typeCode:area.typeCode!,pageId:area.pageId,areaLabel:area.label,groups:area.groups,
      available:!contentContext(new URLSearchParams({area:String(area.pageId),node:String(node.id)}),catalog,areas)?.error
    })));
}

export function postTypeFilterChange(params:URLSearchParams,typeCodes:string[],catalog:ClassificationCatalog,areas:ContentArea[]=[]) {
  const allowed=new Set(postFilterTopics(catalog,typeCodes).map(t=>String(t.id)));
  const work=new Set(postFilterWorkTopics(catalog,areas,typeCodes).filter(t=>t.available).map(t=>String(t.id)));
  return {typeCodes:typeCodes.join(','),topicIds:filterValues(params,'topicIds').filter(id=>allowed.has(id)).join(','),
    workNodeIds:filterValues(params,'workNodeIds').filter(id=>work.has(id)).join(',')};
}
