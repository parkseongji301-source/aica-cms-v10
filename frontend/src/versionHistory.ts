import type {Classification,ImageFile,RestaurantDetails,Section} from './types';
export type HistoryKind='posts'|'pages'|'page-templates';
export type VersionSummary={id:number;kind:HistoryKind;targetId:number;reason:string;sourceRevision:number;sourceVersionId:number|null;createdBy:number;creatorName:string;createdAt:string;baseline:boolean};
export type VersionSnapshot={title?:string;name?:string;slug?:string;content?:string;richContent?:string|null;categoryId?:number|null;categoryName?:string|null;classification?:Classification;restaurant?:RestaurantDetails|null;mediaIds?:number[];attachments?:ImageFile[];sections?:Section[];blocks?:Section[];description?:string;active?:boolean};
export type VersionDetail={version:VersionSummary;snapshot:VersionSnapshot;current:VersionSnapshot;currentRevision:number;canRestore:boolean};
export type VersionList={items:VersionSummary[];total:number;page:number;canRestore:boolean};
export type RestoreResult={versionId:number;revision:number;blockIds:Record<string,string>;alreadyApplied:boolean};
export const reasonLabel=(reason:string)=>({BASELINE_DRAFT:'도입 기준점 · 초안',BASELINE_PUBLISHED:'도입 기준점 · 공개본',BASELINE_TEMPLATE:'도입 기준점 · 템플릿',MANUAL_DRAFT:'수동 저장',PUBLISH:'게시',RESTORE_BACKUP:'복구 직전 보관',RESTORE:'복구'}[reason]||reason);
export function initialHistoryVersion(){const v=Number(new URLSearchParams(window.location.search).get('history'));return Number.isSafeInteger(v)&&v>0?v:null;}


export function historyRequested(){return new URLSearchParams(window.location.search).has("history");}
