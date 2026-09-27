import type {Bootstrap, Menu} from './types';

export type MenuEntry = {label:string;path:string;access?:'site'|'operations'|'templateManage'|'structure'};
export const managementGroups:{label:string;items:MenuEntry[]}[] = [
  {label:'',items:[{label:'대시보드',path:'/dashboard'}]},
  {label:'콘텐츠 관리',items:[{label:'콘텐츠 목록',path:'/posts'},{label:'미디어 관리',path:'/media'}]},
  {label:'페이지 관리',items:[{label:'전체 페이지 현황',path:'/pages',access:'site'}]},
  {label:'',items:[{label:'메뉴 관리',path:'/menus',access:'structure'}]},
  {label:'디자인 관리',items:[{label:'공통 스타일',path:'/design/style',access:'structure'},{label:'공통 컴포넌트',path:'/design/components',access:'structure'},{label:'공용 템플릿',path:'/design/templates',access:'templateManage'}]},
  {label:'운영 관리',items:[{label:'운영 계정 관리',path:'/accounts',access:'operations'},{label:'역할 / 권한 관리',path:'/roles',access:'operations'},{label:'활동 이력',path:'/activity',access:'operations'}]},
  {label:'사이트 설정',items:[{label:'기본 정보',path:'/settings/basic',access:'structure'},{label:'SNS / 외부 링크',path:'/settings/links',access:'structure'},{label:'시스템 설정',path:'/settings/system',access:'structure'}]}
];
export const pagePath = (id:number,blockId?:string|null)=>`/pages/${id}/edit`+(blockId==null?'':'?'+new URLSearchParams({block:blockId}));
export const contentPath = (categoryId:number|null)=>categoryId===null?'/posts':`/posts?categoryId=${categoryId}`;
export const postEditorPath = (id:number)=>`/posts/${id}/edit`;
export function destination(menu:Menu):string {
  if(menu.kind==='PAGE'&&menu.targetId!==null)return pagePath(menu.targetId);
  if(menu.kind==='CATEGORY'&&menu.targetId!==null)return contentPath(menu.targetId);
  return `/menus?edit=${menu.id}`;
}
export const entries=managementGroups.flatMap(g=>g.items);
export function canOpen(path:string,data:Bootstrap):boolean {
  if(/^\/posts\/\d+\/edit$/.test(path))return true; // PostService checks target ownership on every request.
  if(/^\/pages\/\d+\/edit$/.test(path))return data.permissions.site;
  const entry=entries.find(e=>e.path===path);
  return !!entry&&(!entry.access||!!data.permissions[entry.access]);
}
