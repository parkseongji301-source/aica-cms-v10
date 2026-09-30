import test from 'node:test';
import assert from 'node:assert/strict';
import {childrenAllSelected,deletionOrder,homeCandidates,movedSiblings,pageLocation,pageTree,parentOptions,parentWarning,publishedChildren} from '../src/pageHierarchy.ts';
import {pageDraftForm} from '../src/pageCreation.ts';
import type {PageRow} from '../src/types.ts';

const page=(id:number,title:string,parentId:number|null,sortOrder:number,status='PUBLISHED',areaKind:'PAGE'|'GROUP'='PAGE'):PageRow=>
  ({id,title,slug:'p'+id,status,revision:1,pending:false,updatedAt:'2026-09-30T10:00:00',parentId,sortOrder,areaKind,contentTypeCode:null,menuVisible:false,menuLabel:null});
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

test('new pages send a parent only when one is chosen',()=>{
  assert.equal(pageDraftForm('후기','',[]).has('parentId'),false);
  assert.equal(pageDraftForm('후기','',[],65).get('parentId'),'65');
});
