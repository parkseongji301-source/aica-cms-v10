import test from 'node:test';
import assert from 'node:assert/strict';
import {listDraftSelection} from '../src/draftCreation.ts';
import type {ClassificationCatalog} from '../src/types';

const catalog:ClassificationCatalog={
  types:['GENERAL','REVIEW','RESTAURANT','INTERVIEW','FAQ'].map((code,index)=>({code,name:code,sortOrder:index,active:true})),
  cohorts:[],topics:[],allowedTopics:[]
};

test('single active list type is preselected without applying search filters as draft classification',()=>{
  for(const type of catalog.types)assert.deepEqual(listDraftSelection([type.code],catalog),{typeCode:type.code,cohortIds:[],topicIds:[]});
  assert.equal(listDraftSelection(['REVIEW','REVIEW'],catalog).typeCode,'REVIEW');
});

test('All and multiple-type lists require an explicit type for the new draft',()=>{
  assert.equal(listDraftSelection([],catalog).typeCode,'');
  assert.equal(listDraftSelection(['REVIEW','FAQ'],catalog).typeCode,'');
});

test('inactive or unknown types are not preselected for creation',()=>{
  const archived={...catalog,types:catalog.types.map(t=>({...t,active:t.code!=='REVIEW'}))};
  assert.equal(listDraftSelection(['REVIEW'],archived).typeCode,'');
  assert.equal(listDraftSelection(['UNKNOWN'],catalog).typeCode,'');
});
