import type {Classification,ClassificationCatalog,ClassificationSelection} from './types';

export const classificationSelection=(value:ClassificationSelection):ClassificationSelection=>({
  typeCode:value.typeCode,cohortIds:[...value.cohortIds].sort((a,b)=>a-b),topicIds:[...value.topicIds].sort((a,b)=>a-b)
});
export const toggleId=(values:number[],id:number)=>values.includes(id)?values.filter(v=>v!==id):[...values,id].sort((a,b)=>a-b);
export const allowedTopicIds=(catalog:ClassificationCatalog,typeCode:string|null)=>typeCode===null?[]:catalog.allowedTopics.filter(t=>t.typeCode===typeCode).map(t=>t.topicId);
export const invalidTopicIds=(value:ClassificationSelection,catalog:ClassificationCatalog)=>value.topicIds.filter(id=>!allowedTopicIds(catalog,value.typeCode).includes(id));
export function classificationProblem(value:ClassificationSelection,catalog:ClassificationCatalog,baseline:ClassificationSelection):string {
  if(invalidTopicIds(value,catalog).length)return '유형에 맞지 않는 주제가 남아 있습니다. 아래에서 직접 해제하거나 유형을 되돌려 주세요.';
  const type=catalog.types.find(t=>t.code===value.typeCode);
  if(!type||(!type.active&&value.typeCode!==baseline.typeCode))return '사용할 수 없는 콘텐츠 유형입니다.';
  for(const [ids,terms,existing] of [[value.cohortIds,catalog.cohorts,baseline.cohortIds],[value.topicIds,catalog.topics,value.typeCode===baseline.typeCode?baseline.topicIds:[]]] as const){
    if(ids.some(id=>!terms.some(t=>t.id===id&&(t.active||existing.includes(id)))))return '사용할 수 없는 기수·주제 선택을 해제해 주세요.';
  }
  return '';
}
export function topicLabel(id:number,catalog:ClassificationCatalog):string {
  const topic=catalog.topics.find(t=>t.id===id);
  const types=catalog.allowedTopics.filter(t=>t.topicId===id).map(t=>catalog.types.find(type=>type.code===t.typeCode)?.name||t.typeCode);
  return (topic?.name||`주제 #${id}`)+(types.length?' · '+types.join('/'):'');
}
export function classificationView(value:Classification,catalog:ClassificationCatalog):Classification {
  return {...value,typeName:catalog.types.find(t=>t.code===value.typeCode)?.name||value.typeCode,
    cohorts:value.cohortIds.map(id=>catalog.cohorts.find(t=>t.id===id)||value.cohorts.find(t=>t.id===id)||{id,code:'',name:`기수 #${id}`,active:false}),
    topics:value.topicIds.map(id=>catalog.topics.find(t=>t.id===id)||value.topics.find(t=>t.id===id)||{id,code:'',name:`주제 #${id}`,active:false})};
}
export function sameClassification(a:Classification,b:Classification):boolean {
  return JSON.stringify(classificationSelection(a))===JSON.stringify(classificationSelection(b))&&a.typeName===b.typeName&&
    a.cohorts.every(t=>b.cohorts.some(x=>x.id===t.id&&x.name===t.name))&&a.topics.every(t=>b.topics.some(x=>x.id===t.id&&x.name===t.name));
}
