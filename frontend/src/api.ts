import {restaurantPayload} from './restaurantFields';
import type {Bootstrap, PageDocument, PreviewDocument, ImageFile,PostDocument,PostPreview,ClassificationCatalog,PostPublication,ClassificationSelection} from './types';
import {classificationSelection} from './classification';
let csrf: Bootstrap['csrf']|null = null;
export class ApiError extends Error {constructor(public status:number, message:string,public code=''){super(message);}}
export async function request<T>(path:string,options:RequestInit = {}):Promise<T> {
  const headers = new Headers(options.headers);
  if(options.method && options.method !== 'GET' && csrf) headers.set(csrf.headerName,csrf.token);
  const response = await fetch(path,{...options,headers,credentials:'same-origin',cache:'no-store'});
  if(!response.headers.get('content-type')?.includes('application/json')) throw new ApiError(response.status===200?401:response.status,'로그인 상태를 확인한 뒤 다시 시도하세요.');
  const data = await response.json();
  if(!response.ok)throw new ApiError(response.status,data.message || data.error || '요청을 처리하지 못했습니다.',data.code);
  return data;
}
const base='/api/admin/next';

export const bootstrap=async()=>{const data=await request<Bootstrap>(base+'/bootstrap');csrf=data.csrf;return data;};
export const getPage=(id:number)=>request<PageDocument>(`${base}/pages/${id}`);
export const savePage=(page:PageDocument,saveIntent:'AUTOSAVE'|'MANUAL_DRAFT'='MANUAL_DRAFT')=>request<PageDocument>(`${base}/pages/${page.id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({saveIntent,title:page.title,revision:page.revision,sections:page.sections})});
export const previewPage=(page:PageDocument,signal?:AbortSignal)=>request<PreviewDocument>(`${base}/pages/${page.id}/preview`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:page.title,sections:page.sections}),signal});
export const uploadImage=(file:File)=>{const data=new FormData();data.append('file',file);data.append('imageOnly','true');return request<ImageFile>('/admin/media/upload',{method:'POST',body:data});};

export const get = <T>(path:string,signal?:AbortSignal)=>request<T>(base+path,{signal});
export const send = <T>(path:string,method:string,body?:unknown)=>request<T>(base+path,{method,...(body===undefined?{}:{headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})});
export const uploadMedia = (file:File)=>{const body=new FormData();body.append('file',file);return request<ImageFile>(base+'/media',{method:'POST',body});};
export const getPost=(id:number,signal?:AbortSignal)=>get<PostDocument>(`/posts/${id}`,signal);
export const createPost=(title:string,classification:ClassificationSelection)=>send<PostDocument>('/posts','POST',{
  saveIntent:'AUTOSAVE',title,content:'',richContent:null,categoryId:null,mediaIds:[],classification:classificationSelection(classification)
});
export const getClassifications=(signal?:AbortSignal)=>get<ClassificationCatalog>('/classifications',signal);
export const getPublication=(id:number,signal?:AbortSignal)=>get<PostPublication>(`/posts/${id}/publication`,signal);
export const savePost=(post:PostDocument,saveIntent:'AUTOSAVE'|'MANUAL_DRAFT'='MANUAL_DRAFT')=>send<PostDocument>(`/posts/${post.id}`,'PUT',{
  saveIntent,revision:post.revision,title:post.title,content:post.content,richContent:post.richContent,categoryId:post.categoryId,mediaIds:post.mediaIds,classification:classificationSelection(post.classification),...restaurantPayload(post)
});
export const previewPost=(post:PostDocument,signal?:AbortSignal)=>request<PostPreview>(`${base}/posts/${post.id}/preview`,{
  method:'POST',headers:{'Content-Type':'application/json'},signal,
  body:JSON.stringify({title:post.title,content:post.content,richContent:post.richContent,mediaIds:post.mediaIds,classification:classificationSelection(post.classification),...restaurantPayload(post)})
});
