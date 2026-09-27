(() => {
  const form=document.querySelector('[data-writing-form]');if(!form)return;
  const kind=form.dataset.kind,state=form.querySelector('[data-save-state]');
  let timer=null,running=null,dirty=false,version=0,conflict=false;
  if(kind==='posts') {
    const host=form.querySelector('[data-rich-editor]'),s=window.AicaEditor.create(host);
    const render=()=>{
      form.querySelector('[data-preview-title]').textContent=form.querySelector('[name="title"]').value||'제목을 입력하세요';
      form.querySelector('[data-preview-category]').textContent=form.querySelector('[name="categoryId"] option:checked').textContent;
      window.AicaEditor.preview(form.querySelector('[data-live-post]'),s.q.getContents());
      form.querySelector('[data-word-count]').textContent=s.q.getText().trimEnd().length.toLocaleString('ko-KR')+' / 20,000자';
    };
    form.addEventListener('input',render);form.addEventListener('change',render);form.addEventListener('rich-change',render);render();
  }
  const say=(text,error=false)=>{state.textContent=text;state.classList.toggle('save-error',error);};
  function changed(){dirty=true;version++;clearTimeout(timer);if(!conflict){say('변경사항 있음 · 잠시 후 자동 저장');timer=setTimeout(()=>save('save',true),1800);}}
  document.addEventListener('visibilitychange',()=>{clearTimeout(timer);if(!document.hidden&&dirty&&!conflict)timer=setTimeout(()=>save('save',true),1800);});
  form.addEventListener('input',changed);form.addEventListener('change',changed);
  async function save(action,automatic=false){
    clearTimeout(timer);
    if(running){await running;if(automatic&&(!dirty||document.hidden))return;}
    if(conflict){say('다른 창에서 수정되었습니다. 작성 내용을 복사해 보관한 뒤 새로고침해 주세요.',true);return;}
    if(form.dataset.invalid||form.querySelector('[data-invalid]')){say('읽을 수 없는 본문이 있어 저장하지 않았습니다.',true);return;}
    if(form.dataset.uploadPending){say('파일 업로드가 끝나면 저장합니다.');timer=setTimeout(()=>save(action,automatic),1500);return;}
    if(!form.checkValidity()){
      say('제목과 입력 내용을 확인하세요.');
      if(!automatic){form.querySelectorAll('details').forEach(d=>{if(d.querySelector(':invalid'))d.open=true;});form.reportValidity();}return;
    }
    if(automatic&&(!dirty||document.hidden))return;
    const submitted=version,data=new FormData(form);data.set('action',action);data.set('saveIntent',automatic?'AUTOSAVE':'MANUAL_DRAFT');
    const controls=[...form.querySelectorAll('button[type="submit"]')];controls.forEach(b=>b.disabled=true);
    say(action==='publish'?'발행본 저장 중…':'임시저장 중…');
    running=(async()=>{
      try{
        const response=await fetch('/admin/'+kind+'/save-json',{method:'POST',body:data,headers:{'X-CSRF-TOKEN':document.querySelector('meta[name="csrf-token"]').content}});
        if(!response.headers.get('content-type')?.includes('application/json'))throw new Error('세션이 만료되었거나 저장할 수 없습니다. 작성 내용을 복사해 보관한 뒤 다시 로그인해 주세요.');
        const result=await response.json();if(!response.ok){if(result.error?.includes('다른')||result.error?.includes('버전'))conflict=true;throw new Error(result.error||'저장하지 못했습니다.');}
        form.querySelector('[name="id"]').value=result.id;form.querySelector('[name="revision"]').value=result.revision;
        document.querySelectorAll('[data-document-actions] [name="revision"]').forEach(input=>input.value=result.revision);
        const back=form.querySelector('[name="from"]')?.value;
        history.replaceState(null,'','/admin/'+kind+'/'+result.id+'/edit'+(back?'?from='+encodeURIComponent(back):''));
        if(result.slug)form.querySelector('[name="slug"]').value=result.slug;
        form.querySelector('[data-publication-state]').textContent=result.status==='PUBLISHED'?(result.pending?'발행본 있음 · 미반영 수정':'발행본 있음'):result.status==='PRIVATE'?'비공개':'임시저장';
        const actions=document.querySelector('[data-document-actions]');actions.hidden=false;
        actions.querySelectorAll('[data-document-action]').forEach(f=>{f.action='/admin/'+kind+'/'+result.id+'/'+f.dataset.documentAction;if(f.dataset.documentAction==='unpublish')f.hidden=result.status!=='PUBLISHED';});
        const review=actions.querySelector('[data-delete-review]');if(review)review.href='/admin/'+kind+'/'+result.id+'/delete-confirm';
        const link=actions.querySelector('[data-publication-link]');if(link){link.href='/admin/posts/'+result.id+'/publication';link.hidden=result.status!=='PUBLISHED';}
        if(version===submitted)dirty=false;
        const time=new Date().toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit',second:'2-digit',timeZone:document.querySelector('meta[name="operating-time-zone"]').content});
        say((action==='publish'?'발행본 저장 완료':'임시저장 완료')+' · '+time+(dirty?' · 새 변경사항 있음':''));
      }catch(error){say('저장 실패 · '+error.message,true);}
      finally{controls.forEach(b=>b.disabled=false);running=null;}
    })();
    await running;
    if(dirty&&version!==submitted&&!conflict)timer=setTimeout(()=>save('save',true),1800);
  }
  document.querySelectorAll('[data-document-action]').forEach(action=>action.addEventListener('submit',e=>{if(dirty||running||form.dataset.uploadPending){e.preventDefault();e.stopImmediatePropagation();say('저장을 완료한 뒤 다시 선택하세요.',true);}},true));
  document.querySelector('[data-delete-review]')?.addEventListener('click',e=>{if(dirty||running||form.dataset.uploadPending){e.preventDefault();say('저장을 완료한 뒤 삭제 영향을 확인하세요.',true);}});
  form.addEventListener('submit',e=>{e.preventDefault();save(e.submitter?.value||'save');});
  window.addEventListener('beforeunload',e=>{if(dirty||running||form.dataset.uploadPending){e.preventDefault();e.returnValue='';}});
  document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='s'){e.preventDefault();save('save');}});
  form.querySelectorAll('[data-preview-size]').forEach(button=>button.addEventListener('click',()=>{
    form.querySelector('.live-preview').classList.toggle('preview-mobile',button.dataset.previewSize==='mobile');
    form.querySelectorAll('[data-preview-size]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
  }));
  document.querySelector('[data-focus-toggle]')?.addEventListener('click',event=>{
    const focused=document.body.classList.toggle('writing-focus');event.currentTarget.setAttribute('aria-pressed',String(focused));event.currentTarget.textContent=focused?'메뉴 다시 보기':'집중 모드';
    const sidebar=document.querySelector('.sidebar');sidebar.inert=focused;sidebar.setAttribute('aria-hidden',String(focused));
  });
})();
