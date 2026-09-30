import test from 'node:test';
import assert from 'node:assert/strict';
import {composeWritingTemplate,isEmptyWritingDocument} from '../src/writingTemplateDocument.ts';
const template=JSON.stringify({ops:[{insert:'프로젝트 후기'},{insert:'\n',attributes:{header:1}},{insert:'[나의 경험]\n'}]});
test('append preserves existing text, formatting and media in order',()=>{
 const original={ops:[{insert:'기존 글',attributes:{bold:true}},{insert:'\n'},{insert:{aicaImage:{id:42,alt:'그림'}}},{insert:'\n'}]};
 const output=composeWritingTemplate(JSON.stringify(original),template,'append');
 assert.deepEqual(output.ops.slice(0,4),original.ops);
 assert.deepEqual(output.ops.slice(4),JSON.parse(template).ops);
 output.ops[0].insert='changed';assert.equal(original.ops[0].insert,'기존 글');
});
test('empty body applies template once without leading empty paragraphs',()=>{
 assert.deepEqual(composeWritingTemplate('{"ops":[{"insert":"\\n"}]}',template,'append'),JSON.parse(template));
 assert.equal(isEmptyWritingDocument('{"ops":[{"insert":{"divider":true}}]}'),false);
});
test('replace uses an independent copy and never alters the source',()=>{
 const a=composeWritingTemplate('{"ops":[{"insert":"기존\\n"}]}',template,'replace');
 a.ops[0].insert='edited';
 assert.equal(composeWritingTemplate('{"ops":[]}',template,'replace').ops[0].insert,'프로젝트 후기');
});
test('oversized combined body is rejected before the editor is changed',()=>{
 assert.throws(()=>composeWritingTemplate(JSON.stringify({ops:[{insert:'가'.repeat(20000)}]}),template,'append'),/제한/);
});
