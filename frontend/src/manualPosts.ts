import type {PostsQuery,Section} from './types';

export const addManualPost=(ids:number[],id:number,max=20)=>ids.includes(id)||ids.length>=max?ids:[...ids,id];
export const removeManualPost=(ids:number[],id:number)=>ids.filter(value=>value!==id);
export function moveManualPost(ids:number[],id:number,offset:number):number[] {
 const index=ids.indexOf(id),to=index+offset;
 if(index<0||to<0||to>=ids.length)return ids;
 const next=[...ids];[next[index],next[to]]=[next[to],next[index]];return next;
}
export function postsSourcePatch(section:Section,mode:'category'|'query'|'manual',defaultQuery:PostsQuery):Partial<Section> {
 // Leaving the old category mode never wakes up a condition stored alongside it: it starts from all types.
 if(mode==='query'&&isLegacyCategoryBlock(section))return {sourceMode:'query',categoryId:null,query:allTypesQuery(defaultQuery.limit),manual:section.manual};
 return {sourceMode:mode,query:section.query??(mode==='query'?defaultQuery:section.query),manual:section.manual??(mode==='manual'?{postIds:[]}:section.manual)};
}
/** Legacy categories are retired: this mode is kept readable, but new POSTS blocks use query mode. */
export const isLegacyCategoryBlock=(section:Section)=>section.type==='POSTS'&&(section.sourceMode==null||section.sourceMode==='category');
export const allTypesQuery=(limit=6):PostsQuery=>({typeCode:null,cohortIds:[],topicIds:[],sort:'LATEST',limit});
/**
 * The old category mode without a category lists every published post, newest first, 6 at a time.
 * Query mode with all types and limit 6 means exactly the same; a block with a category needs a person to decide.
 */
export function legacyToAllTypes(section:Section):Partial<Section>|null {
 if(!isLegacyCategoryBlock(section)||section.categoryId!=null)return null;
 return {sourceMode:'query',categoryId:null,query:allTypesQuery(6)};
}
