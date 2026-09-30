import test from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync,readFileSync} from 'node:fs';
import {join} from 'node:path';
import {usageRoute} from '../src/usageRoute.ts';

test('server usage links open the matching React screen',()=>{
  assert.equal(usageRoute('/admin/posts/12/edit'),'/posts/12/edit');
  assert.equal(usageRoute('/admin/pages/65/edit'),'/pages/65/edit');
  assert.equal(usageRoute('/admin/menus?edit=34'),'/menus?edit=34');
  assert.equal(usageRoute('/admin/settings/basic'),'/settings/basic');
  assert.equal(usageRoute('/admin/design/style'),'/design/style');
  assert.equal(usageRoute('/admin-next/trash'),'/trash');
  assert.equal(usageRoute('/admin-next/design/templates?template=3'),'/design/templates?template=3');
  assert.equal(usageRoute('/admin/categories'),null);
  assert.equal(usageRoute('https://example.com/admin/posts/1/edit'),'/posts/1/edit');
});
// The React admin must finish every task without sending the operator to a legacy Thymeleaf screen.
// Only file delivery, upload and the page-creation form endpoint are shared server paths.
test('React sources do not link to legacy admin screens',()=>{
  const allowed=['/admin/media/','/admin/media/upload','/admin/pages/save-json'];
  const dir=new URL('../src/',import.meta.url);const found:string[]=[];
  for(const name of readdirSync(dir)){
    if(!/\.(ts|tsx)$/.test(name)||name==='usageRoute.ts')continue;
    const source=readFileSync(join(dir.pathname.replace(/^\/([A-Za-z]:)/,'$1'),name),'utf8');
    for(const match of source.matchAll(/[`'"](\/admin(?!-next)[^`'"$]*)/g))if(!allowed.includes(match[1]))found.push(name+': '+match[1]);
  }
  assert.deepEqual(found,[]);
});
