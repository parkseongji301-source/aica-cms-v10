import test from 'node:test';
import assert from 'node:assert/strict';
import {childrenAllSelected,deletionOrder,homeCandidates,movedSiblings,pageLocation,pageTree,parentOptions,parentWarning,publishedChildren} from '../src/pageHierarchy.ts';
import {pageDraftForm} from '../src/pageCreation.ts';
import type {PageRow} from '../src/types.ts';

const page=(id:number,title:string,parentId:number|null,sortOrder:number,status='PUBLISHED',areaKind:'PAGE'|'GROUP'='PAGE'):PageRow=>
  ({id,title,slug:'p'+id,status,revision:1,pending:false,updatedAt:'2026-09-30T10:00:00',parentId,sortOrder,areaKind,contentTypeCode:null,menuVisible:false,menuLabel:null,inStructure:true});
// Server order: flat by (sortOrder, id), children mixed in with top-level pages.
const pages=[page(1,'홈',null,0),page(70,'후기',65,0),page(65,'인사교 소개',null,1,'PRIVATE'),page(71,'FAQ',65,1),page(72,'오시는 길',null,2)];

test('rows follow the site structure: each page followed by its children in sibling order',()=>{
  assert.deepEqual(pageTree(pages).map(r=>`${r.page.id}:${r.depth}:${r.children}`),['1:1:0','65:1:2','70:2:0','71:2:0','72:1:0']);
  assert.equal(pageTree(pages).find(r=>r.page.id===71)?.parent?.title,'인사교 소개');
  // A missing parent does not hide the page.
  assert.deepEqual(pageTree([page(5,'고아',99,0)]).map(r=>r.page.id),[5]);
  assert.equal(pageLocation(pages,70),'인사교 소개 › 후기');
});

test('parent choices explain the three-level, cycle and home rules like the server',()=>{
  const reason=(pageId:number|null,parent:number)=>parentOptions(pages,pageId,1).find(o=>o.id===parent)?.reason;
  assert.equal(reason(72,65),null);
  // Three levels are the current operating limit (V14): a third level is fine, a fourth is refused.
  assert.equal(reason(72,70),null);
  const deep=[...pages,page(75,'셋째',70,0)],deepReason=(pageId:number,parent:number)=>parentOptions(deep,pageId,1).find(o=>o.id===parent)?.reason;
  assert.match(deepReason(72,75)!,/최대 3단계라 그 아래에는/);
  assert.match(deepReason(65,72)!,/최대 3단계를 넘습니다/);
  assert.match(reason(72,1)!,/홈\(첫 화면\) 페이지 아래/);
  assert.match(reason(1,65)!,/최상위에 고정/);
  assert.ok(!parentOptions(pages,72,1).some(o=>o.id===72));
  // Choices follow the site structure, not the flat server order.
  assert.deepEqual(parentOptions(pages,72,1).map(o=>o.id),[1,65,70,71]);
  // A page being created can go under any page up to the second level, except home.
  assert.deepEqual(parentOptions(pages,null,1).filter(o=>!o.reason).map(o=>o.id),[65,70,71,72]);
});

test('a published child under a non-public parent is only a warning',()=>{
  assert.equal(parentWarning(pages,pages[1]),'상위 페이지 비공개');
  assert.equal(parentWarning(pages,page(73,'초안',65,2,'DRAFT')),null);
  assert.equal(parentWarning(pages,pages[4]),null);
  // A GROUP parent (always DRAFT, no screen) never makes a published child look hidden.
  assert.equal(parentWarning([page(90,'묶음',null,3,'DRAFT','GROUP')],page(91,'안내',90,0)),null);
  assert.equal(publishedChildren(pages,65),2);
});

test('sibling moves stay inside the sibling group and stop at either end',()=>{
  assert.deepEqual(movedSiblings(pages,71,-1),[71,70]);
  assert.equal(movedSiblings(pages,70,-1),null);
  assert.deepEqual(movedSiblings(pages,72,-1),[1,72,65]);
  assert.equal(movedSiblings(pages,72,1),null);
});

test('selected children are deleted before their parent, and only a full selection releases the parent',()=>{
  assert.deepEqual(deletionOrder([pages[2],pages[1],pages[4]],pages).map(p=>p.id),[70,65,72]);
  assert.equal(childrenAllSelected(pages,65,[65,70,71]),true);
  assert.equal(childrenAllSelected(pages,65,[65,70]),false);
  assert.equal(childrenAllSelected(pages,72,[72]),false);
});

test('the home setting offers published top-level pages without children, never a GROUP',()=>{
  assert.deepEqual(homeCandidates(pages,'1').map(p=>p.id),[1,72]);
  assert.deepEqual(homeCandidates([...pages,page(90,'묶음',null,3,'PUBLISHED','GROUP')],'1').map(p=>p.id),[1,72]);
  // A GROUP can hold areas like any parent.
  assert.equal(parentOptions([...pages,page(90,'묶음',null,3,'DRAFT','GROUP')],72,1).find(o=>o.id===90)?.reason,null);
  assert.deepEqual(homeCandidates(pages,'70').map(p=>p.id),[1,70,72]);
});

test('nothing goes under an area removed from the structure',()=>{
  const removed={...page(90,'제거됨',null,3),inStructure:false};
  assert.match(parentOptions([...pages,removed],72,1).find(o=>o.id===90)!.reason!,/구성에서 제거된 영역 아래/);
  assert.match(parentOptions([...pages,removed],null,1).find(o=>o.id===90)!.reason!,/구성에서 제거된 영역 아래/);
});

test('new pages send a parent only when one is chosen',()=>{
  assert.equal(pageDraftForm('후기','',[]).has('parentId'),false);
  assert.equal(pageDraftForm('후기','',[],65).get('parentId'),'65');
});

import {childHost,childParentOptions,draggable,dropPlan} from '../src/pageHierarchy.ts';
// 사이트 구조 (검수 전 UX): fixed top level, home fixed first, second-level pages move by drag.
const site=[page(1,'홈',null,0),page(65,'소개',null,1),page(98,'후기',null,2),page(81,'지원',null,3),page(66,'연혁',65,0),page(67,'오시는 길',65,1),page(99,'프로젝트',98,0),page(90,'묶음',null,4,'DRAFT','GROUP'),page(91,'셋째',66,0)];
const removed={...page(82,'빠진 항목',null,5),inStructure:false};

test('the home page is completely fixed and top-level items only take children',()=>{
  assert.equal(draggable(site,site[0],1),false);
  assert.equal(draggable(site,site[1],1),true);assert.equal(draggable(site,site[4],1),true);
  assert.equal(draggable(site,site[8],1),false);
  assert.equal(childHost(site[0],1),false);assert.equal(childHost(site[2],1),true);assert.equal(childHost(site[4],1),false);assert.equal(childHost(removed,1),false);
  assert.match(dropPlan(site,1,1,{overId:65,after:true}) as string,/맨 위에 고정/);
});

test('top-level items reorder among themselves and never ahead of home',()=>{
  assert.deepEqual(dropPlan(site,1,81,{overId:65,after:false}),{pageId:81,parentId:null,ids:[1,81,65,98,90],moved:false});
  assert.deepEqual(dropPlan(site,1,65,{overId:90,after:true}),{pageId:65,parentId:null,ids:[1,98,81,90,65],moved:false});
  assert.match(dropPlan(site,1,98,{overId:1,after:false}) as string,/맨 위에 고정/);
  assert.deepEqual(dropPlan(site,1,98,{overId:1,after:true}),{pageId:98,parentId:null,ids:[1,98,65,81,90],moved:false});
  assert.match(dropPlan(site,1,81,{overId:66,after:false}) as string,/최상위 항목 사이에서만/);
  assert.equal(dropPlan(site,1,65,{overId:65,after:false}),null);
  assert.equal(dropPlan(site,1,98,{overId:65,after:true}),null);
});

test('second-level pages reorder in their parent or move under another top-level item',()=>{
  assert.deepEqual(dropPlan(site,1,67,{overId:66,after:false}),{pageId:67,parentId:65,ids:[67,66],moved:false});
  assert.equal(dropPlan(site,1,66,{overId:67,after:false}),null);
  assert.deepEqual(dropPlan(site,1,66,{overId:99,after:true}),{pageId:66,parentId:98,ids:[99,66],moved:true});
  assert.deepEqual(dropPlan(site,1,66,{overId:81,after:false}),{pageId:66,parentId:81,ids:[66],moved:true});
  assert.equal(dropPlan(site,1,66,{overId:65,after:false}),null);
  assert.match(dropPlan(site,1,66,{overId:1,after:false}) as string,/홈\(첫 화면\) 아래/);
  assert.match(dropPlan([...site,removed],1,66,{overId:82,after:false}) as string,/구성에서 제거된/);
  assert.match(dropPlan(site,1,66,{overId:91,after:false}) as string,/하위 페이지는/);
  assert.match(dropPlan(site,1,91,{overId:99,after:false}) as string,/위치 버튼/);
  // Keyboard alternative: parents are the top-level items except home.
  assert.deepEqual(childParentOptions(site,66,1).map(o=>o.id),[65,98,81,90]);
});
