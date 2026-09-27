import {test} from 'node:test';
import assert from 'node:assert/strict';
import {classificationSelection,classificationProblem,classificationView,invalidTopicIds,sameClassification,toggleId,topicLabel} from '../src/classification.ts';
import type {Classification,ClassificationCatalog} from '../src/types.ts';
import type {PostDocument} from '../src/types.ts';
import {postFingerprint} from '../src/contentDocument.ts';
const catalog:ClassificationCatalog={types:[{code:'GENERAL',name:'일반',active:true,sortOrder:0},{code:'REVIEW',name:'후기',active:true,sortOrder:1},{code:'FAQ',name:'FAQ',active:true,sortOrder:2}],cohorts:[{id:6,code:'C6',name:'6기',active:true},{id:7,code:'C7',name:'7기',active:true}],topics:[{id:11,code:'RL',name:'생활',active:true},{id:12,code:'RC',name:'수업',active:true},{id:21,code:'FL',name:'생활',active:true}],allowedTopics:[{typeCode:'REVIEW',topicId:11},{typeCode:'REVIEW',topicId:12},{typeCode:'FAQ',topicId:21}]};
const review:Classification={typeCode:'REVIEW',typeName:'후기',cohortIds:[6,7],topicIds:[11],cohorts:catalog.cohorts,topics:[catalog.topics[0]]};
test('switching type preserves invalid IDs and blocks save until explicit removal',()=>{
 const next={...review,typeCode:'FAQ'};
 assert.deepEqual(invalidTopicIds(next,catalog),[11]);assert.ok(classificationProblem(next,catalog,review));
 assert.deepEqual(next.topicIds,[11]);
 const selected={...next,topicIds:toggleId(next.topicIds,21)};
 assert.ok(classificationProblem(selected,catalog,review));
 const resolved={...selected,topicIds:toggleId(selected.topicIds,11)};
 assert.equal(classificationProblem(resolved,catalog,review),'');assert.deepEqual(resolved.topicIds,[21]);
});
test('identical display names have distinct IDs and visible filter scope',()=>{
 assert.equal(topicLabel(11,catalog),'생활 · 후기');assert.equal(topicLabel(21,catalog),'생활 · FAQ');
 assert.deepEqual(invalidTopicIds({...review,topicIds:[21]},catalog),[21]);
});
test('empty and multiple selections, canonical payload and order-independent equality',()=>{
 assert.equal(classificationProblem({...review,typeCode:'GENERAL',cohortIds:[],topicIds:[]},catalog,review),'');
 assert.deepEqual(classificationSelection({...review,cohortIds:[7,6],topicIds:[12,11]}),{typeCode:'REVIEW',cohortIds:[6,7],topicIds:[11,12]});
 assert.ok(sameClassification(review,{...review,cohortIds:[7,6]}));
 assert.ok(!sameClassification(review,{...review,topicIds:[12],topics:[catalog.topics[1]]}));
});
test('draft labels come from catalog while published snapshot names remain intact',()=>{
 const changed={...catalog,topics:catalog.topics.map(t=>t.id===11?{...t,name:'생활 변경'}:t)};
 const draft=classificationView(review,changed);
 assert.equal(draft.topics[0].name,'생활 변경');assert.equal(review.topics[0].name,'생활');assert.ok(!sameClassification(draft,review));
});
test('archived values can be retained but cannot be newly selected',()=>{
 const archived={...catalog,topics:catalog.topics.map(t=>({...t,active:false}))};
 assert.equal(classificationProblem(review,archived,review),'');
 assert.ok(classificationProblem({...review,topicIds:[12]},archived,review));
});
test('classification-only changes participate in the actual editor dirty fingerprint',()=>{
 const doc={title:'동일 제목',content:'동일 본문',richContent:null,categoryId:2,classification:review} as PostDocument;
 for(const classification of [{...review,typeCode:'FAQ'},{...review,cohortIds:[]},{...review,topicIds:[12]}])assert.notEqual(postFingerprint({...doc,classification}),postFingerprint(doc));
 assert.equal(postFingerprint({...doc,classification:{...review,cohortIds:[7,6]}}),postFingerprint(doc));
});
