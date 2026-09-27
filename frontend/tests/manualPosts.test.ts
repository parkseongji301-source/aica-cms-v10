import test from 'node:test';
import assert from 'node:assert/strict';
import {addManualPost,moveManualPost,removeManualPost,postsSourcePatch} from '../src/manualPosts.ts';
import {newSection,duplicateSection,changeSection,moveSection} from '../src/pageBlocks.ts';

test('manual choices are unique, bounded and keep the exact operator order',()=>{
 const ids=[97,105,33];assert.equal(addManualPost(ids,105),ids);assert.deepEqual(addManualPost(ids,44),[97,105,33,44]);assert.equal(addManualPost(ids,44,3),ids);
 assert.deepEqual(moveManualPost(ids,33,-1),[97,33,105]);assert.deepEqual(moveManualPost(ids,97,1),[105,97,33]);assert.equal(moveManualPost(ids,97,-1),ids);assert.equal(moveManualPost(ids,0,1),ids);
 assert.deepEqual(removeManualPost(ids,105),[97,33]);assert.deepEqual(ids,[97,105,33]);
});
test('switching sources restores both inactive settings and never drops unavailable references',()=>{
 const query={typeCode:'REVIEW',cohortIds:[2],topicIds:[1],sort:'LATEST' as const,limit:6};
 let section={...newSection('POSTS'),categoryId:11,query,manual:{postIds:[105,999999,97]}};
 for(const mode of ['manual','query','category','manual'] as const){section={...section,...postsSourcePatch(section,mode,query)};assert.deepEqual(section.manual.postIds,[105,999999,97]);assert.deepEqual(section.query,query);assert.equal(section.categoryId,11);}
 const fresh=newSection('POSTS');assert.deepEqual(postsSourcePatch(fresh,'manual',query).manual,{postIds:[]});assert.equal(fresh.manual,undefined);
});
test('manual clone arrays are independent and dirty serialization follows moves and removals',()=>{
 const original={...newSection('POSTS'),sourceMode:'manual' as const,manual:{postIds:[97,105,33]}};const baseline=JSON.stringify(original),copy=duplicateSection(original);assert.notEqual(copy.id,original.id);assert.deepEqual(copy.manual,original.manual);
 copy.manual!.postIds.reverse();assert.equal(JSON.stringify(original),baseline);assert.deepEqual(copy.manual!.postIds,[33,105,97]);
 const moved=moveSection([original,copy],1,-1);assert.equal(moved[0].id,copy.id);assert.deepEqual(moved[1].manual,original.manual);
 const changed=changeSection(moved,copy.id,{manual:{postIds:[105]}});assert.deepEqual(changed[1],original);assert.notEqual(JSON.stringify(changed),JSON.stringify(moved));assert.deepEqual(JSON.parse(JSON.stringify(changed))[0].manual,{postIds:[105]});
});
