// Server usage lists carry absolute admin URLs; open the React screen for the same item inside the app.
// Returns null when React has no screen for the target (legacy screens, categories), so the caller shows plain text.
export function usageRoute(href:string):string|null {
  let url:URL;
  try{url=new URL(href,'http://usage.local');}catch{return null;}
  const path=url.pathname.replace(/\/$/,'');
  if(/^\/admin\/legacy(\/|$)/.test(path))return null;
  const post=path.match(/^\/admin\/posts\/(\d+)(?:\/edit)?$/);if(post)return `/posts/${post[1]}/edit`;
  const page=path.match(/^\/admin\/pages\/(\d+)(?:\/edit)?$/);if(page)return `/pages/${page[1]}/edit`;
  if(/^\/admin\/menus$/.test(path)){const edit=url.searchParams.get('edit');return edit&&/^\d+$/.test(edit)?`/menus?edit=${edit}`:'/menus';}
  if(/^\/admin\/settings\/(basic|links|system)$/.test(path))return path.slice('/admin'.length);
  if(/^\/admin\/design\/(style|components|templates)$/.test(path))return path.slice('/admin'.length)+url.search;
  if(path==='/admin/trash')return '/trash'+url.search;
  return null;
}
