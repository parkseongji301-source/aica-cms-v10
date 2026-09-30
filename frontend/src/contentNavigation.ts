import type {ClassificationCatalog,ClassificationSelection,ContentArea} from './types';

// 콘텐츠 작업 locations come from the site composition: a PAGE area linked to a content type is that
// type's representative work area, and the type's allowed topics (dictionary order) are its sub-navigation.
// Locations only filter posts; they never create pages or classification terms.
export type ContentNode={key:string;label:string;topicId:number|null};
export type ContentContext={pageId:number;type:string;param:'area';key:string;label:string;areaLabel:string;groups:string[];topicId:number|null;error:string};

/** 'all' is the area itself; the other nodes are the active topics allowed for its type. */
export function contentNodes(area:ContentArea,catalog:ClassificationCatalog|null):ContentNode[] {
  const nodes:ContentNode[]=[{key:'all',label:area.label,topicId:null}];
  if(catalog)for(const topic of catalog.topics)
    if(topic.active&&catalog.allowedTopics.some(a=>a.typeCode===area.typeCode&&a.topicId===topic.id))nodes.push({key:topic.code,label:topic.name,topicId:topic.id});
  return nodes;
}
// Addresses from before V14 (the demo sections) still open the representative area of the same type.
const legacyLocations:Record<string,{type:string;keys:Record<string,string>}>={
  reviewSection:{type:'REVIEW',keys:{life:'REVIEW_LIFE',class:'REVIEW_CLASS',project:'REVIEW_PROJECT'}},
  faqSection:{type:'FAQ',keys:{preparation:'FAQ_PREPARATION',application:'FAQ_APPLICATION',class:'FAQ_CLASS',life:'FAQ_LIFE',employment:'FAQ_EMPLOYMENT',allowance:'FAQ_ALLOWANCE',project:'FAQ_PROJECT'}},
  restaurantSection:{type:'RESTAURANT',keys:{}},
};
const locationParams=(search:URLSearchParams)=>['area',...Object.keys(legacyLocations)].filter(key=>search.has(key));
export const hasContentLocation=(search:URLSearchParams)=>locationParams(search).length>0;
export const contentLocationLabel=(scope:ContentContext)=>scope.key==='all'?scope.areaLabel:`${scope.label} ${scope.areaLabel}`;
export function rememberPostOrigin(origins:Record<string,string>,previous:{path:string;query:string},next:{path:string;query:string}) {
  return previous.path==='/posts'&&/^\/posts\/\d+\/edit$/.test(next.path)?{...origins,[next.path]:previous.path+previous.query}:origins;
}
export const filterValues=(search:URLSearchParams,key:string)=>[...new Set(search.getAll(key).flatMap(v=>v.split(',')).filter(Boolean))];

export function contentContext(search:URLSearchParams,catalog:ClassificationCatalog|null,areas:ContentArea[]):ContentContext|null {
  const params=locationParams(search);if(!params.length)return null;
  let area:ContentArea|undefined,key='all';
  if(params[0]==='area'){area=areas.find(a=>String(a.pageId)===search.get('area'));key=search.get('topic')||'all';}
  else{const legacy=legacyLocations[params[0]];area=areas.find(a=>a.typeCode===legacy.type);const old=search.get(params[0])||'';key=old==='all'?'all':legacy.keys[old]??old;}
  const base={pageId:area?.pageId??0,type:area?.typeCode??'',param:'area' as const,key,label:area?.label??'',areaLabel:area?.label??'',groups:area?.groups??[],topicId:null};
  if(params.length>1)return {...base,error:'콘텐츠 탐색 위치가 중복되었습니다. 한 위치를 선택하세요.'};
  if(!area)return {...base,error:'이 콘텐츠 작업 위치를 찾을 수 없습니다. 사이트 구성에서 콘텐츠 작업 연결을 확인하세요.'};
  if(!catalog)return {...base,error:'분류 사전을 불러오는 중입니다.'};
  if(!catalog.types.some(t=>t.code===area!.typeCode&&t.active))return {...base,error:`${area.label}에 연결된 유형을 사용할 수 없습니다.`};
  if(key==='all')return {...base,error:''};
  const node=contentNodes(area,catalog).find(n=>n.key===key);
  if(!node)return {...base,error:`이 주제가 ${area.label} 유형에 연결되지 않았습니다.`};
  return {...base,label:node.label,topicId:node.topicId,error:''};
}
/** The location part of an address: the area and, below it, one topic. */
export function locationQuery(scope:ContentContext):Record<string,string> {
  return scope.key==='all'?{area:String(scope.pageId)}:{area:String(scope.pageId),topic:scope.key};
}
export function sectionPath(area:ContentArea,key:string,catalog:ClassificationCatalog|null,areas:ContentArea[]=[area]):string|null {
  const search=new URLSearchParams(key==='all'?{area:String(area.pageId)}:{area:String(area.pageId),topic:key});
  const scope=contentContext(search,catalog,areas);if(!scope||scope.error)return null;
  search.set('typeCodes',scope.type);if(scope.topicId!==null)search.set('topicIds',String(scope.topicId));
  return '/posts?'+search;
}
export function scopedPostParams(search:URLSearchParams,scope:ContentContext|null):URLSearchParams {
  const result=new URLSearchParams({q:search.get('q')||'',status:search.get('status')||'',page:String(Math.max(0,Number(search.get('page'))||0))});
  if(search.get('categoryId'))result.set('categoryId',search.get('categoryId')!);
  for(const key of ['typeCodes','cohortIds','topicIds'])if(filterValues(search,key).length)result.set(key,filterValues(search,key).join(','));
  // Area lists use the sidebar location, search and status only. Old URLs must
  // not silently keep advanced filters that can no longer be seen or cleared.
  if(scope){result.set('typeCodes',scope.type);result.delete('categoryId');result.delete('cohortIds');result.delete('topicIds');if(scope.topicId!==null)result.set('topicIds',String(scope.topicId));}
  return result;
}
export const draftSelection=(scope:ContentContext):ClassificationSelection=>({typeCode:scope.type,cohortIds:[],topicIds:scope.topicId===null?[]:[scope.topicId]});
export const contextualPostPath=(id:number,scope:ContentContext|null)=>`/posts/${id}/edit`+(scope?'?'+new URLSearchParams(locationQuery(scope)):'');
export function returnSectionPath(search:URLSearchParams,catalog:ClassificationCatalog|null,areas:ContentArea[]) {
  const scope=contentContext(search,catalog,areas);if(!scope||scope.error)return null;
  const area=areas.find(a=>a.pageId===scope.pageId)!;return sectionPath(area,scope.key,catalog,areas);
}
