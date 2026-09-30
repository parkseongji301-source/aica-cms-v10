import test from 'node:test';
import assert from 'node:assert/strict';
import {classificationProblem,invalidTopicIds} from '../src/classification.ts';
import {contentPresentation} from '../src/contentPresentation.ts';
import {postFingerprint} from '../src/contentDocument.ts';
import type {ClassificationCatalog,PostDocument} from '../src/types';
const catalog:ClassificationCatalog={types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1},{code:'FAQ',name:'FAQ',active:true,sortOrder:2}],cohorts:[],
  topics:[{id:91,code:'REVIEW_LIFE',name:'생활',active:true},...['FAQ_PREPARATION','FAQ_APPLICATION','FAQ_CLASS','FAQ_LIFE','FAQ_EMPLOYMENT','FAQ_ALLOWANCE','FAQ_PROJECT'].map((code,i)=>({id:301+i,code,name:code,active:true}))],
  allowedTopics:[{typeCode:'REVIEW',topicId:91},...Array.from({length:7},(_,i)=>({typeCode:'FAQ',topicId:301+i}))]};
test('changing FAQ to REVIEW keeps disallowed topic until explicit correction and remains dirty',()=>{
  const baseline={typeCode:'FAQ',cohortIds:[],topicIds:[304]},changed={...baseline,typeCode:'REVIEW'};
  assert.deepEqual(changed.topicIds,[304]);assert.deepEqual(invalidTopicIds(changed,catalog),[304]);
  assert.ok(classificationProblem(changed,catalog,baseline));
  assert.equal(classificationProblem({...changed,topicIds:[91]},catalog,baseline),'');
  const doc={title:'질문',content:'답변',richContent:null,categoryId:null,classification:baseline} as unknown as PostDocument;
  assert.notEqual(postFingerprint(doc),postFingerprint({...doc,classification:{...doc.classification,...changed}}));
  assert.notEqual(postFingerprint(doc),postFingerprint({...doc,content:'답변 수정'}));
});
test('FAQ changes display vocabulary while the stored fields stay title/content',()=>{
  const faq=contentPresentation('FAQ'),general=contentPresentation('GENERAL');
  assert.equal(faq.title,'질문');assert.equal(faq.body,'답변');assert.equal(faq.editorLabel,'FAQ 답변');
  assert.equal(general.title,'제목');assert.equal(general.body,'본문');
  assert.deepEqual(contentPresentation('REVIEW'),general);
});
