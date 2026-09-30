import test from 'node:test';
import assert from 'node:assert/strict';
import {collectionSections,collectionTitle,pageDraftForm} from '../src/pageCreation.ts';

test('creates an empty unpublished draft without an existing page ID or revision',()=>{
  const form=pageDraftForm(' 교육생 후기 ',' Reviews-2026 ');
  assert.deepEqual(Object.fromEntries(form),{title:'교육생 후기',slug:'reviews-2026',sectionsJson:'[]',action:'save',saveIntent:'MANUAL_DRAFT'});
  assert.equal(form.has('id'),false);assert.equal(form.has('revision'),false);
});
test('blank optional address preserves server-side automatic generation',()=>{
  assert.equal(pageDraftForm('후기','  ').get('slug'),'');
});
test('form encoding preserves titles containing ampersands and cannot add request fields',()=>{
  const form=pageDraftForm('후기 & action=publish + 인터뷰','reviews');
  const parsed=new URLSearchParams(form.toString());
  assert.equal(parsed.get('title'),'후기 & action=publish + 인터뷰');
  assert.equal(parsed.get('action'),'save');assert.equal(parsed.getAll('action').length,1);
});
test('a content collection preset is a normal draft page with one POSTS query block',()=>{
  const sections=collectionSections({typeCode:'REVIEW',cohortId:1,topicId:3,limit:6},' 6기 프로젝트 후기 ');
  const form=pageDraftForm('6기 프로젝트 후기','',sections);
  assert.equal(form.get('action'),'save');assert.equal(form.has('id'),false);
  const saved=JSON.parse(form.get('sectionsJson')!);
  assert.equal(saved.length,1);assert.equal(saved[0].type,'POSTS');assert.equal(saved[0].sourceMode,'query');
  assert.equal(saved[0].categoryId,null);assert.equal(saved[0].heading,'6기 프로젝트 후기');
  assert.match(saved[0].id,/^block_[0-9a-f-]{36}$/);assert.equal(saved[0].schemaVersion,2);
  assert.deepEqual(saved[0].query,{typeCode:'REVIEW',cohortIds:[1],topicIds:[3],sort:'LATEST',limit:6});
});
test('collection presets need a type and a 1-20 limit and leave cohort and topic open when unset',()=>{
  assert.throws(()=>collectionSections({typeCode:'',cohortId:null,topicId:null,limit:6},''));
  assert.throws(()=>collectionSections({typeCode:'FAQ',cohortId:null,topicId:null,limit:21},''));
  assert.deepEqual(collectionSections({typeCode:'FAQ',cohortId:null,topicId:null,limit:20},'')[0].query,{typeCode:'FAQ',cohortIds:[],topicIds:[],sort:'LATEST',limit:20});
});
test('suggested collection titles follow cohort, topic and type names',()=>{
  const catalog={types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1}],cohorts:[{id:1,code:'COHORT_06',name:'6기',active:true}],topics:[{id:3,code:'REVIEW_PROJECT',name:'프로젝트',active:true}],allowedTopics:[{typeCode:'REVIEW',topicId:3}]} as never;
  assert.equal(collectionTitle(catalog,{typeCode:'REVIEW',cohortId:1,topicId:3,limit:6}),'6기 프로젝트 후기');
  assert.equal(collectionTitle(catalog,{typeCode:'REVIEW',cohortId:null,topicId:null,limit:6}),'후기');
});
