import type {ClassificationCatalog} from './types';
import {filterValues} from './contentNavigation.ts';

// List controls only: the stored classification and editor validation are unchanged.
export function postFilterTopics(catalog:ClassificationCatalog,typeCodes:string[]) {
  if(!typeCodes.length)return catalog.topics;
  const allowed=new Set(catalog.allowedTopics.filter(t=>typeCodes.includes(t.typeCode)).map(t=>t.topicId));
  return catalog.topics.filter(t=>allowed.has(t.id));
}

export function postTypeFilterChange(params:URLSearchParams,typeCodes:string[],catalog:ClassificationCatalog) {
  const allowed=new Set(postFilterTopics(catalog,typeCodes).map(t=>String(t.id)));
  return {typeCodes:typeCodes.join(','),topicIds:filterValues(params,'topicIds').filter(id=>allowed.has(id)).join(',')};
}
