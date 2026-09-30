import test from 'node:test';
import assert from 'node:assert/strict';
import {deleteSequentially} from '../src/deleteBatch.ts';

const targets=[1,2,3].map(id=>({id,label:'항목 '+id,revision:10+id}));
test('partial failure retains item identity, continues valid targets, and never retries a delete',async()=>{
 const calls:number[]=[];
 const result=await deleteSequentially(targets,async target=>{calls.push(target.id);if(target.id===2)throw Object.assign(new Error('수정 충돌'),{status:409});});
 assert.deepEqual(calls,[1,2,3]);assert.deepEqual(result.succeeded,[1,3]);assert.deepEqual(result.failed,[{...targets[1],message:'수정 충돌'}]);
});
for(const status of [401,403])test('authentication/permission '+status+' stops remaining writes',async()=>{
 const calls:number[]=[];
 const result=await deleteSequentially(targets,async target=>{calls.push(target.id);throw Object.assign(new Error('접근 거부'),{status});});
 assert.deepEqual(calls,[1]);assert.equal(result.failed.length,3);assert.equal(result.succeeded.length,0);
});
test('uncertain network outcome is not retried and later items are skipped',async()=>{
 let calls=0;const result=await deleteSequentially(targets,async()=>{calls++;throw new TypeError('Failed to fetch');});
 assert.equal(calls,1);assert.equal(result.failed.length,3);
});
test('requests are sequential and preserve the revision reviewed before confirmation',async()=>{
 let running=0;const progress:number[]=[];
 const result=await deleteSequentially(targets,async target=>{assert.equal(++running,1);assert.equal(target.revision,10+target.id);await Promise.resolve();running--;},n=>progress.push(n));
 assert.deepEqual(result.succeeded,[1,2,3]);assert.deepEqual(progress,[1,2,3]);
});
