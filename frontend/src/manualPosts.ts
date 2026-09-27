import type {PostsQuery,Section} from './types';

export const addManualPost=(ids:number[],id:number,max=20)=>ids.includes(id)||ids.length>=max?ids:[...ids,id];
export const removeManualPost=(ids:number[],id:number)=>ids.filter(value=>value!==id);
export function moveManualPost(ids:number[],id:number,offset:number):number[] {
 const index=ids.indexOf(id),to=index+offset;
 if(index<0||to<0||to>=ids.length)return ids;
 const next=[...ids];[next[index],next[to]]=[next[to],next[index]];return next;
}
export function postsSourcePatch(section:Section,mode:'category'|'query'|'manual',defaultQuery:PostsQuery):Partial<Section> {
 return {sourceMode:mode,query:section.query??(mode==='query'?defaultQuery:section.query),manual:section.manual??(mode==='manual'?{postIds:[]}:section.manual)};
}
