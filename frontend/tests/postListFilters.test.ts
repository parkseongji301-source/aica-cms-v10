import test from 'node:test';
import assert from 'node:assert/strict';
import {postFilterTopics,postTypeFilterChange} from '../src/postListFilters.ts';
import type {ClassificationCatalog} from '../src/types';

const catalog:ClassificationCatalog={types:[],cohorts:[],topics:[
  {id:71,code:'REVIEW_LIFE',name:'생활',active:true},
  {id:84,code:'REVIEW_CLASS',name:'수업',active:false},
  {id:99,code:'FAQ_LIFE',name:'생활',active:true},
  {id:103,code:'SHARED',name:'공통 주제',active:true}
],allowedTopics:[
  {typeCode:'REVIEW',topicId:71},{typeCode:'REVIEW',topicId:84},{typeCode:'REVIEW',topicId:103},
  {typeCode:'FAQ',topicId:99},{typeCode:'FAQ',topicId:103}
]};

test('type boxes use real topic associations, including distinct same-named and archived topics',()=>{
  assert.deepEqual(postFilterTopics(catalog,['REVIEW']).map(t=>t.id),[71,84,103]);
  assert.deepEqual(postFilterTopics(catalog,['FAQ']).map(t=>t.id),[99,103]);
  for(const type of ['GENERAL','RESTAURANT','INTERVIEW'])assert.deepEqual(postFilterTopics(catalog,[type]),[]);
});

test('multiple types expose the union without duplicated shared topics; All exposes the full catalog',()=>{
  assert.deepEqual(postFilterTopics(catalog,['REVIEW','FAQ']).map(t=>t.id),[71,84,99,103]);
  assert.deepEqual(postFilterTopics(catalog,['RESTAURANT','REVIEW']).map(t=>t.id),[71,84,103]);
  assert.deepEqual(postFilterTopics(catalog,[]),catalog.topics);
});

test('changing types removes only incompatible list topics without changing other criteria or source data',()=>{
  const params=new URLSearchParams('typeCodes=REVIEW,FAQ&topicIds=71,99,103&cohortIds=6,7&status=DRAFT&categoryId=2&q=sample&page=3');
  const original=params.toString(),originalCatalog=JSON.stringify(catalog);
  assert.deepEqual(postTypeFilterChange(params,['FAQ'],catalog),{typeCodes:'FAQ',topicIds:'99,103'});
  assert.deepEqual(postTypeFilterChange(params,['RESTAURANT'],catalog),{typeCodes:'RESTAURANT',topicIds:''});
  assert.deepEqual(postTypeFilterChange(params,['REVIEW','FAQ'],catalog),{typeCodes:'REVIEW,FAQ',topicIds:'71,99,103'});
  assert.equal(params.toString(),original);
  assert.equal(JSON.stringify(catalog),originalCatalog);
});

test('All clears only type restrictions and preserves existing valid topic selections',()=>{
  const params=new URLSearchParams('typeCodes=FAQ&topicIds=99&topicIds=103');
  assert.deepEqual(postTypeFilterChange(params,[],catalog),{typeCodes:'',topicIds:'99,103'});
  assert.deepEqual(postTypeFilterChange(new URLSearchParams(),[],catalog),{typeCodes:'',topicIds:''});
});
