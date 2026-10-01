import test from 'node:test';
import assert from 'node:assert/strict';
import {postFilterTopics,postFilterWorkTopics,postTypeFilterChange} from '../src/postListFilters.ts';
import type {ClassificationCatalog,ContentArea} from '../src/types';

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
  assert.deepEqual(postTypeFilterChange(params,['FAQ'],catalog),{typeCodes:'FAQ',topicIds:'99,103',workNodeIds:''});
  assert.deepEqual(postTypeFilterChange(params,['RESTAURANT'],catalog),{typeCodes:'RESTAURANT',topicIds:'',workNodeIds:''});
  assert.deepEqual(postTypeFilterChange(params,['REVIEW','FAQ'],catalog),{typeCodes:'REVIEW,FAQ',topicIds:'71,99,103',workNodeIds:''});
  assert.equal(params.toString(),original);
  assert.equal(JSON.stringify(catalog),originalCatalog);
});

test('All clears only type restrictions and preserves existing valid topic selections',()=>{
  const params=new URLSearchParams('typeCodes=FAQ&topicIds=99&topicIds=103');
  assert.deepEqual(postTypeFilterChange(params,[],catalog),{typeCodes:'',topicIds:'99,103',workNodeIds:''});
  assert.deepEqual(postTypeFilterChange(new URLSearchParams(),[],catalog),{typeCodes:'',topicIds:'',workNodeIds:''});
});

const workCatalog:ClassificationCatalog={...catalog,types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1},{code:'FAQ',name:'FAQ',active:true,sortOrder:2}]};
const areas:ContentArea[]=[
  {pageId:70,typeCode:'REVIEW',label:'후기 모음',groups:['이야기'],nodes:[{id:8,name:'공통 이야기',topicId:103},{id:5,name:'생활 후기',topicId:71},{id:9,name:'예전 수업',topicId:84}]},
  {pageId:80,typeCode:'FAQ',label:'자주 묻는 질문',groups:[],nodes:[{id:11,name:'공통 이야기',topicId:103},{id:12,name:'생활 안내',topicId:99}]},
  {pageId:90,typeCode:null,label:'소개',groups:[],nodes:[]}
];
test('topic choices follow saved work-item names, parent groups and order, including shared topics as separate items',()=>{
  const options=postFilterWorkTopics(workCatalog,areas);
  assert.deepEqual(options.map(t=>[t.id,t.name,t.pageId,t.available]),[[8,'공통 이야기',70,true],[5,'생활 후기',70,true],[9,'예전 수업',70,false],[11,'공통 이야기',80,true],[12,'생활 안내',80,true]]);
  assert.deepEqual(options[0].groups,['이야기']);
  assert.deepEqual(postFilterWorkTopics(workCatalog,areas,['FAQ']).map(t=>t.id),[11,12]);
  assert.deepEqual(postFilterWorkTopics(workCatalog,[]),[]);
  assert.deepEqual(postFilterWorkTopics(workCatalog,[{...areas[0],nodes:[]}]),[]);
});
test('renaming, reordering or removing an item changes choices without changing its saved topic',()=>{
  const changed=[{...areas[0],nodes:[{...areas[0].nodes[1],name:'새 생활 항목'},areas[0].nodes[0]]}];
  assert.deepEqual(postFilterWorkTopics(workCatalog,changed).map(t=>[t.id,t.name,t.topicId]),[[5,'새 생활 항목',71],[8,'공통 이야기',103]]);
  assert.equal(areas[0].nodes[1].name,'생활 후기');
  assert.equal(postFilterWorkTopics({...workCatalog,allowedTopics:[]},areas).every(t=>!t.available),true);
});
test('changing types keeps only compatible work items and preserves the caller search, cohort and status',()=>{
  const params=new URLSearchParams('typeCodes=REVIEW,FAQ&workNodeIds=8,5,11,12&cohortIds=6&q=기록&status=DRAFT&page=3');
  const before=params.toString();
  assert.deepEqual(postTypeFilterChange(params,['FAQ'],workCatalog,areas),{typeCodes:'FAQ',topicIds:'',workNodeIds:'11,12'});
  assert.deepEqual(postTypeFilterChange(params,[],workCatalog,areas),{typeCodes:'',topicIds:'',workNodeIds:'8,5,11,12'});
  assert.equal(params.toString(),before);
});
