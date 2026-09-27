(() => {
  const editor = document.querySelector('[data-page-editor]');
  if (editor) {
    const field = editor.querySelector('#sections-data');
    const list = editor.querySelector('[data-sections]');
    const template = editor.querySelector('#section-template');
    const names = {HERO:'대표 문구',TEXT:'본문',IMAGE:'이미지',POSTS:'글 목록',CTA:'안내 버튼'};
    const read = () => [...list.children].map(card => {
      const s = {id:card.dataset.key,schemaVersion:Number(card.dataset.schemaVersion),variation:card.dataset.variation,type:card.dataset.type,...JSON.parse(card.dataset.postsSource||'{}')};
      card.querySelectorAll('[data-field]').forEach(input => {
        const key = input.dataset.field;
        s[key] = key === 'visible' ? input.checked : key.endsWith('Id') ? (input.value ? Number(input.value) : null) : input.value;
      }); return s;
    });
    const renderPreview = () => {
      const target=editor.querySelector('[data-live-sections]');if(!target)return;
      editor.querySelector('[data-preview-title]').textContent=editor.querySelector('[name="title"]').value||'페이지 제목';
      const existing=new Map([...target.children].map(n=>[n.dataset.key,n]));
      [...list.children].forEach((card,index)=>{
        
        if(!card.querySelector('[data-field="visible"]').checked)return;
        let block=existing.get(card.dataset.key);
        if(!block){block=document.createElement('section');block.className='preview-section';block.dataset.key=card.dataset.key;const heading=document.createElement('h2'),body=document.createElement('div'),image=document.createElement('img'),note=document.createElement('p'),button=document.createElement('span');heading.dataset.heading='';body.dataset.body='';image.dataset.image='';image.className='preview-attachment';note.className='preview-placeholder';button.className='preview-cta';block.append(heading,body,image,note,button);}
        existing.delete(card.dataset.key);target.append(block);
        block.className='preview-section type-'+card.dataset.type.toLowerCase()+' variation-'+card.dataset.variation;
        const val=k=>card.querySelector('[data-field="'+k+'"]')?.value||'';
        const heading=block.querySelector('[data-heading]');heading.textContent=val('heading');heading.hidden=!heading.textContent;
        const body=block.querySelector('[data-body]');body.hidden=card.dataset.type==='POSTS';if(!body.hidden)window.AicaEditor.preview(body,val('bodyDoc'),val('body'));
        const image=block.querySelector('[data-image]');image.hidden=card.dataset.type!=='IMAGE'||!val('imageId');if(!image.hidden){image.src='/admin/media/'+Number(val('imageId'))+'/file';image.alt=val('heading');}
        const note=block.querySelector('.preview-placeholder');note.hidden=card.dataset.type!=='POSTS';note.textContent=['query','manual'].includes(JSON.parse(card.dataset.postsSource||'{}').sourceMode)?'연결된 글 목록 · 실제 결과는 React 미리보기에서 확인하세요.':'글 목록 · '+(card.querySelector('[data-field="categoryId"] option:checked')?.textContent||'전체 카테고리');
        const button=block.querySelector('.preview-cta');button.hidden=!['HERO','CTA'].includes(card.dataset.type)||!val('label');button.textContent=val('label');
      });existing.forEach(n=>n.remove());
    };
    const sync = () => {
      if(editor.dataset.invalid)return;
      field.value = JSON.stringify(read());
      renderPreview();
      editor.querySelector('[data-sections-empty]').hidden = list.children.length !== 0;
      list.querySelectorAll('[data-section]').forEach((card,index) => {
        card.querySelector('[data-section-label]').textContent = String(index+1).padStart(2,'0')+' · '+names[card.dataset.type];
        card.querySelector('[data-section-move="up"]').disabled = index===0;
        card.querySelector('[data-section-move="down"]').disabled = index===list.children.length-1;
      });
    };
    const add = s => {
      const card = template.content.firstElementChild.cloneNode(true);
      card.dataset.type = s.type;
      card.dataset.key = s.id || ('block_'+crypto.randomUUID());
      card.dataset.schemaVersion = String(s.schemaVersion ?? 2);
      card.dataset.variation = s.variation ?? 'default';
      card.dataset.postsSource=JSON.stringify({sourceMode:s.sourceMode??null,query:s.query??null,manual:s.manual??null});
      if(s.type==='POSTS'&&['query','manual'].includes(s.sourceMode)) {
        const note=document.createElement('p');note.textContent='이 블록의 콘텐츠 조건·직접 선택은 React 관리자에서 편집합니다. 저장·발행 시 조건은 보존됩니다.';
        card.append(note);card.querySelector('[data-field="categoryId"]').disabled=true;
      }
      card.querySelectorAll('[data-field]').forEach(input => {
        const key=input.dataset.field;
        if(key==='visible') input.checked=s.visible!==false;
        else input.value=s[key] ?? '';
      });
      const allowed = {HERO:['body','link'],TEXT:['body'],IMAGE:['body','imageId'],POSTS:['categoryId'],CTA:['body','link']}[s.type] || [];
      card.querySelectorAll('[data-block-field]').forEach(group => {group.hidden=!allowed.includes(group.dataset.blockField);});
      card.querySelector('[data-section-remove]').addEventListener('click',() => {
        if(!window.confirm('이 섹션을 삭제할까요?'))return;
        card.remove();sync();editor.dispatchEvent(new Event('input',{bubbles:true}));
      });
      card.querySelectorAll('[data-section-move]').forEach(button=>button.addEventListener('click',()=>{
        if(button.dataset.sectionMove==='up' && card.previousElementSibling) list.insertBefore(card,card.previousElementSibling);
        else if(button.dataset.sectionMove==='down' && card.nextElementSibling) list.insertBefore(card.nextElementSibling,card);
        sync();editor.dispatchEvent(new Event('input',{bubbles:true}));
      }));
      list.append(card);
      window.AicaEditor.create(card.querySelector('[data-rich-editor]'));
    };
    try {JSON.parse(field.value).forEach(add);} catch {editor.dataset.invalid='true';editor.querySelector('[data-sections-empty]').textContent='섹션 내용을 읽을 수 없습니다. 페이지 목록에서 다시 열어 주세요.';}
    sync();
    editor.querySelectorAll('[data-add-section]').forEach(button=>button.addEventListener('click',()=>{
      if(list.children.length>=30){window.alert('섹션은 최대 30개까지 추가할 수 있습니다.');return;}
      add({type:button.dataset.addSection,visible:true});sync();editor.dispatchEvent(new Event('input',{bubbles:true}));
      list.lastElementChild.querySelector('input[data-field="heading"]').focus();
    }));
    editor.addEventListener('input',sync);editor.addEventListener('change',sync);editor.addEventListener('rich-change',sync);
    editor.addEventListener('submit',sync);
  }
  document.querySelectorAll('[data-reorder]').forEach(form=>{
    const list=form.querySelector('[data-order-items]');
    form.querySelectorAll('[data-move]').forEach(button=>button.addEventListener('click',()=>{
      const row=button.closest('.order-row');
      if(button.dataset.move==='up' && row.previousElementSibling)list.insertBefore(row,row.previousElementSibling);
      else if(button.dataset.move==='down' && row.nextElementSibling)list.insertBefore(row.nextElementSibling,row);
      form.querySelector('[data-order-status]').textContent='미저장 변경사항';
      form.querySelector('[data-order-save]').disabled=false;
      [...list.children].forEach((item,i)=>{item.querySelector('[data-move="up"]').disabled=i===0;item.querySelector('[data-move="down"]').disabled=i===list.children.length-1;});
      form.dispatchEvent(new Event('input',{bubbles:true}));
    }));
  });
  document.querySelectorAll('[data-menu-destination]').forEach(select=>{
    const update=()=>{const fields=select.form.querySelector('[data-link-fields]'),link=select.value==='LINK';fields.hidden=!link;fields.querySelectorAll('input').forEach(input=>{input.disabled=!link;input.required=link;});};
    select.addEventListener('change',update);update();
  });
  document.querySelectorAll('[data-menu-kind]').forEach(select=>{
    const update=()=>select.closest('form').querySelectorAll('[data-menu-target]').forEach(group=>{
      group.hidden=group.dataset.menuTarget!==select.value;
      group.querySelectorAll('input,select').forEach(input=>{input.disabled=group.hidden;});
    });
    select.addEventListener('change',update);update();
  });
  document.querySelectorAll('[data-quick-upload]').forEach(input=>{
    input.addEventListener('change',async()=>{
      const file=input.files[0];if(!file)return;
      const form=input.closest('form'),status=form.querySelector('[data-upload-status]'),picker=form.querySelector('[data-media-picker]');
      if(file.size>5*1024*1024){status.textContent='5MB 이하의 이미지를 선택하세요.';input.value='';return;}
      const data=new FormData();data.append('file',file);
      const csrf=document.querySelector('meta[name="csrf-token"]').content;
      status.textContent='이미지를 업로드하고 있습니다…';input.disabled=true;form.dataset.uploadPending='true';
      try {
        const response=await fetch('/admin/media/upload',{method:'POST',headers:{'X-CSRF-TOKEN':csrf},body:data});
        if(!response.ok || !response.headers.get('content-type')?.includes('application/json'))throw new Error('파일 형식과 크기를 확인하거나 다시 로그인해 주세요.');
        const m=await response.json(),label=document.createElement('label'),check=document.createElement('input'),image=document.createElement('img'),text=document.createElement('span');
        label.className='media-option';check.type='checkbox';check.name='mediaIds';check.value=m.id;check.checked=true;
        image.src='/admin/media/'+m.id+'/file';image.alt=m.alt;text.textContent=m.name;
        label.append(check,image,text);picker.append(label);status.textContent='업로드 완료 · 첨부 이미지로 선택했습니다.';
        form.dispatchEvent(new Event('input',{bubbles:true}));
      }catch(error){status.textContent='업로드하지 못했습니다. '+error.message;}
      finally{input.disabled=false;input.value='';delete form.dataset.uploadPending;}
    });
  });
  document.querySelectorAll('form[data-unsaved]').forEach(form=>{
    let dirty=false;
    form.addEventListener('input',()=>{dirty=true;});
    form.addEventListener('change',()=>{dirty=true;});
    form.addEventListener('submit',event=>{
      if(form.dataset.uploadPending){event.preventDefault();window.alert('이미지 업로드가 끝난 뒤 저장해 주세요.');return;}
      if(!event.submitter?.hasAttribute('data-preview'))dirty=false;
    });
    window.addEventListener('beforeunload',event=>{if(dirty){event.preventDefault();event.returnValue='';}});
  });
})();
