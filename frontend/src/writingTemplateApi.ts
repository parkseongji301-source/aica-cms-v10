import {get,send} from './api';
export type WritingTemplate={id:number;name:string;description:string;typeCode:'REVIEW';revision:number;updatedAt:string;updaterName:string};
export type WritingTemplateDocument={info:WritingTemplate;richContent:string;content:string;bodyHtml:string};
export type WritingTemplateInput={name:string;description:string;richContent:string;revision?:number};
export const writingTemplateBase='/writing-templates';
export const getWritingTemplate=(id:number,signal?:AbortSignal)=>get<WritingTemplateDocument>(writingTemplateBase+'/'+id,signal);
export const prepareWritingTemplate=(id:number,revision:number)=>send<WritingTemplateDocument>(`${writingTemplateBase}/${id}/prepare`,'POST',{revision});
export const saveWritingTemplate=(id:number|null,input:WritingTemplateInput)=>send<WritingTemplateDocument>(writingTemplateBase+'/manage'+(id===null?'':'/'+id),id===null?'POST':'PUT',input);
export const deleteWritingTemplate=(id:number,revision:number)=>send(writingTemplateBase+'/manage/'+id,'DELETE',{revision,confirmed:true});
