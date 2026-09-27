import type {PostDocument} from './types';
import {classificationSelection} from './classification.ts';

/** Legacy attachments enter the existing Delta representation only in editor memory. */
export function editablePost(source:PostDocument):PostDocument {
  source={...source,restaurant:source.restaurant??(source.classification.typeCode==='RESTAURANT'?{address:''}:null)};
  if(source.richContent?.trim())return source;
  const ops:{insert:string|Record<string,unknown>}[]=[{insert:source.content.endsWith('\n')?source.content:source.content+'\n'}];
  for(const file of source.attachments) {
    ops.push({insert:file.mime.startsWith('image/')
      ?{aicaImage:{id:file.id,alt:file.alt||'',caption:'',width:'100',align:'center'}}
      :{aicaFile:{id:file.id,label:file.name}}},{insert:'\n'});
  }
  return {...source,richContent:JSON.stringify({ops})};
}

export const postFingerprint=(post:PostDocument)=>JSON.stringify({
  title:post.title,categoryId:post.categoryId,content:post.content,richContent:post.richContent,classification:classificationSelection(post.classification),restaurant:post.restaurant?.address??null
});
