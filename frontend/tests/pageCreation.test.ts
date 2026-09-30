import test from 'node:test';
import assert from 'node:assert/strict';
import {pageDraftForm} from '../src/pageCreation.ts';

test('creates an empty unpublished draft without an existing page ID or revision',()=>{
  const form=pageDraftForm(' 교육생 후기 ',' Reviews-2026 ');
  assert.deepEqual(Object.fromEntries(form),{title:'교육생 후기',slug:'reviews-2026',sectionsJson:'[]',action:'save',saveIntent:'MANUAL_DRAFT'});
  assert.equal(form.has('id'),false);assert.equal(form.has('revision'),false);
});
test('blank optional address preserves server-side automatic generation',()=>{
  assert.equal(pageDraftForm('후기','  ').get('slug'),'');
});
test('form encoding preserves titles containing ampersands and cannot add request fields',()=>{
  const form=pageDraftForm('후기 & action=publish + 인터뷰','reviews');
  const parsed=new URLSearchParams(form.toString());
  assert.equal(parsed.get('title'),'후기 & action=publish + 인터뷰');
  assert.equal(parsed.get('action'),'save');assert.equal(parsed.getAll('action').length,1);
});
