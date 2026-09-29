import test from 'node:test';
import assert from 'node:assert/strict';
import {rememberPostOrigin} from '../src/contentNavigation.ts';

test('each editor returns to its own list conditions even after another list was visited',()=>{
  const first={path:'/posts',query:'?reviewSection=life&q=교육&status=DRAFT&page=2'};
  const second={path:'/posts',query:'?faqSection=class&status=PUBLISHED'};
  let origins=rememberPostOrigin({},first,{path:'/posts/1/edit',query:'?reviewSection=life'});
  origins=rememberPostOrigin(origins,second,{path:'/posts/2/edit',query:'?faqSection=class'});
  assert.equal(origins['/posts/1/edit'],first.path+first.query);
  assert.equal(origins['/posts/2/edit'],second.path+second.query);
  assert.equal(rememberPostOrigin(origins,{path:'/dashboard',query:''},{path:'/posts/1/edit',query:''}),origins);
  origins=rememberPostOrigin(origins,second,{path:'/posts/1/edit',query:''});
  assert.equal(origins['/posts/1/edit'],second.path+second.query);
});
