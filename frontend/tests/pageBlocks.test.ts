import test from 'node:test';
import assert from 'node:assert/strict';
import {newSection,duplicateSection,changeSection,moveSection,insertDuplicate,removeSection} from '../src/pageBlocks.ts';

test('new and duplicate blocks get distinct UUID v4 IDs without deriving them from data',()=>{
  const a=newSection('TEXT'),b=newSection('TEXT'),copy=duplicateSection(a);
  assert.equal(new Set([a.id,b.id,copy.id]).size,3);
  for(const s of [a,b,copy])assert.match(s.id,/^block_[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/);
  assert.deepEqual({...copy,id:a.id},a);assert.equal(a.schemaVersion,2);assert.equal(a.variation,'default');
});
test('reorder and edit follow identity without changing the published snapshot',()=>{
  const original=[newSection('HERO'),newSection('TEXT'),newSection('CTA')];
  const draft=moveSection(original,2,-1);assert.deepEqual(draft.map(s=>s.id),[original[0].id,original[2].id,original[1].id]);
  const changed=changeSection(draft,original[1].id,{heading:'수정',visible:false,id:'overwrite'});
  assert.equal(changed[2].id,original[1].id);assert.equal(changed[2].heading,'수정');assert.equal(original[1].heading,'');assert.equal(original[1].visible,true);
  assert.deepEqual(JSON.parse(JSON.stringify(changed)).map((s:{id:string})=>s.id),draft.map(s=>s.id));
});
test('a delayed upload addresses its block after a reorder and does not resurrect a deleted block',()=>{
  const a=newSection('IMAGE'),b=newSection('TEXT');const pendingId=a.id;
  const moved=moveSection([a,b],0,1);assert.equal(changeSection(moved,pendingId,{imageId:65})[1].imageId,65);
  assert.deepEqual(changeSection([b],pendingId,{imageId:65}),[b]);
});

test('duplicating a hidden centered hero preserves all content but subsequent edits are independent',()=>{
  const hero={...newSection('HERO'),heading:'원본',body:'본문',bodyDoc:'{"ops":[{"insert":"본문\\n"}]}',variation:'centered',visible:false,link:'/about',label:'안내'};
  const tail=newSection('POSTS'),result=insertDuplicate([hero,tail],hero.id),copy=result.sections[1];
  assert.equal(result.selectedId,copy.id);assert.notEqual(copy.id,hero.id);assert.deepEqual({...copy,id:hero.id},hero);
  const edited=changeSection(result.sections,copy.id,{heading:'복제본 수정',bodyDoc:'{"ops":[{"insert":"새 본문\\n"}]}',variation:'default',visible:true});
  assert.deepEqual(edited[0],hero);assert.equal(edited[1].heading,'복제본 수정');assert.equal(edited[2].id,tail.id);
  const moved=moveSection(edited,1,-1);assert.equal(moved[0].id,copy.id);assert.equal(moved[0].heading,'복제본 수정');assert.deepEqual(moved[1],hero);
});

test('deletion selects the next neighbor and later additions cannot reuse a removed ID',()=>{
  const a=newSection('TEXT'),b=newSection('IMAGE'),c=newSection('CTA');
  const middle=removeSection([a,b,c],b.id);assert.equal(middle.selectedId,c.id);assert.deepEqual(middle.sections,[a,c]);
  const last=removeSection(middle.sections,c.id);assert.equal(last.selectedId,a.id);
  const empty=removeSection(last.sections,a.id);assert.equal(empty.selectedId,null);assert.deepEqual(empty.sections,[]);
  const next=newSection('TEXT');assert.ok(![a.id,b.id,c.id].includes(next.id));assert.equal(next.variation,'default');
});

test('POSTS queries deep-copy arrays, remain attached on move, and are included in draft change detection',()=>{
 const source={...newSection('POSTS'),sourceMode:'query' as const,categoryId:11,query:{typeCode:'REVIEW',cohortIds:[6,7],topicIds:[21,22],sort:'LATEST' as const,limit:6}};
 const before=JSON.stringify(source),copy=duplicateSection(source);
 assert.deepEqual(copy.query,source.query);assert.notEqual(copy.id,source.id);
 copy.query!.cohortIds.push(8);copy.query!.topicIds.splice(0,1);copy.query!.limit=3;
 assert.equal(JSON.stringify(source),before);assert.notEqual(JSON.stringify(copy),before);
 const moved=moveSection([source,copy],1,-1);assert.equal(moved[1].id,source.id);assert.deepEqual(moved[1].query,source.query);
 const restored=JSON.parse(JSON.stringify(moved));assert.deepEqual(restored[0].query,copy.query);assert.equal(restored[1].categoryId,11);
});
