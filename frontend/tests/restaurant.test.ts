import test from 'node:test';
import assert from 'node:assert/strict';
import {contentContext,contentSections,contextualPostPath,draftSelection,sectionPath,scopedPostParams} from '../src/contentNavigation.ts';
import {restaurantPayload,restaurantProblem} from '../src/restaurantFields.ts';
import {contentPresentation} from '../src/contentPresentation.ts';
import {postFingerprint} from '../src/contentDocument.ts';
import type {ClassificationCatalog,PostDocument} from '../src/types';
const catalog:ClassificationCatalog={types:[{code:'RESTAURANT',name:'맛집',active:true,sortOrder:2}],topics:[],cohorts:[],allowedTopics:[]};
const doc={title:'식당',content:'소개',richContent:null,categoryId:null,classification:{typeCode:'RESTAURANT',cohortIds:[],topicIds:[]},restaurant:{address:'주소 A'}} as unknown as PostDocument;
test('restaurant IA is a type-only existing list and shares the editor ID',()=>{
  const scope=contentContext(new URLSearchParams('restaurantSection=all&typeCodes=FAQ&topicIds=7'),catalog)!;
  const params=scopedPostParams(new URLSearchParams('restaurantSection=all&typeCodes=FAQ&topicIds=7'),scope);
  assert.equal(params.get('typeCodes'),'RESTAURANT');assert.equal(params.has('topicIds'),false);
  assert.equal(contentSections.RESTAURANT.parent,'인사교 Real Life');assert.equal(contentSections.RESTAURANT.group,'인사교 꿀팁');
  assert.equal(sectionPath('RESTAURANT','all',catalog),'/posts?restaurantSection=all&typeCodes=RESTAURANT');
  assert.equal(contextualPostPath(105,scope).split('?')[0],contextualPostPath(105,null));
  assert.deepEqual(draftSelection(scope),{typeCode:'RESTAURANT',cohortIds:[],topicIds:[]});
});
test('address-only changes participate in dirty state and save/preview payload',()=>{
  const changed={...doc,restaurant:{address:'주소 B'}};assert.notEqual(postFingerprint(doc),postFingerprint(changed));
  assert.deepEqual(restaurantPayload(changed),{restaurant:{address:'주소 B'}});assert.equal(restaurantProblem(changed),'');
});
test('type conversion retains address, blocks save, and requires explicit clearing',()=>{
  const changed={...doc,classification:{...doc.classification,typeCode:'FAQ'}};
  assert.equal(changed.restaurant.address,'주소 A');assert.ok(restaurantProblem(changed));
  const cleared={...changed,restaurant:{address:''}};assert.equal(restaurantProblem(cleared),'');assert.deepEqual(restaurantPayload(cleared),{restaurant:{address:''}});
  assert.deepEqual(restaurantPayload({...changed,restaurant:null}),{});
});
test('empty address is allowed while a too-long address is rejected',()=>{
  assert.equal(restaurantProblem({...doc,restaurant:{address:''}}),'');assert.ok(restaurantProblem({...doc,restaurant:{address:'x'.repeat(501)}}));
  assert.deepEqual(restaurantPayload({...doc,restaurant:null}),{restaurant:{address:''}});
});
test('restaurant presentation uses common title/content and no extra fields',()=>{
  const p=contentPresentation('RESTAURANT');assert.equal(p.title,'식당명');assert.equal(p.body,'소개');assert.equal(p.editorLabel,'식당 소개');
  assert.equal(contentPresentation('FAQ').title,'질문');assert.equal(contentPresentation('REVIEW').title,'제목');
});
