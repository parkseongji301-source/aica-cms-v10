import type {ClassificationCatalog,ClassificationSelection} from './types';

// Navigation locations apply filters to posts; they never create pages or classification terms.
export const contentSections={
  REVIEW:{param:'reviewSection',label:'후기',parent:'선배들의 SSUL',nodes:[
    {key:'all',label:'후기',code:null},{key:'life',label:'생활',code:'REVIEW_LIFE'},
    {key:'class',label:'수업',code:'REVIEW_CLASS'},{key:'project',label:'프로젝트',code:'REVIEW_PROJECT'}]},
  FAQ:{param:'faqSection',label:'FAQ',parent:'지원 전 Check!!',nodes:[
    {key:'all',label:'FAQ',code:null},{key:'preparation',label:'준비사항',code:'FAQ_PREPARATION'},
    {key:'application',label:'지원·선발',code:'FAQ_APPLICATION'},{key:'class',label:'수업',code:'FAQ_CLASS'},
    {key:'life',label:'생활',code:'FAQ_LIFE'},{key:'employment',label:'취업',code:'FAQ_EMPLOYMENT'},
    {key:'allowance',label:'지원금',code:'FAQ_ALLOWANCE'},{key:'project',label:'프로젝트',code:'FAQ_PROJECT'}]}
  ,RESTAURANT:{param:'restaurantSection',label:'근처 맛집',parent:'인사교 Real Life',group:'인사교 꿀팁',nodes:[{key:'all',label:'근처 맛집',code:null}]}
} as const;
export type SectionType=keyof typeof contentSections;
export type ContentContext={type:SectionType;param:string;key:string;label:string;topicId:number|null;error:string};
export const contentLocationLabel=(scope:ContentContext)=>scope.key==='all'?contentSections[scope.type].label:`${scope.label} ${contentSections[scope.type].label}`;
export function rememberPostOrigin(origins:Record<string,string>,previous:{path:string;query:string},next:{path:string;query:string}) {
  return previous.path==='/posts'&&/^\/posts\/\d+\/edit$/.test(next.path)?{...origins,[next.path]:previous.path+previous.query}:origins;
}
export const filterValues=(search:URLSearchParams,key:string)=>[...new Set(search.getAll(key).flatMap(v=>v.split(',')).filter(Boolean))];
export function sectionType(search:URLSearchParams):SectionType|null {
  return (Object.keys(contentSections) as SectionType[]).find(type=>search.has(contentSections[type].param))||null;
}
export function contentContext(search:URLSearchParams,catalog:ClassificationCatalog|null):ContentContext|null {
  const type=sectionType(search);if(!type)return null;
  const section=contentSections[type],key=search.get(section.param)||'',node=section.nodes.find(n=>n.key===key);
  const base={type,param:section.param,key,label:node?.label||section.label,topicId:null};
  if(Object.values(contentSections).filter(s=>search.has(s.param)).length>1)return {...base,error:'콘텐츠 탐색 위치가 중복되었습니다. 한 위치를 선택하세요.'};
  if(!node)return {...base,error:`${section.label} 탐색 위치를 확인하세요.`};
  if(!catalog)return {...base,error:'분류 사전을 불러오는 중입니다.'};
  if(!catalog.types.some(t=>t.code===type&&t.active))return {...base,error:`${section.label} 유형이 연결되지 않았습니다.`};
  if(!node.code)return {...base,error:''};
  const topic=catalog.topics.find(t=>t.code===node.code&&t.active);
  if(!topic||!catalog.allowedTopics.some(a=>a.typeCode===type&&a.topicId===topic.id))
    return {...base,error:`${node.label} 주제가 ${section.label} 유형에 연결되지 않았습니다.`};
  return {...base,topicId:topic.id,error:''};
}
export function sectionPath(type:SectionType,key:string,catalog:ClassificationCatalog|null):string|null {
  const search=new URLSearchParams({[contentSections[type].param]:key,typeCodes:type}),scope=contentContext(search,catalog);
  if(!scope||scope.error)return null;
  if(scope.topicId!==null)search.set('topicIds',String(scope.topicId));
  return '/posts?'+search;
}
export function scopedPostParams(search:URLSearchParams,scope:ContentContext|null):URLSearchParams {
  const result=new URLSearchParams({q:search.get('q')||'',status:search.get('status')||'',page:String(Math.max(0,Number(search.get('page'))||0))});
  if(search.get('categoryId'))result.set('categoryId',search.get('categoryId')!);
  for(const key of ['typeCodes','cohortIds','topicIds'])if(filterValues(search,key).length)result.set(key,filterValues(search,key).join(','));
  // Section lists use the sidebar location, search and status only. Old URLs must
  // not silently keep advanced filters that can no longer be seen or cleared.
  if(scope){result.set('typeCodes',scope.type);result.delete('categoryId');result.delete('cohortIds');result.delete('topicIds');if(scope.topicId!==null)result.set('topicIds',String(scope.topicId));}
  return result;
}
export const draftSelection=(scope:ContentContext):ClassificationSelection=>({typeCode:scope.type,cohortIds:[],topicIds:scope.topicId===null?[]:[scope.topicId]});
export const contextualPostPath=(id:number,scope:ContentContext|null)=>`/posts/${id}/edit`+(scope?'?'+scope.param+'='+encodeURIComponent(scope.key):'');
export function returnSectionPath(search:URLSearchParams,catalog:ClassificationCatalog|null) {
  const type=sectionType(search);return type?sectionPath(type,search.get(contentSections[type].param)||'',catalog):null;
}
