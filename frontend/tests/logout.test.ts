import test from 'node:test';
import assert from 'node:assert/strict';
import {loggedOut,unsavedState} from '../src/logout.ts';

test('logout is allowed only when every visited editor is saved',()=>{
  assert.equal(unsavedState([]),'clean');
  assert.equal(unsavedState([()=>true,()=>true]),'clean');
  assert.equal(unsavedState([()=>true,()=>false]),'dirty');
});
test('an in-flight save or upload in any editor blocks logout before unsaved input',()=>{
  assert.equal(unsavedState([()=>false,()=>'busy']),'busy');
});
test('only the security redirect counts as a completed logout',()=>{
  assert.equal(loggedOut({type:'opaqueredirect',status:0}),true);
  assert.equal(loggedOut({type:'basic',status:302}),true);
  assert.equal(loggedOut({type:'basic',status:403}),false);
  assert.equal(loggedOut({type:'basic',status:200}),false);
});
