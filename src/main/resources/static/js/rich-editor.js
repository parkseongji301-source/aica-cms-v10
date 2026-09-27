(() => {
  'use strict';
  if (!document.querySelector('[data-writing-form]')) return;
  const Q = window.Quill, Delta = Q.import('delta'), Embed = Q.import('blots/block/embed');
  const Parchment = Q.import('parchment');
  const colors = ['navy','blue','teal','red','purple','gray','yellow','white'];
  Q.register(new Parchment.ClassAttributor('color','rt-color',{scope:Parchment.Scope.INLINE,whitelist:colors}),true);
  Q.register(new Parchment.ClassAttributor('background','rt-background',{scope:Parchment.Scope.INLINE,whitelist:colors}),true);
  const Font = Q.import('attributors/class/font'); Font.whitelist=['sans','serif','mono']; Q.register(Font,true);
  const formats=['header','font','size','bold','italic','underline','strike','color','background','align','list','blockquote','code-block','link','aicaImage','aicaFile','aicaTable','divider'];
  const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
  const fileUrl=id=>'/admin/media/'+Number(id)+'/file';
  class ImageEmbed extends Embed {
    static blotName='aicaImage'; static tagName='figure'; static className='rt-image';
    static create(v) {
      const n=super.create();const value={id:Number(v.id),width:['25','50','75','100'].includes(String(v.width))?String(v.width):'100',align:['left','center','right'].includes(v.align)?v.align:'center',alt:String(v.alt||''),caption:String(v.caption||'')};
      n.dataset.value=JSON.stringify(value);n.classList.add('rt-width-'+value.width,'rt-align-'+value.align);n.contentEditable='false';n.tabIndex=0;n.setAttribute('role','button');n.setAttribute('aria-label','사진 설정: '+(value.alt||'본문 이미지'));
      const img=el('img');img.src=fileUrl(value.id);img.alt=value.alt;img.draggable=false;n.append(img);if(value.caption)n.append(el('figcaption','',value.caption));return n;
    }
    static value(n){return JSON.parse(n.dataset.value);}
  }
  class FileEmbed extends Embed {
    static blotName='aicaFile';static tagName='div';static className='rt-file';
    static create(v){const n=super.create(),value={id:Number(v.id),label:String(v.label||'첨부 파일')};n.dataset.value=JSON.stringify(value);n.contentEditable='false';n.tabIndex=0;n.setAttribute('role','button');n.setAttribute('aria-label','파일 설정: '+value.label);const a=el('a','',value.label+' ↓');a.href=fileUrl(value.id);a.setAttribute('download','');n.append(a);return n;}
    static value(n){return JSON.parse(n.dataset.value);}
  }
  class TableEmbed extends Embed {
    static blotName='aicaTable';static tagName='div';static className='rt-table';
    static create(v){const n=super.create();n.dataset.value=JSON.stringify(v);n.contentEditable='false';n.tabIndex=0;n.setAttribute('role','button');n.setAttribute('aria-label','표 편집');const table=el('table'),body=el('tbody');(v.rows||[]).forEach(row=>{const tr=el('tr');row.forEach(cell=>tr.append(el('td','',cell)));body.append(tr);});table.append(body);n.append(table);return n;}
    static value(n){return JSON.parse(n.dataset.value);}
  }
  class Divider extends Embed {static blotName='divider';static tagName='hr';static value(){return true;}}
  Q.register(ImageEmbed);Q.register(FileEmbed);Q.register(TableEmbed);Q.register(Divider);
  const instances=new WeakMap();let active=null,selected=null,linkRange=null;
  const library=document.querySelector('[data-library-dialog]'),linkDialog=document.querySelector('[data-link-dialog]'),embedDialog=document.querySelector('[data-embed-dialog]'),tableDialog=document.querySelector('[data-table-dialog]');
  document.querySelectorAll('[data-close-dialog]').forEach(b=>b.addEventListener('click',()=>b.closest('dialog').close('cancel')));
  document.querySelectorAll('dialog').forEach(d=>{d.addEventListener('click',e=>{if(e.target===d)d.close('cancel');});});
  function position(s){return Math.max(0,Math.min(s.range?.index??s.q.getLength()-1,s.q.getLength()-1));}
  function message(s,text,error=false){const box=s.host.querySelector('[data-editor-message]');box.hidden=!text;box.textContent=text;box.classList.toggle('is-error',error);}
  function insert(s,type,value,index=position(s)) {
    s.q.history.cutoff();
    const ops=new Delta().retain(index);if(index>0 && s.q.getText(index-1,1)!=='\n'){ops.insert('\n');index++;}
    ops.insert({[type]:value}).insert('\n');s.q.updateContents(ops,'user');s.q.setSelection(index+2,0,'silent');s.range={index:index+2,length:0};s.q.history.cutoff();return index;
  }
  function insertMedia(s,m,index){return insert(s,m.mime.startsWith('image/')?'aicaImage':'aicaFile',m.mime.startsWith('image/')?{id:Number(m.id),alt:m.alt||'',caption:'',width:'100',align:'center'}:{id:Number(m.id),label:m.name},index);}
  function replaceSelected(value){if(!selected)return;const {s,index,type}=selected;s.q.history.cutoff();s.q.updateContents(new Delta().retain(index).delete(1).insert({[type]:value}),'user');s.q.history.cutoff();selected.value=value;}
  function selectEmbed(s,node){
    const blot=Q.find(node);if(!blot)return;const type=blot.statics.blotName;
    selected={s,index:s.q.getIndex(blot),type,value:blot.statics.value(node)};active=s;
    if(type==='aicaTable'){openTable(selected.value.rows);return;}
    const image=type==='aicaImage',v=selected.value;
    embedDialog.querySelector('[data-embed-title]').textContent=image?'사진 설정':'첨부 파일 설정';
    embedDialog.querySelector('[data-image-options]').hidden=!image;embedDialog.querySelector('[data-file-options]').hidden=image;
    embedDialog.querySelector('[data-file-label]').disabled=image;embedDialog.querySelector('[data-file-label]').value=v.label||'';
    embedDialog.querySelector('[data-image-width]').value=v.width||'100';embedDialog.querySelector('[data-image-align]').value=v.align||'center';embedDialog.querySelector('[data-image-alt]').value=v.alt||'';embedDialog.querySelector('[data-image-caption]').value=v.caption||'';
    embedDialog.showModal();
  }
  embedDialog.querySelector('form').addEventListener('submit',()=>{
    const v={...selected.value};if(selected.type==='aicaImage'){v.width=embedDialog.querySelector('[data-image-width]').value;v.align=embedDialog.querySelector('[data-image-align]').value;v.alt=embedDialog.querySelector('[data-image-alt]').value;v.caption=embedDialog.querySelector('[data-image-caption]').value;}else v.label=embedDialog.querySelector('[data-file-label]').value;
    replaceSelected(v);
  });
  embedDialog.querySelector('[data-embed-remove]').addEventListener('click',()=>{selected.s.q.deleteText(selected.index,1,'user');embedDialog.close();});
  embedDialog.querySelectorAll('[data-embed-move]').forEach(b=>b.addEventListener('click',()=>{
    const {s,index,type,value}=selected,lines=s.q.getLines(),starts=lines.map(line=>s.q.getIndex(line));
    const before=starts.filter(n=>n<index),after=starts.filter(n=>n>index+1);
    let target=b.dataset.embedMove==='up'?before.at(-1):after[0];
    if(target===undefined)return;
    s.q.history.cutoff();s.q.deleteText(index,1,'user');if(target>index)target--;selected.index=insert(s,type,value,target);message(s,'사진·파일 위치를 이동했습니다.');embedDialog.close();
  }));
  function readTable(){return [...tableDialog.querySelectorAll('tr')].map(row=>[...row.querySelectorAll('textarea')].map(cell=>cell.value));}
  function tableGrid(rows){const grid=tableDialog.querySelector('[data-table-grid]');grid.replaceChildren();const table=el('table'),body=el('tbody');rows.forEach((row,r)=>{const tr=el('tr');row.forEach((cell,c)=>{const td=el('td'),input=el('textarea');input.rows=2;input.maxLength=500;input.value=cell;input.setAttribute('aria-label',(r+1)+'행 '+(c+1)+'열');td.append(input);tr.append(td);});body.append(tr);});table.append(body);grid.append(table);}
  function openTable(rows){const data=rows||Array.from({length:3},()=>['','','']);tableDialog.querySelector('[data-table-rows]').value=data.length;tableDialog.querySelector('[data-table-cols]').value=data[0].length;tableGrid(data);tableDialog.showModal();}
  tableDialog.querySelector('[data-table-resize]').addEventListener('click',()=>{const old=readTable(),rows=Math.max(1,Math.min(20,Number(tableDialog.querySelector('[data-table-rows]').value)||1)),cols=Math.max(1,Math.min(8,Number(tableDialog.querySelector('[data-table-cols]').value)||1));if((rows<old.length||cols<old[0].length)&&!window.confirm('크기를 줄이면 범위 밖의 셀 내용이 삭제됩니다. 계속할까요?'))return;tableGrid(Array.from({length:rows},(_,r)=>Array.from({length:cols},(_,c)=>old[r]?.[c]||'')));});
  tableDialog.querySelector('form').addEventListener('submit',()=>{const v={rows:readTable()};if(selected?.type==='aicaTable')replaceSelected(v);else insert(active,'aicaTable',v);});
  linkDialog.querySelector('form').addEventListener('submit',event=>{
    const url=linkDialog.querySelector('#link-url').value.trim();let parsed;try{parsed=new URL(url);}catch{event.preventDefault();return;}
    if(!['http:','https:'].includes(parsed.protocol)||parsed.username||parsed.password){event.preventDefault();linkDialog.querySelector('#link-url').setCustomValidity('http 또는 https 주소를 입력하세요.');linkDialog.querySelector('#link-url').reportValidity();return;}
    const range=linkRange,label=linkDialog.querySelector('#link-label').value||url;
    active.q.updateContents(new Delta().retain(range.index).delete(range.length).insert(label,{link:url}),'user');active.q.setSelection(range.index+label.length,0,'silent');
  });
  linkDialog.querySelector('#link-url').addEventListener('input',e=>e.target.setCustomValidity(''));
  library.querySelector('[data-library-search]').addEventListener('input',e=>library.querySelectorAll('[data-media-id]').forEach(b=>{b.hidden=!b.dataset.name.toLowerCase().includes(e.target.value.toLowerCase());}));
  library.addEventListener('click',e=>{const b=e.target.closest('[data-media-id]');if(!b)return;insertMedia(active,{id:b.dataset.mediaId,name:b.dataset.name,alt:b.dataset.alt,mime:b.dataset.mime});library.close();});
  function addToLibrary(m){if(library.querySelector('[data-media-id="'+Number(m.id)+'"]'))return;const b=el('button','library-item');b.type='button';Object.assign(b.dataset,{mediaId:m.id,name:m.name,alt:m.alt,mime:m.mime});if(m.mime.startsWith('image/')){const img=el('img');img.src=fileUrl(m.id);img.alt=m.alt;b.append(img);}else b.append(el('span','file-symbol','문서 ↓'));b.append(el('span','',m.name));library.querySelector('.library-items').prepend(b);library.querySelector('[data-library-empty]')?.remove();document.querySelectorAll('select[data-field="imageId"]').forEach(select=>{if(m.mime.startsWith('image/')){const option=el('option','',m.name);option.value=m.id;select.append(option);}});}
  async function upload(s,files,index=position(s)) {
    if(s.uploadAnchor){message(s,'현재 업로드가 끝난 뒤 추가해 주세요.',true);return;}
    const form=s.host.closest('form');s.uploadAnchor={index};form.dataset.uploadPending=String(Number(form.dataset.uploadPending||0)+1);
    for(const file of files){
      if(file.size>5*1024*1024){message(s,file.name+': 파일당 5MB 이하로 올려 주세요.',true);continue;}
      message(s,file.name+' 업로드 중…');const data=new FormData();data.append('file',file);
      try {
        const res=await fetch('/admin/media/upload',{method:'POST',body:data,headers:{'X-CSRF-TOKEN':document.querySelector('meta[name="csrf-token"]').content}});
        if(!res.headers.get('content-type')?.includes('application/json'))throw new Error('업로드할 수 없습니다. 로그인 상태를 확인하세요.');
        const m=await res.json();if(!res.ok)throw new Error(m.error||'업로드하지 못했습니다.');const at=insertMedia(s,m,s.uploadAnchor.index);s.uploadAnchor.index=at+2;addToLibrary(m);document.dispatchEvent(new CustomEvent('media-uploaded',{detail:m}));message(s,'본문에 삽입했습니다. 사진이나 파일을 눌러 설정할 수 있습니다.');
      }catch(error){message(s,'업로드 실패 · '+error.message,true);}
    }
    s.uploadAnchor=null;const pending=Number(form.dataset.uploadPending)-1;if(pending)form.dataset.uploadPending=String(pending);else delete form.dataset.uploadPending;
    form.dispatchEvent(new Event('input',{bubbles:true}));
  }
  library.querySelector('[data-library-upload]').addEventListener('change',async e=>{if(!active||!e.target.files.length)return;const files=[...e.target.files];library.close();await upload(active,files);e.target.value='';});
  document.addEventListener('media-uploaded',e=>addToLibrary(e.detail));
  function create(host){
    if(instances.has(host))return instances.get(host);
    const doc=host.querySelector('[data-document]'),plain=host.querySelector('[data-plain]');
    host.prepend(document.querySelector('#rich-tools').content.cloneNode(true));
    const area=el('div','rich-editor-area');host.append(area);
    const q=new Q(area,{theme:null,formats,placeholder:'여기에 내용을 작성하세요…',modules:{toolbar:false,history:{delay:600,maxStack:150,userOnly:true}}});
    q.root.setAttribute('role','textbox');q.root.setAttribute('aria-label',host.dataset.label||'본문');q.root.setAttribute('aria-multiline','true');q.root.setAttribute('spellcheck','false');
    const s={host,q,doc,plain,range:{index:0,length:0},uploadAnchor:null};instances.set(host,s);
    try {if(doc.value.trim())q.setContents(JSON.parse(doc.value),'silent');else {q.setText(plain.value||'','silent');const legacy=host.closest('form').querySelector('[data-legacy-media]');legacy?.querySelectorAll('[data-id]').forEach(m=>insertMedia(s,{id:m.dataset.id,mime:m.dataset.mime,name:m.dataset.name,alt:m.dataset.alt},q.getLength()-1));}}
    catch {host.dataset.invalid='true';q.disable();message(s,'저장된 본문을 읽을 수 없습니다. 내용을 보호하기 위해 저장을 중지했습니다.',true);}
    q.history.clear();
    const sync=()=>{if(host.dataset.invalid)return;doc.value=JSON.stringify(q.getContents());plain.value=q.getText().trimEnd();};sync();
    q.on('text-change',(delta,old,source)=>{if(s.uploadAnchor)s.uploadAnchor.index=delta.transformPosition(s.uploadAnchor.index,true);sync();host.dispatchEvent(new CustomEvent('rich-change',{bubbles:true}));if(source!=='silent')host.dispatchEvent(new Event('input',{bubbles:true}));});
    const updateTools=()=>{const fmt=q.getFormat(s.range.index,s.range.length);host.querySelectorAll('[data-format]').forEach(control=>{const key=control.dataset.format;if(control.tagName==='SELECT')control.value=fmt[key]===undefined?'':String(fmt[key]);else {const pressed=control.dataset.value?fmt[key]===control.dataset.value:Boolean(fmt[key]);control.setAttribute('aria-pressed',String(pressed));}});};
    q.on('selection-change',range=>{if(range){s.range=range;active=s;updateTools();}});
    host.querySelectorAll('button[data-format],button[data-action]').forEach(b=>b.addEventListener('mousedown',e=>e.preventDefault()));
    host.querySelectorAll('[data-format]').forEach(control=>control.addEventListener(control.tagName==='SELECT'?'change':'click',()=>{
      const range=s.range,key=control.dataset.format,current=q.getFormat(range.index,range.length)[key];let value=control.tagName==='SELECT'?control.value:control.dataset.value||!current;
      if(control.dataset.value && current===value)value=false;if(key==='header' && value)value=Number(value);if(key==='code-block' && value)value='plain';
      q.focus();q.setSelection(range,'silent');q.format(key,value||false,'user');updateTools();
    }));
    host.querySelectorAll('[data-action]').forEach(b=>b.addEventListener('click',()=>{
      active=s;selected=null;const action=b.dataset.action;
      if(action==='upload')host.querySelector('[data-editor-upload]').click();
      else if(action==='library')library.showModal();
      else if(action==='table')openTable();
      else if(action==='divider')insert(s,'divider',true);
      else if(action==='link'){linkRange={...s.range};linkDialog.querySelector('#link-url').value=q.getFormat(s.range).link||'';linkDialog.querySelector('#link-label').value=q.getText(s.range.index,s.range.length);linkDialog.showModal();}
      else if(action==='clean'){
        const [line,offset]=q.getLine(s.range.index),start=s.range.length?s.range.index:s.range.index-offset,length=s.range.length||line.length();
        const delta=new Delta().retain(start);q.getContents(start,length).ops.forEach(op=>{const count=typeof op.insert==='string'?op.insert.length:1;delta.retain(count,typeof op.insert==='string'?Object.fromEntries(Object.keys(op.attributes||{}).map(k=>[k,null])):undefined);});q.updateContents(delta,'user');
      }
      else q.history[action]();
    }));
    host.querySelector('[data-editor-upload]').addEventListener('change',e=>{upload(s,[...e.target.files]);e.target.value='';});
    q.root.addEventListener('paste',e=>{const files=[...e.clipboardData.files];if(files.length){e.preventDefault();e.stopImmediatePropagation();upload(s,files);}},true);
    q.root.addEventListener('dragover',e=>{if(e.dataTransfer.types.includes('Files')){e.preventDefault();q.root.classList.add('drag-over');}});
    q.root.addEventListener('dragleave',()=>q.root.classList.remove('drag-over'));
    q.root.addEventListener('drop',e=>{q.root.classList.remove('drag-over');if(!e.dataTransfer.files.length)return;e.preventDefault();e.stopImmediatePropagation();let index=position(s);const range=document.caretRangeFromPoint?.(e.clientX,e.clientY);if(range&&q.root.contains(range.startContainer)){const blot=Q.find(range.startContainer,true);if(blot)index=Math.min(q.getLength()-1,q.getIndex(blot)+range.startOffset);}upload(s,[...e.dataTransfer.files],index);},true);
    const open=e=>{const node=e.target.closest('.rt-image,.rt-file,.rt-table');if(node){e.preventDefault();selectEmbed(s,node);}};
    q.root.addEventListener('click',open);q.root.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target!==q.root&&e.target.closest('.rt-image,.rt-file,.rt-table'))open(e);});
    updateTools();return s;
  }
  function preview(target,doc,plain){
    let q=instances.get(target);if(!q){q=new Q(target,{theme:null,readOnly:true,formats,modules:{toolbar:false}});instances.set(target,q);q.root.classList.add('rich-content');q.root.removeAttribute('contenteditable');}
    if(doc){try{q.setContents(typeof doc==='string'?JSON.parse(doc):doc,'silent');}catch{q.setText('본문을 읽을 수 없습니다.','silent');}}else q.setText(plain||'','silent');
    q.root.querySelectorAll('[role="button"]').forEach(n=>{n.removeAttribute('role');n.removeAttribute('tabindex');n.removeAttribute('aria-label');});
  }
  window.AicaEditor={create,preview,get:host=>instances.get(host)};
})();
