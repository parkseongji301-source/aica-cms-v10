import type {ComponentDefinition,PageDocument,PageTarget,Section} from './types';

export const hasStableBlockId=(id:unknown):id is string=>typeof id==='string'&&/^block_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(id);
export const addressableBlock=(sections:Section[],id:unknown)=>hasStableBlockId(id)&&sections.filter(s=>s.id===id).length===1;
export function selectedBlockId(sections:Section[],requested:string|null):string|null {
 if(requested!==null)return addressableBlock(sections,requested)?requested:null;
 return sections.find(s=>addressableBlock(sections,s.id))?.id??null;
}
export function pageOutline(doc:PageDocument,definitions:ComponentDefinition[]):PageTarget {
 const blocks=doc.sections.map(s=>({kind:'block' as const,label:s.heading||definitions.find(d=>d.type===s.type)?.label||s.type,pageId:doc.id,blockId:addressableBlock(doc.sections,s.id)?s.id:null,type:s.type,visible:s.visible}));
 return {kind:'page',label:doc.title,pageId:doc.id,blocks,issue:blocks.some(b=>b.blockId===null)?'ID가 없거나 중복된 블록은 직접 이동할 수 없습니다.':null};
}
