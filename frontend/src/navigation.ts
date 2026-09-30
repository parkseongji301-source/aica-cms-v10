import type {Bootstrap, Menu, ViewMode} from './types';

export type MenuEntry = {label:string;path:string;access?:'site'|'operations'|'templateManage'|'structure'|'permanentDelete'};
export const managementGroups:{label:string;items:MenuEntry[]}[] = [
  {label:'',items:[{label:'대시보드',path:'/dashboard'}]},
  {label:'페이지·메뉴',items:[{label:'사이트 구조',path:'/pages',access:'site'},{label:'메뉴 관리',path:'/menus',access:'structure'}]},
  {label:'공통 구조 관리',items:[{label:'공통 영역·블록 안내',path:'/design/components',access:'structure'},{label:'페이지 템플릿',path:'/design/templates',access:'templateManage'},{label:'글쓰기 템플릿',path:'/design/writing-templates',access:'structure'}]},
  {label:'자료 관리',items:[{label:'휴지통',path:'/trash',access:'permanentDelete'},{label:'미디어 관리',path:'/media'}]},
  {label:'운영 관리',items:[{label:'운영 계정 관리',path:'/accounts',access:'operations'},{label:'역할·권한 안내',path:'/roles',access:'operations'},{label:'활동 이력',path:'/activity',access:'operations'}]},
  {label:'사이트 설정',items:[{label:'기본 정보',path:'/settings/basic',access:'structure'},{label:'SNS / 외부 링크',path:'/settings/links',access:'structure'},{label:'시스템 설정',path:'/settings/system',access:'structure'}]}
];
export const pagePath = (id:number,blockId?:string|null)=>`/pages/${id}/edit`+(blockId==null?'':'?'+new URLSearchParams({block:blockId}));
export const pageOverviewPath = (id:number,blockId?:string|null)=>'/pages?'+new URLSearchParams({inspect:String(id),...(blockId==null?{}:{block:blockId})});
export function pageOverviewId(search:URLSearchParams):number|null {
  const value=search.get('inspect');
  return value!==null&&/^[1-9]\d*$/.test(value)&&Number.isSafeInteger(Number(value))?Number(value):null;
}
export const contentPath = (categoryId:number|null)=>categoryId===null?'/posts':`/posts?categoryId=${categoryId}`;
export const postEditorPath = (id:number)=>`/posts/${id}/edit`;
export function destination(menu:Menu):string {
  if(menu.kind==='PAGE'&&menu.targetId!==null)return pagePath(menu.targetId);
  if(menu.kind==='CATEGORY'&&menu.targetId!==null)return contentPath(menu.targetId);
  return `/menus?edit=${menu.id}`;
}
export const contentEntries:MenuEntry[]=[{label:'전체 콘텐츠',path:'/posts'}];
// Keep the existing style screen and access rules, without listing it in navigation.
export const entries:MenuEntry[]=[...contentEntries,...managementGroups.flatMap(g=>g.items),{label:'공통 스타일',path:'/design/style',access:'structure'}];
export const workspaceHome=(view:ViewMode)=>view==='structure'?'/posts':'/dashboard';
// Keep old links usable while showing each screen in its owning workspace.
export function workspaceView(path:string,requested:ViewMode):ViewMode {
  const pathname=path.split('?')[0];
  if(pathname==='/posts'||/^\/posts\/\d+\/edit$/.test(pathname))return 'structure';
  if(pathname==='/pages'||/^\/pages\/\d+\/edit$/.test(pathname))return requested;
  return 'manage';
}
export function canOpen(path:string,data:Bootstrap):boolean {
  if(/^\/posts\/\d+\/edit$/.test(path))return true; // PostService checks target ownership on every request.
  if(/^\/pages\/\d+\/edit$/.test(path))return data.permissions.site;
  const entry=entries.find(e=>e.path===path);
  return !!entry&&(!entry.access||!!data.permissions[entry.access]);
}
