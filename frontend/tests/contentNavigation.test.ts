import test from 'node:test';
import assert from 'node:assert/strict';
import {contentContext,contentLocationLabel,contentNodes,contextualPostPath,draftSelection,hasContentLocation,returnSectionPath,scopedPostParams,sectionPath} from '../src/contentNavigation.ts';
import type {ClassificationCatalog,ContentArea} from '../src/types';

// 콘텐츠 작업 follows the site composition: areas linked to a content type, and under each area only the
// sub-navigation nodes the operator saved (V16). The topic dictionary never becomes navigation by itself.
const catalog:ClassificationCatalog={types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1},{code:'FAQ',name:'FAQ',active:true,sortOrder:2},{code:'RESTAURANT',name:'맛집',active:true,sortOrder:3}],cohorts:[],
  topics:[{id:71,code:'REVIEW_LIFE',name:'생활',active:true},{id:84,code:'REVIEW_CLASS',name:'수업',active:true},{id:95,code:'REVIEW_PROJECT',name:'프로젝트',active:true},
    {id:301,code:'FAQ_PREPARATION',name:'준비사항',active:true},{id:304,code:'FAQ_LIFE',name:'생활',active:true},{id:399,code:'FAQ_OLD',name:'폐지',active:false}],
  allowedTopics:[{typeCode:'REVIEW',topicId:71},{typeCode:'REVIEW',topicId:84},{typeCode:'REVIEW',topicId:95},{typeCode:'FAQ',topicId:301},{typeCode:'FAQ',topicId:304},{typeCode:'FAQ',topicId:399}]};
// 후기 has three allowed topics but the operator saved only two nodes, in their own order and with their own names.
const reviews:ContentArea={pageId:70,typeCode:'REVIEW',label:'후기 전체',groups:['선배들의 SSUL'],nodes:[{id:5,name:'프로젝트 후기',topicId:95},{id:3,name:'생활 이야기',topicId:71}]};
const faq:ContentArea={pageId:80,typeCode:'FAQ',label:'자주 묻는 질문',groups:[],nodes:[{id:9,name:'옛 항목',topicId:399},{id:10,name:'다른 유형 주제',topicId:84}]};
const food:ContentArea={pageId:90,typeCode:'RESTAURANT',label:'근처 식당',groups:['인사교 Real Life','인사교 꿀팁'],nodes:[]};
const areas=[reviews,faq,food];
const query=(path:string)=>new URLSearchParams(path.split('?')[1]);

test('the sub-navigation is exactly the saved nodes in saved order, never the dictionary',()=>{
  assert.deepEqual(contentNodes(reviews).map(n=>[n.key,n.label,n.topicId]),[['all','후기 전체',null],['5','프로젝트 후기',95],['3','생활 이야기',71]]);
  assert.deepEqual(contentNodes(food).map(n=>n.key),['all']);
  // A type with allowed topics but no saved nodes shows the area alone.
  assert.deepEqual(contentNodes({...reviews,nodes:[]}).map(n=>n.key),['all']);
});

test('node locations resolve the saved topic and refuse retired or disallowed topics',()=>{
  const project=query(sectionPath(reviews,'5',catalog,areas)!);
  assert.equal(project.get('area'),'70');assert.equal(project.get('node'),'5');assert.equal(project.get('typeCodes'),'REVIEW');assert.equal(project.get('topicIds'),'95');
  assert.equal(query(sectionPath(reviews,'3',catalog,areas)!).get('topicIds'),'71');
  assert.equal(query(sectionPath(reviews,'all',catalog,areas)!).get('topicIds'),null);
  assert.equal(sectionPath(food,'all',catalog,areas),'/posts?area=90&typeCodes=RESTAURANT');
  // Missing dictionary, unknown node, retired topic or a topic of another type never fall back to all posts.
  assert.equal(sectionPath(reviews,'5',null,areas),null);
  assert.equal(sectionPath(reviews,'999',catalog,areas),null);
  assert.equal(sectionPath(faq,'9',catalog,areas),null);
  assert.equal(sectionPath(faq,'10',catalog,areas),null);
  assert.equal(sectionPath(reviews,'5',{...catalog,allowedTopics:[]},areas),null);
  assert.equal(sectionPath(reviews,'all',{...catalog,types:catalog.types.map(t=>({...t,active:false}))},areas),null);
  assert.match(contentContext(new URLSearchParams('area=80&node=9'),catalog,areas)!.error,/주제를 사용할 수 없습니다/);
  assert.equal(contentContext(new URLSearchParams('area=80&node=9'),catalog,areas)!.relocate,false);
});

test('an unlinked, removed or duplicated location explains itself instead of listing everything',()=>{
  const removed=contentContext(new URLSearchParams('area=999'),catalog,areas)!;
  assert.match(removed.error,/사이트 구조/);assert.equal(removed.relocate,true);
  // A node that was removed keeps the area but asks for a new choice (posts are untouched).
  const gone=contentContext(new URLSearchParams('area=70&node=999'),catalog,areas)!;
  assert.equal(gone.relocate,true);assert.equal(gone.pageId,70);assert.match(gone.error,/더 이상 없습니다/);
  assert.equal(contentContext(new URLSearchParams('area=70'),catalog,areas)!.relocate,false);
  assert.equal(contentContext(new URLSearchParams('q=x'),catalog,areas),null);
  assert.equal(hasContentLocation(new URLSearchParams('area=70')),true);
  assert.equal(hasContentLocation(new URLSearchParams('typeCodes=REVIEW')),false);
});

test('addresses from before V14 are recognized by shape only and lead to 전체 콘텐츠 or the current composition',()=>{
  // No old section name maps to a type or topic any more, whatever areas exist.
  for(const query of ['reviewSection=life','faqSection=preparation','anythingSection=all','area=70&reviewSection=life']){
    const old=contentContext(new URLSearchParams(query),catalog,areas)!;
    assert.equal(old.relocate,true,query);assert.equal(old.pageId,query.startsWith('area')?70:0,query);assert.match(old.error,/예전 콘텐츠 작업 주소/);
    assert.equal(sectionPath(areas[0],'all',catalog,areas)?.includes('Section'),false);
  }
  assert.equal(hasContentLocation(new URLSearchParams('reviewSection=life')),true);
  // An old address never filters the list by a guessed type.
  const params=scopedPostParams(new URLSearchParams('faqSection=life&q=x'),contentContext(new URLSearchParams('faqSection=life'),catalog,areas));
  assert.equal(params.get('typeCodes'),'');
  assert.equal(returnSectionPath(new URLSearchParams('reviewSection=life'),catalog,areas),null);
  // Words that merely contain "Section" in other parameters are not locations.
  assert.equal(contentContext(new URLSearchParams('q=Section&status=DRAFT'),catalog,areas),null);
});

test('an area list keeps search, status and page and drops hidden advanced filters',()=>{
  const input=new URLSearchParams('area=70&node=3&typeCodes=FAQ&topicIds=99&workNodeIds=9&cohortIds=6,7&categoryId=2&q=test&status=DRAFT&page=1');
  const params=scopedPostParams(input,contentContext(input,catalog,areas));
  assert.equal(params.get('typeCodes'),'REVIEW');assert.equal(params.get('topicIds'),'71');
  assert.equal(params.has('cohortIds'),false);assert.equal(params.has('categoryId'),false);assert.equal(params.get('page'),'1');
  assert.equal(params.get('q'),'test');assert.equal(params.get('status'),'DRAFT');assert.equal(params.has('area'),false);
  const unscoped=scopedPostParams(input,null);
  assert.equal(params.has('workNodeIds'),false);assert.equal(unscoped.get('workNodeIds'),'9');
  assert.equal(unscoped.get('cohortIds'),'6,7');assert.equal(unscoped.get('categoryId'),'2');
});

test('new content starts with the area type and the node topic; editor and list keep the location',()=>{
  const life=contentContext(new URLSearchParams('area=70&node=3'),catalog,areas)!;
  const all=contentContext(new URLSearchParams('area=90'),catalog,areas)!;
  assert.deepEqual(draftSelection(life),{typeCode:'REVIEW',cohortIds:[],topicIds:[71]});
  assert.deepEqual(draftSelection(all),{typeCode:'RESTAURANT',cohortIds:[],topicIds:[]});
  assert.equal(contextualPostPath(65,life),'/posts/65/edit?area=70&node=3');
  assert.equal(contextualPostPath(65,life).split('?')[0],contextualPostPath(65,null));
  assert.equal(returnSectionPath(new URLSearchParams('area=70&node=3'),catalog,areas),sectionPath(reviews,'3',catalog,areas));
  assert.equal(contentLocationLabel(life),'후기 전체 · 생활 이야기');assert.equal(contentLocationLabel(all),'근처 식당');
});
