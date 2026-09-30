import test from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync,readFileSync} from 'node:fs';
import {join} from 'node:path';
import {usageRoute} from '../src/usageRoute.ts';
import {routePath} from '../src/adminBase.ts';

test('server usage links open the matching React screen',()=>{
  assert.equal(usageRoute('/admin/posts/12/edit'),'/posts/12/edit');
  assert.equal(usageRoute('/admin/pages/65/edit'),'/pages/65/edit');
  assert.equal(usageRoute('/admin/menus?edit=34'),'/menus?edit=34');
  assert.equal(usageRoute('/admin/settings/basic'),'/settings/basic');
  assert.equal(usageRoute('/admin/design/style'),'/design/style');
  assert.equal(usageRoute('/admin/trash'),'/trash');
  assert.equal(usageRoute('/admin/design/templates?template=3'),'/design/templates?template=3');
  assert.equal(usageRoute('/admin/categories'),null);
  assert.equal(usageRoute('/admin/legacy/posts/12/edit'),null);
  assert.equal(usageRoute('https://example.com/admin/posts/1/edit'),'/posts/1/edit');
});
test('React routes are read relative to the /admin base',()=>{
  assert.equal(routePath('/admin'),'');
  assert.equal(routePath('/admin/'),'');
  assert.equal(routePath('/admin/posts/3/edit'),'/posts/3/edit');
  assert.equal(routePath('/administrator'),'/administrator');
});
// The React admin must finish every task without sending the operator to a legacy Thymeleaf screen.
// Only the app base, file delivery, upload and the page-creation form endpoint are shared server paths.
test('React sources do not link to legacy admin screens',()=>{
  const allowed=['/admin','/admin/media/','/admin/media/upload','/admin/pages/save-json'];
  const dir=new URL('../src/',import.meta.url);const found:string[]=[];
  for(const name of readdirSync(dir)){
    if(!/\.(ts|tsx)$/.test(name)||name==='usageRoute.ts')continue;
    const source=readFileSync(join(dir.pathname.replace(/^\/([A-Za-z]:)/,'$1'),name),'utf8');
    for(const match of source.matchAll(/[`'"](\/admin[^`'"$]*)/g))if(!allowed.includes(match[1]))found.push(name+': '+match[1]);
    if(name!=='adminBase.ts'&&source.includes("'/admin'"))found.push(name+': literal base (use ADMIN_BASE)');
  }
  assert.deepEqual(found,[]);
});
