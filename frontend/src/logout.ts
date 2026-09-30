import type {EditorGuard} from './editorGuard';

export type UnsavedState='clean'|'dirty'|'busy';
// Visited editors stay mounted, so every registered guard counts, not only the visible one.
export function unsavedState(guards:EditorGuard[]):UnsavedState {
 let dirty=false;
 for(const guard of guards){const state=guard();if(state==='busy')return 'busy';if(state!==true)dirty=true;}
 return dirty?'dirty':'clean';
}
// Spring Security answers a successful logout with a redirect; a JSON/HTML error means the session was not ended.
export const loggedOut=(response:{type:string;status:number})=>response.type==='opaqueredirect'||(response.status>=300&&response.status<400);
