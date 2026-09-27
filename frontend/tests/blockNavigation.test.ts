import test from 'node:test';
import assert from 'node:assert/strict';
import {addressableBlock,hasStableBlockId,pageOutline,selectedBlockId} from '../src/blockNavigation.ts';
import {newSection,duplicateSection,moveSection} from '../src/pageBlocks.ts';
import {pagePath} from '../src/navigation.ts';
import type {PageDocument,Section} from '../src/types.ts';

test('block URLs identify the same page and exact ID in both views',()=>{
 const a=newSection('HERO');
 for(const view of ['manage','structure']){
  const url=new URL('/admin-next'+pagePath(65,a.id),'http://localhost');url.searchParams.set('view',view);
  assert.equal(url.pathname,'/admin-next/pages/65/edit');assert.equal(url.searchParams.get('block'),a.id);
  assert.equal(selectedBlockId([a],url.searchParams.get('block')),a.id);
 }
 assert.equal(pagePath(65),'/pages/65/edit');
});
test('missing, deleted and empty explicit targets never fall back to another block',()=>{
 const a=newSection('HERO'),b=newSection('POSTS');
 assert.equal(selectedBlockId([a,b],null),a.id);
 for(const missing of ['',newSection('HERO').id,'old-block-position-1',b.id])assert.equal(selectedBlockId([a],missing),null);
 assert.equal(selectedBlockId([],a.id),null);
});
test('a hidden target and reordered target keep their identity; clone links are independent',()=>{
 const a={...newSection('HERO'),visible:false},b=newSection('POSTS'),copy=duplicateSection(a);
 const moved=moveSection([a,b,copy],0,1);
 assert.equal(selectedBlockId(moved,a.id),a.id);assert.equal(selectedBlockId(moved,copy.id),copy.id);
 assert.notEqual(a.id,copy.id);assert.equal(selectedBlockId([a,b],copy.id),null);
});
test('legacy or duplicate identifiers are unaddressable, without content or position matching',()=>{
 const valid=newSection('TEXT');const legacy={...valid,id:null,heading:'Same title'} as unknown as Section;
 assert.equal(hasStableBlockId(legacy.id),false);assert.equal(addressableBlock([legacy],legacy.id),false);
 assert.equal(selectedBlockId([legacy],null),null);
 assert.equal(selectedBlockId([valid,{...valid,body:'different'}],valid.id),null);
 assert.equal(selectedBlockId([legacy,valid],valid.id),valid.id);
});
test('structure is a projection of current draft metadata, not a second content copy',()=>{
 const a={...newSection('TEXT'),heading:'소개',body:'private draft body',visible:false};
 const doc={id:65,title:'인사교 소개',sections:[a]} as PageDocument;
 const outline=pageOutline(doc,[]);
 assert.deepEqual(outline.blocks[0],{kind:'block',pageId:65,blockId:a.id,label:'소개',type:'TEXT',visible:false});
 assert.equal(JSON.stringify(outline).includes('private draft body'),false);
 assert.equal(pageOutline({...doc,sections:[{...a,heading:'변경'}]},[]).blocks[0].blockId,a.id);
 const legacy=pageOutline({...doc,sections:[{...a,id:''}]},[]);assert.equal(legacy.blocks[0].blockId,null);assert.ok(legacy.issue);
});
