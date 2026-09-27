import type {Section} from './types';
import {hasStableBlockId} from './blockNavigation.ts';

export type TemplateMode='append'|'replace';
export function applyTemplateBlocks(current:Section[],copies:Section[],mode:TemplateMode):Section[] {
 const oldIds=new Set(current.map(s=>s.id)),newIds=new Set(copies.map(s=>s.id));
 if(newIds.size!==copies.length||copies.some(s=>!hasStableBlockId(s.id)||oldIds.has(s.id)))throw new Error('새 블록 ID를 확인할 수 없습니다. 템플릿을 다시 불러오세요.');
 const result=mode==='append'?[...current,...copies]:copies;
 if(result.length>30)throw new Error('페이지 블록은 최대 30개입니다. 기존 블록을 줄이거나 교체를 선택하세요.');
 return structuredClone(result);
}
