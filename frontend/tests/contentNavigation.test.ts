import test from 'node:test';
import assert from 'node:assert/strict';
import {contentContext,contentLocationLabel,contentNodes,contextualPostPath,draftSelection,hasContentLocation,returnSectionPath,scopedPostParams,sectionPath} from '../src/contentNavigation.ts';
import type {ClassificationCatalog,ContentArea} from '../src/types';

// 콘텐츠 작업 follows the site composition: areas linked to a content type, topics from the dictionary.
const catalog:ClassificationCatalog={types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1},{code:'FAQ',name:'FAQ',active:true,sortOrder:2},{code:'RESTAURANT',name:'맛집',active:true,sortOrder:3}],cohorts:[],
  topics:[{id:71,code:'REVIEW_LIFE',name:'생활',active:true},{id:84,code:'REVIEW_CLASS',name:'수업',active:true},{id:95,code:'REVIEW_PROJECT',name:'프로젝트',active:true},
    {id:301,code:'FAQ_PREPARATION',name:'준비사항',active:true},{id:304,code:'FAQ_LIFE',name:'생활',active:true},{id:399,code:'FAQ_OLD',name:'폐지',active:false}],
  allowedTopics:[{typeCode:'REVIEW',topicId:71},{typeCode:'REVIEW',topicId:84},{typeCode:'REVIEW',topicId:95},{typeCode:'FAQ',topicId:301},{typeCode:'FAQ',topicId:304},{typeCode:'FAQ',topicId:399}]};
const reviews:ContentArea={pageId:70,typeCode:'REVIEW',label:'후기',groups:['선배들의 SSUL']};
const faq:ContentArea={pageId:80,typeCode:'FAQ',label:'자주 묻는 질문',groups:[]};
const food:ContentArea={pageId:90,typeCode:'RESTAURANT',label:'근처 식당',groups:['인사교 Real Life','인사교 꿀팁']};
const areas=[reviews,faq,food];
const query=(path:string)=>new URLSearchParams(path.split('?')[1]);

test('topics of the linked type become the area sub-navigation, in dictionary order, active only',()=>{
  assert.deepEqual(contentNodes(reviews,catalog).map(n=>n.key),['all','REVIEW_LIFE','REVIEW_CLASS','REVIEW_PROJECT']);
  assert.deepEqual(contentNodes(faq,catalog).map(n=>n.label),['자주 묻는 질문','준비사항','생활']);
  assert.deepEqual(contentNodes(food,catalog).map(n=>n.key),['all']);
  assert.deepEqual(contentNodes(reviews,null).map(n=>n.key),['all']);
});

test('area locations resolve real topic IDs by code and never another type\'s same-named topic',()=>{
  const life=query(sectionPath(reviews,'REVIEW_LIFE',catalog,areas)!);
  assert.equal(life.get('area'),'70');assert.equal(life.get('topic'),'REVIEW_LIFE');assert.equal(life.get('typeCodes'),'REVIEW');assert.equal(life.get('topicIds'),'71');
  assert.equal(query(sectionPath(faq,'FAQ_LIFE',catalog,areas)!).get('topicIds'),'304');
  assert.equal(query(sectionPath(reviews,'all',catalog,areas)!).get('topicIds'),null);
  assert.equal(sectionPath(food,'all',catalog,areas),'/posts?area=90&typeCodes=RESTAURANT');
  // Missing dictionary, unknown, inactive or disallowed topics never fall back to all posts.
  assert.equal(sectionPath(reviews,'REVIEW_LIFE',null,areas),null);
  assert.equal(sectionPath(reviews,'FAQ_LIFE',catalog,areas),null);
  assert.equal(sectionPath(faq,'FAQ_OLD',catalog,areas),null);
  assert.equal(sectionPath(reviews,'REVIEW_LIFE',{...catalog,allowedTopics:[]},areas),null);
  assert.equal(sectionPath(reviews,'all',{...catalog,types:catalog.types.map(t=>({...t,active:false}))},areas),null);
});

test('an unlinked, removed or duplicated location explains itself instead of listing everything',()=>{
  assert.match(contentContext(new URLSearchParams('area=999'),catalog,areas)!.error,/사이트 구성/);
  assert.match(contentContext(new URLSearchParams('area=70&faqSection=life'),catalog,areas)!.error,/중복/);
  assert.equal(contentContext(new URLSearchParams('q=x'),catalog,areas),null);
  assert.equal(hasContentLocation(new URLSearchParams('area=70')),true);
  assert.equal(hasContentLocation(new URLSearchParams('typeCodes=REVIEW')),false);
});

test('addresses from before V14 open the representative area of the same type',()=>{
  const life=contentContext(new URLSearchParams('reviewSection=life'),catalog,areas)!;
  assert.equal(life.error,'');assert.equal(life.pageId,70);assert.equal(life.topicId,71);
  assert.equal(contentContext(new URLSearchParams('faqSection=preparation'),catalog,areas)!.topicId,301);
  assert.equal(contentContext(new URLSearchParams('restaurantSection=all'),catalog,areas)!.pageId,90);
  assert.match(contentContext(new URLSearchParams('reviewSection=life'),catalog,[faq])!.error,/사이트 구성/);
});

test('an area list keeps search, status and page and drops hidden advanced filters',()=>{
  const input=new URLSearchParams('area=70&topic=REVIEW_LIFE&typeCodes=FAQ&topicIds=99&cohortIds=6,7&categoryId=2&q=test&status=DRAFT&page=1');
  const params=scopedPostParams(input,contentContext(input,catalog,areas));
  assert.equal(params.get('typeCodes'),'REVIEW');assert.equal(params.get('topicIds'),'71');
  assert.equal(params.has('cohortIds'),false);assert.equal(params.has('categoryId'),false);assert.equal(params.get('page'),'1');
  assert.equal(params.get('q'),'test');assert.equal(params.get('status'),'DRAFT');assert.equal(params.has('area'),false);
  const unscoped=scopedPostParams(input,null);
  assert.equal(unscoped.get('cohortIds'),'6,7');assert.equal(unscoped.get('categoryId'),'2');
});

test('new content starts with the area type and selected topic; editor and list keep the location',()=>{
  const life=contentContext(new URLSearchParams('area=70&topic=REVIEW_LIFE'),catalog,areas)!;
  const all=contentContext(new URLSearchParams('area=90'),catalog,areas)!;
  assert.deepEqual(draftSelection(life),{typeCode:'REVIEW',cohortIds:[],topicIds:[71]});
  assert.deepEqual(draftSelection(all),{typeCode:'RESTAURANT',cohortIds:[],topicIds:[]});
  assert.equal(contextualPostPath(65,life),'/posts/65/edit?area=70&topic=REVIEW_LIFE');
  assert.equal(contextualPostPath(65,life).split('?')[0],contextualPostPath(65,null));
  assert.equal(returnSectionPath(new URLSearchParams('area=70&topic=REVIEW_LIFE'),catalog,areas),sectionPath(reviews,'REVIEW_LIFE',catalog,areas));
  assert.equal(contentLocationLabel(life),'생활 후기');assert.equal(contentLocationLabel(all),'근처 식당');
});
