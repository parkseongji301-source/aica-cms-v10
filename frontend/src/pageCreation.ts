import type {ClassificationCatalog} from './types';
import {newBlockId} from './pageBlocks.ts';

/** Use the existing page form API with an explicit, unpublished draft; sections default to none. */
export function pageDraftForm(title:string,slug:string,sections:unknown[]=[]) {
  return new URLSearchParams({title:title.trim(),slug:slug.trim().toLowerCase(),sectionsJson:JSON.stringify(sections),action:'save',saveIntent:'MANUAL_DRAFT'});
}
export type CreatedPage = {id:number;revision:number;slug:string;status:string;pending:boolean};

// A content collection is an ordinary page whose first block is a POSTS list in query mode; no page type exists.
export type CollectionPreset={typeCode:string;cohortId:number|null;topicId:number|null;limit:number};
export const COLLECTION_LIMITS={min:1,max:20,default:6} as const;
export function collectionSections(preset:CollectionPreset,heading:string) {
  if(!preset.typeCode)throw new Error('콘텐츠 유형을 선택하세요.');
  if(!Number.isInteger(preset.limit)||preset.limit<COLLECTION_LIMITS.min||preset.limit>COLLECTION_LIMITS.max)throw new Error('표시 개수는 1~20개로 선택하세요.');
  return [{id:newBlockId(),schemaVersion:2,type:'POSTS',variation:'default',heading:heading.trim(),body:'',bodyDoc:null,imageId:null,categoryId:null,link:'',label:'',visible:true,
    sourceMode:'query',query:{typeCode:preset.typeCode,cohortIds:preset.cohortId===null?[]:[preset.cohortId],topicIds:preset.topicId===null?[]:[preset.topicId],sort:'LATEST',limit:preset.limit},manual:null}];
}
/** Suggested title such as "6기 프로젝트 후기"; the operator can change it. */
export function collectionTitle(catalog:ClassificationCatalog,preset:CollectionPreset) {
  const type=catalog.types.find(t=>t.code===preset.typeCode)?.name??'';
  const cohort=preset.cohortId===null?'':catalog.cohorts.find(c=>c.id===preset.cohortId)?.name??'';
  const topic=preset.topicId===null?'':catalog.topics.find(t=>t.id===preset.topicId)?.name??'';
  return [cohort,topic,type].filter(Boolean).join(' ');
}
