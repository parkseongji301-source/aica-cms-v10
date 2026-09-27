import {useCallback,useEffect,useState} from 'react';
import type {EditorGuard} from './editorGuard';
import type {Category,PostDocument,ClassificationCatalog} from './types';
import {getPost,getClassifications} from './api';
import {ContentEditor} from './ContentEditor';
import {Feedback,messageOf} from './ui';

export function ContentPanel({id,active,categories,onLoaded,onSaved,onList,onMediaChange,registerGuard,canPublish}:{
  registerGuard:(path:string,guard:EditorGuard|null)=>void;canPublish:boolean;id:number;active:boolean;categories:Category[];onLoaded:(post:PostDocument)=>void;
  onSaved:(post:PostDocument)=>void;onList:()=>void;onMediaChange:()=>void;
}) {
  const onGuard=useCallback((guard:EditorGuard|null)=>registerGuard('/posts/'+id+'/edit',guard),[id,registerGuard]);
  const [document,setDocument]=useState<PostDocument|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  const [catalog,setCatalog]=useState<ClassificationCatalog|null>(null);
  useEffect(()=>{
    if(document||!active)return;
    const controller=new AbortController();
    void Promise.all([getPost(id,controller.signal),getClassifications(controller.signal)]).then(([post,terms])=>{if(!controller.signal.aborted){setCatalog(terms);setDocument(post);onLoaded(post);}})
      .catch(e=>{if(!controller.signal.aborted)setError(messageOf(e));});
    return()=>controller.abort();
  },[id,active,document,retry]);
  return document&&catalog?<ContentEditor onGuard={onGuard} canPublish={canPublish} initial={document} catalog={catalog} categories={categories} active={active} onSaved={onSaved} onList={onList} onMediaChange={onMediaChange}/>
    :<><Feedback error={error} loading={!error}/>{error&&<button onClick={()=>{setError('');setRetry(n=>n+1);}}>다시 시도</button>}</>;
}
