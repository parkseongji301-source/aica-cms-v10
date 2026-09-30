import test from 'node:test';
import assert from 'node:assert/strict';
import {allTypesQuery,isLegacyCategoryBlock,legacyToAllTypes,postsSourcePatch} from '../src/manualPosts.ts';
import {newSection} from '../src/pageBlocks.ts';
import {allowedTopicIds} from '../src/classification.ts';
import type {ClassificationCatalog,Section} from '../src/types';

const block=(patch:Partial<Section>):Section=>({id:'block_x',schemaVersion:2,variation:'default',type:'POSTS',heading:'',body:'',bodyDoc:null,imageId:null,categoryId:null,link:'',label:'',visible:true,...patch});

test('the old category mode without a category converts to all types, newest first, 6 at a time',()=>{
  assert.deepEqual(legacyToAllTypes(block({})),{sourceMode:'query',categoryId:null,query:{typeCode:null,cohortIds:[],topicIds:[],sort:'LATEST',limit:6}});
  assert.deepEqual(legacyToAllTypes(block({sourceMode:'category'})),legacyToAllTypes(block({})));
  // A block that really points at a category needs a person to decide; query and manual blocks are not legacy.
  assert.equal(legacyToAllTypes(block({categoryId:2})),null);
  assert.equal(legacyToAllTypes(block({sourceMode:'query',query:allTypesQuery()})),null);
  assert.equal(isLegacyCategoryBlock(block({sourceMode:'manual',manual:{postIds:[]}})),false);
  assert.equal(isLegacyCategoryBlock({...block({}),type:'TEXT'}),false);
});

test('leaving the old category mode never activates a condition stored next to it',()=>{
  // Like a draft that kept a REVIEW query while still in category mode.
  const dormant=block({sourceMode:'category',query:{typeCode:'REVIEW',cohortIds:[],topicIds:[1],sort:'LATEST',limit:6},manual:{postIds:[]}});
  const patch=postsSourcePatch(dormant,'query',allTypesQuery(6));
  assert.equal(patch.sourceMode,'query');assert.equal(patch.query?.typeCode,null);assert.deepEqual(patch.query?.topicIds,[]);
  assert.equal(legacyToAllTypes(dormant)?.query?.typeCode,null);
  // Other switches keep their stored settings as before.
  const query=block({sourceMode:'query',query:{typeCode:'FAQ',cohortIds:[],topicIds:[],sort:'LATEST',limit:3}});
  assert.equal(postsSourcePatch(query,'manual',allTypesQuery()).query?.typeCode,'FAQ');
});

test('new POSTS blocks start in query mode with all types',()=>{
  const posts=newSection('POSTS');
  assert.equal(posts.sourceMode,'query');assert.equal(posts.query?.typeCode,null);assert.equal(posts.categoryId,null);
  assert.equal(newSection('TEXT').sourceMode,undefined);
  const catalog:ClassificationCatalog={types:[],cohorts:[],topics:[],allowedTopics:[{typeCode:'GENERAL',topicId:11}]};
  assert.deepEqual(allowedTopicIds(catalog,null),[]);assert.deepEqual(allowedTopicIds(catalog,'GENERAL'),[11]);
});
