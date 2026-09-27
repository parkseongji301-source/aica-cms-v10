import test from 'node:test';
import assert from 'node:assert/strict';
import {contextualPostPath,reviewContext,reviewDraftSelection,reviewPath,scopedPostParams} from '../src/reviewNavigation.ts';
import type {ClassificationCatalog} from '../src/types';
const catalog:ClassificationCatalog={types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1}],cohorts:[],topics:[
  {id:71,code:'REVIEW_LIFE',name:'생활',active:true},{id:84,code:'REVIEW_CLASS',name:'수업',active:true},
  {id:95,code:'REVIEW_PROJECT',name:'프로젝트',active:true},{id:99,code:'FAQ_LIFE',name:'생활',active:true}
],allowedTopics:[{typeCode:'REVIEW',topicId:71},{typeCode:'REVIEW',topicId:84},{typeCode:'REVIEW',topicId:95},{typeCode:'FAQ',topicId:99}]};
test('IA locations resolve actual topic IDs by code, not display name or assumed numbers',()=>{
  for(const [key,id] of [['life','71'],['class','84'],['project','95']]){
    const path=reviewPath(key,catalog)!;const query=new URLSearchParams(path.split('?')[1]);
    assert.equal(query.get('typeCodes'),'REVIEW');assert.equal(query.get('topicIds'),id);
  }
  assert.equal(new URLSearchParams(reviewPath('all',catalog)!.split('?')[1]).get('topicIds'),null);
});
test('missing, inactive or disallowed topic never falls back to all posts',()=>{
  assert.equal(reviewPath('life',null),null);
  assert.equal(reviewPath('unknown',catalog),null);
  assert.equal(reviewPath('life',{...catalog,topics:catalog.topics.filter(t=>t.id!==71)}),null);
  assert.equal(reviewPath('life',{...catalog,allowedTopics:[]}),null);
  assert.equal(reviewPath('life',{...catalog,topics:catalog.topics.map(t=>({...t,active:false}))}),null);
});
test('location locks REVIEW/topic while retaining cohort OR and other AND filters',()=>{
  const input=new URLSearchParams('reviewSection=life&typeCodes=FAQ&topicIds=99&cohortIds=6,7&categoryId=2&q=test&status=DRAFT&page=1');
  const params=scopedPostParams(input,reviewContext(input,catalog));
  assert.equal(params.get('typeCodes'),'REVIEW');assert.equal(params.get('topicIds'),'71');
  assert.equal(params.get('cohortIds'),'6,7');assert.equal(params.get('categoryId'),'2');assert.equal(params.get('page'),'1');
  assert.equal(params.has('reviewSection'),false);
});
test('new review starts only the selected topic, cohorts empty; both entries share editor path and ID',()=>{
  const life=reviewContext(new URLSearchParams('reviewSection=life'),catalog)!;
  const all=reviewContext(new URLSearchParams('reviewSection=all'),catalog)!;
  assert.deepEqual(reviewDraftSelection(life),{typeCode:'REVIEW',cohortIds:[],topicIds:[71]});
  assert.deepEqual(reviewDraftSelection(all),{typeCode:'REVIEW',cohortIds:[],topicIds:[]});
  assert.equal(contextualPostPath(65,life).split('?')[0],contextualPostPath(65,null));
});
