import type {ClassificationCatalog,ClassificationSelection,ContentArea} from './types';

// 콘텐츠 작업 locations come from the site composition: a PAGE area linked to a content type is that
// type's representative work area, and its sub-navigation is the nodes the operator saved for it (V16),
// each one topic of that type, in saved order. Nothing is derived from the topic dictionary.
// Locations only filter posts; they never create pages, nodes or classification terms.
export type ContentNode={key:string;label:string;topicId:number|null};
// relocate: the address points to a location that no longer exists (an old address, an area removed from
// the composition, or a node that was removed). The screen then offers 전체 콘텐츠 and the current
// composition instead of guessing.
export type ContentContext={pageId:number;type:string;param:'area';key:string;label:string;areaLabel:string;groups:string[];topicId:number|null;error:string;relocate:boolean};

/** 'all' is the area itself; the other nodes are the saved sub-navigation entries (key = node id). */
export function contentNodes(area:ContentArea):ContentNode[] {
  const nodes:ContentNode[]=[{key:'all',label:area.label,topicId:null}];
  for(const node of area.nodes??[])nodes.push({key:String(node.id),label:node.name,topicId:node.topicId});
  return nodes;
}
// Addresses from before V14 used a fixed section parameter per demo area (for example ?somethingSection=key).
// They are recognized only by that shape and never mapped to a type: the composition decides locations now.
const oldLocation=(key:string)=>/^[a-z][A-Za-z0-9]*Section$/.test(key);
const locationParams=(search:URLSearchParams)=>[...new Set([...search.keys()])].filter(key=>key==='area'||oldLocation(key));
export const hasContentLocation=(search:URLSearchParams)=>locationParams(search).length>0;
export const contentLocationLabel=(scope:ContentContext)=>scope.key==='all'?scope.areaLabel:`${scope.areaLabel} · ${scope.label}`;
export function rememberPostOrigin(origins:Record<string,string>,previous:{path:string;query:string},next:{path:string;query:string}) {
  return previous.path==='/posts'&&/^\/posts\/\d+\/edit$/.test(next.path)?{...origins,[next.path]:previous.path+previous.query}:origins;
}
export const filterValues=(search:URLSearchParams,key:string)=>[...new Set(search.getAll(key).flatMap(v=>v.split(',')).filter(Boolean))];

export function contentContext(search:URLSearchParams,catalog:ClassificationCatalog|null,areas:ContentArea[]):ContentContext|null {
  const params=locationParams(search);if(!params.length)return null;
  const area=params.includes('area')?areas.find(a=>String(a.pageId)===search.get('area')):undefined,key=search.get('node')||'all';
  const base={pageId:area?.pageId??0,type:area?.typeCode??'',param:'area' as const,key,label:area?.label??'',areaLabel:area?.label??'',groups:area?.groups??[],topicId:null,relocate:false};
  if(params.some(oldLocation))return {...base,relocate:true,error:'예전 콘텐츠 작업 주소입니다. 콘텐츠 작업 위치는 이제 사이트 구조에서 정합니다. 전체 글에서 찾거나 현재 구조에서 위치를 다시 선택하세요.'};
  if(!area)return {...base,relocate:true,error:'이 콘텐츠 작업 위치를 찾을 수 없습니다. 사이트 구조에서 빠졌거나 글 종류 선택이 바뀌었을 수 있습니다. 글은 그대로 있으니 전체 글에서 찾거나 현재 구조에서 위치를 다시 선택하세요.'};
  if(!area.typeCode)return {...base,error:'이 페이지에는 관리할 글 종류가 없습니다. 콘텐츠 작업 메뉴에서는 페이지 내용을 편집합니다.'};
  if(!catalog)return {...base,error:'분류 사전을 불러오는 중입니다.'};
  if(!catalog.types.some(t=>t.code===area!.typeCode&&t.active))return {...base,error:`${area.label}에 연결된 유형을 사용할 수 없습니다.`};
  if(key==='all')return {...base,error:''};
  const node=(area.nodes??[]).find(n=>String(n.id)===key);
  if(!node)return {...base,relocate:true,error:`이 하위 항목은 '${area.label}' 아래에 더 이상 없습니다. 글은 그대로 있으니 전체 글에서 찾거나 현재 하위 항목을 다시 선택하세요.`};
  // A node shows its topic's posts; a retired or disallowed topic is reported, never widened to all posts.
  const topic=node.topicId===null?null:catalog.topics.find(t=>t.id===node.topicId);
  if(node.topicId!==null&&(!topic||!topic.active||!catalog.allowedTopics.some(a=>a.typeCode===area.typeCode&&a.topicId===node.topicId)))
    return {...base,label:node.name,error:`‘${node.name}’ 항목의 주제를 사용할 수 없습니다. 사이트 구조의 페이지 설정에서 이 항목의 주제를 바꾸세요.`};
  return {...base,label:node.name,topicId:node.topicId,error:''};
}
/** The location part of an address: the area and, below it, one saved node. */
export function locationQuery(scope:ContentContext):Record<string,string> {
  return scope.key==='all'?{area:String(scope.pageId)}:{area:String(scope.pageId),node:scope.key};
}
export function sectionPath(area:ContentArea,key:string,catalog:ClassificationCatalog|null,areas:ContentArea[]=[area]):string|null {
  const search=new URLSearchParams(key==='all'?{area:String(area.pageId)}:{area:String(area.pageId),node:key});
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
