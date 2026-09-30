import type {Section,ComponentDefinition} from './types';
import {allTypesQuery} from './manualPosts.ts';

export const newBlockId=()=>`block_${crypto.randomUUID()}`;
// New POSTS blocks start in query mode with all types; the legacy category mode is not offered for new blocks.
const postsStart=(type:Section['type'],definition?:ComponentDefinition):Partial<Section>=>type==='POSTS'?{sourceMode:'query',query:allTypesQuery(definition?.postsQuery?.defaultLimit??6)}:{};
export const newSection=(type:Section['type'],definition?:ComponentDefinition):Section=>({...postsStart(type,definition),heading:'',body:'',bodyDoc:null,imageId:null,categoryId:null,link:'',label:'',visible:true,...definition?.defaults,id:newBlockId(),schemaVersion:definition?.schemaVersion??2,variation:definition?.defaultVariation??'default',type});
// Query arrays must be independent as well as the top-level block fields.
export const duplicateSection=(section:Section):Section=>({...structuredClone(section),id:newBlockId()});
export const changeSection=(sections:Section[],id:string,patch:Partial<Section>)=>sections.map(s=>s.id===id?{...s,...patch,id:s.id}:s);
export function moveSection(sections:Section[],index:number,offset:number):Section[] {
  const destination=index+offset;if(index<0||index>=sections.length||destination<0||destination>=sections.length)return sections;
  const next=[...sections];[next[index],next[destination]]=[next[destination],next[index]];return next;
}

export function removeSection(sections:Section[],id:string):{sections:Section[];selectedId:string|null} {
 const index=sections.findIndex(s=>s.id===id),next=sections.filter(s=>s.id!==id);
 return {sections:next,selectedId:next[Math.min(Math.max(index,0),next.length-1)]?.id??null};
}
export function insertDuplicate(sections:Section[],id:string):{sections:Section[];selectedId:string} {
 const index=sections.findIndex(s=>s.id===id);if(index<0)throw new Error('복제할 블록을 다시 선택하세요.');
 const copy=duplicateSection(sections[index]);return {sections:[...sections.slice(0,index+1),copy,...sections.slice(index+1)],selectedId:copy.id};
}
