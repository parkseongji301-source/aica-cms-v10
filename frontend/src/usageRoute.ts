// Server usage lists still carry legacy admin URLs; open the React screen for the same item instead.
// Returns null when React has no screen for the target, so the caller shows plain text.
export function usageRoute(href:string):string|null {
  let url:URL;
  try{url=new URL(href,'http://usage.local');}catch{return null;}
  const path=url.pathname.replace(/\/$/,'');
  const post=path.match(/^\/admin\/posts\/(\d+)(?:\/edit)?$/);if(post)return `/posts/${post[1]}/edit`;
  const page=path.match(/^\/admin\/pages\/(\d+)(?:\/edit)?$/);if(page)return `/pages/${page[1]}/edit`;
  if(/^\/admin\/menus$/.test(path)){const edit=url.searchParams.get('edit');return edit&&/^\d+$/.test(edit)?`/menus?edit=${edit}`:'/menus';}
  if(/^\/admin\/settings\/(basic|links|system)$/.test(path))return path.slice('/admin'.length);
  if(/^\/admin\/design\/(style|components|templates)$/.test(path))return path.slice('/admin'.length)+url.search;
  if(/^\/admin-next\//.test(path))return path.slice('/admin-next'.length)+url.search;
  return null;
}
