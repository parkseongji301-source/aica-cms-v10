import {useEffect,useRef,useState} from 'react';
import type {ReactNode} from 'react';
import Quill,{Delta} from 'quill';
import type {Parchment,Range} from 'quill';
import type {ImageFile} from './types';
import {get,uploadMedia} from './api';
import {messageOf} from './ui';

type EmbedKind='aicaImage'|'aicaFile'|'aicaTable';
type EmbedValue={id?:number;width?:string;align?:string;alt?:string;caption?:string;label?:string;rows?:string[][]};
type Selection={blot:Parchment.Blot;kind:EmbedKind;value:EmbedValue};
type Props={editor:Quill|null;onUploadState?:(busy:boolean)=>void;onMediaChange?:()=>void;extraTools?:ReactNode};
const accepted='.jpg,.jpeg,.png,.pdf,.txt,.docx,.xlsx,.pptx,.hwp';

/** Tools operate on the existing allowlisted Delta embeds and the shared media API. */
export function RichContentTools({editor:q,onUploadState,onMediaChange,extraTools}:Props) {
  const [modal,setModal]=useState<'library'|'link'|'embed'|'table'|null>(null);
  const [library,setLibrary]=useState<ImageFile[]>([]),[query,setQuery]=useState(''),[loading,setLoading]=useState(false);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const [options,setOptions]=useState<EmbedValue>({}),[rows,setRows]=useState<string[][]>([['','',''],['','',''],['','','']]);
  const [rowCount,setRowCount]=useState(3),[columnCount,setColumnCount]=useState(3);
  const [link,setLink]=useState({url:'',text:''});
  const dialog=useRef<HTMLDialogElement>(null),fileInput=useRef<HTMLInputElement>(null);
  const range=useRef<Range>({index:0,length:0}),selected=useRef<Selection|null>(null),linkRange=useRef<Range>({index:0,length:0});
  const uploading=useRef(false),anchor=useRef<number|null>(null),alive=useRef(true);
  const callbacks=useRef({onUploadState,onMediaChange});callbacks.current={onUploadState,onMediaChange};
  useEffect(()=>()=>{alive.current=false;},[]);

  const position=()=>q?Math.min(q.getLength()-1,Math.max(0,range.current.index)):0;
  function close(){setModal(null);q?.focus();}
  function open(kind:NonNullable<typeof modal>) {setError('');setNotice('');setModal(kind);}
  useEffect(()=>{
    if(modal&&!dialog.current?.open)dialog.current?.showModal();
    if(!modal&&dialog.current?.open)dialog.current.close();
  },[modal]);
  useEffect(()=>{
    if(modal!=='library')return;
    const controller=new AbortController();setLoading(true);
    void get<ImageFile[]>('/media',controller.signal).then(files=>{if(!controller.signal.aborted)setLibrary(files);})
      .catch(e=>{if(!controller.signal.aborted)setError(messageOf(e));}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    return()=>controller.abort();
  },[modal]);

  function insert(kind:string,value:unknown,index=position()) {
    if(!q)return index;
    const at=Math.max(0,Math.min(index,q.getLength()-1));let end=at;
    const delta=new Delta().retain(at);
    if(at>0&&q.getText(at-1,1)!=='\n'){delta.insert('\n');end++;}
    delta.insert({[kind]:value}).insert('\n');
    q.history.cutoff();q.updateContents(delta,'user');q.history.cutoff();
    range.current={index:Math.min(end+2,q.getLength()-1),length:0};q.setSelection(range.current,'silent');
    return range.current.index;
  }
  function insertMedia(file:ImageFile,index=position()) {
    return file.mime.startsWith('image/')
      ?insert('aicaImage',{id:file.id,width:'100',align:'center',alt:file.alt||'',caption:''},index)
      :insert('aicaFile',{id:file.id,label:file.name},index);
  }
  async function upload(files:File[],index=position()) {
    if(!q||uploading.current||!files.length)return;
    uploading.current=true;anchor.current=index;setBusy(true);callbacks.current.onUploadState?.(true);setError('');setNotice('');
    try {
      for(const file of files) {
        if(file.size>5*1024*1024)throw new Error(file.name+': 파일당 5MB 이하로 선택하세요.');
        const stored=await uploadMedia(file);
        if(!alive.current)return;
        anchor.current=insertMedia(stored,anchor.current??index);callbacks.current.onMediaChange?.();
      }
      setNotice('선택한 위치에 파일을 넣었습니다.');
    }catch(e){if(alive.current)setError(messageOf(e));}
    finally{uploading.current=false;anchor.current=null;if(alive.current){setBusy(false);callbacks.current.onUploadState?.(false);}}
  }
  function selectEmbed(node:HTMLElement) {
    if(!q)return;
    const blot=Quill.find(node) as Parchment.Blot|null;
    if(!blot)return;
    const kind=(['aicaImage','aicaFile','aicaTable'] as const).find(k=>blot.statics.blotName===k);
    if(!kind)return;
    try {
      const value=JSON.parse(node.dataset.value||'{}') as EmbedValue;selected.current={blot,kind,value};
      if(kind==='aicaTable') {const table=value.rows||[['']];setRows(table.map(r=>[...r]));setRowCount(table.length);setColumnCount(table[0].length);open('table');}
      else {setOptions({...value,width:value.width||'100',align:value.align||'center'});open('embed');}
    }catch{setError('이 항목을 읽을 수 없습니다. 저장된 내용을 다시 조회하세요.');}
  }
  useEffect(()=>{
    if(!q)return;range.current={index:q.getLength()-1,length:0};
    const decorate=()=>q.root.querySelectorAll<HTMLElement>('.rt-image,.rt-file,.rt-table').forEach(node=>{
      node.tabIndex=0;node.setAttribute('role','button');
      node.setAttribute('aria-label',node.classList.contains('rt-image')?'본문 사진 설정':node.classList.contains('rt-file')?'본문 첨부 설정':'본문 표 편집');
      node.querySelector('img')?.setAttribute('draggable','false');
    });
    const selection=(value:Range|null)=>{if(value)range.current=value;};
    const changed=(delta:Delta)=>{if(anchor.current!==null)anchor.current=delta.transformPosition(anchor.current,true);decorate();};
    const click=(event:MouseEvent)=>{const node=(event.target as HTMLElement).closest<HTMLElement>('.rt-image,.rt-file,.rt-table');if(node){event.preventDefault();selectEmbed(node);}};
    const key=(event:KeyboardEvent)=>{if(event.target!==q.root&&['Enter',' '].includes(event.key)){const node=(event.target as HTMLElement).closest<HTMLElement>('.rt-image,.rt-file,.rt-table');if(node){event.preventDefault();selectEmbed(node);}}};
    const paste=(event:ClipboardEvent)=>{const files=Array.from(event.clipboardData?.files||[]);if(files.length){event.preventDefault();event.stopImmediatePropagation();void upload(files);}};
    const drag=(event:DragEvent)=>{if(event.dataTransfer?.types.includes('Files')){event.preventDefault();q.root.classList.add('drag-over');}};
    const leave=()=>q.root.classList.remove('drag-over');
    const drop=(event:DragEvent)=>{
      leave();const files=Array.from(event.dataTransfer?.files||[]);if(!files.length)return;
      event.preventDefault();event.stopImmediatePropagation();let index=position();
      const caret=document.caretRangeFromPoint(event.clientX,event.clientY);
      if(caret&&q.root.contains(caret.startContainer)) {
        const blot=Quill.find(caret.startContainer,true) as Parchment.Blot|null;
        if(blot)index=Math.min(q.getLength()-1,q.getIndex(blot)+caret.startOffset);
      }
      void upload(files,index);
    };
    decorate();q.on('selection-change',selection);q.on('text-change',changed);
    q.root.addEventListener('click',click);q.root.addEventListener('keydown',key);q.root.addEventListener('paste',paste,true);
    q.root.addEventListener('dragover',drag);q.root.addEventListener('dragleave',leave);q.root.addEventListener('drop',drop,true);
    return()=>{q.off('selection-change',selection);q.off('text-change',changed);q.root.removeEventListener('click',click);q.root.removeEventListener('keydown',key);q.root.removeEventListener('paste',paste,true);q.root.removeEventListener('dragover',drag);q.root.removeEventListener('dragleave',leave);q.root.removeEventListener('drop',drop,true);};
  },[q]);

  function selectedIndex() {
    const target=selected.current;
    if(!q||!target||!q.root.contains(target.blot.domNode))throw new Error('선택한 항목이 변경되었습니다. 다시 선택하세요.');
    return q.getIndex(target.blot);
  }
  function replace(value:EmbedValue) {
    if(!q||!selected.current)return;
    const index=selectedIndex();q.history.cutoff();
    q.updateContents(new Delta().retain(index).delete(1).insert({[selected.current.kind]:value}),'user');q.history.cutoff();
  }
  function applyEmbed() {
    try {
      const target=selected.current;if(!target)return;
      replace(target.kind==='aicaImage'?{id:target.value.id,width:options.width,align:options.align,alt:options.alt||'',caption:options.caption||''}:{id:target.value.id,label:options.label||'첨부 파일'});
      close();
    }catch(e){setError(messageOf(e));}
  }
  function removeEmbed() {try{if(q){q.history.cutoff();q.deleteText(selectedIndex(),1,'user');q.history.cutoff();close();}}catch(e){setError(messageOf(e));}}
  function moveEmbed(direction:-1|1) {
    if(!q||!selected.current)return;
    try {
      const index=selectedIndex(),target=selected.current;
      const lines=q.getLines(0,q.getLength()).map(line=>({index:q.getIndex(line),length:line.length()}));
      const line=direction===-1?lines.filter(l=>l.index<index).at(-1):lines.find(l=>l.index>index);
      if(!line){setError(direction===-1?'맨 위 항목입니다.':'맨 아래 항목입니다.');return;}
      let destination=direction===-1?line.index:Math.min(q.getLength()-1,line.index+line.length);
      q.history.cutoff();q.deleteText(index,1,'user');if(destination>index)destination--;
      insert(target.kind,target.value,destination);close();
    }catch(e){setError(messageOf(e));}
  }
  function startLink() {if(!q)return;linkRange.current={...range.current};setLink({url:String(q.getFormat(range.current).link||''),text:q.getText(range.current.index,range.current.length)});open('link');}
  function applyLink() {
    if(!q)return;
    try {const url=new URL(link.url);if(!['http:','https:'].includes(url.protocol)||url.username||url.password)throw new Error();}
    catch{setError('올바른 http 또는 https 주소를 입력하세요.');return;}
    const target=linkRange.current,text=link.text||link.url;q.history.cutoff();
    q.updateContents(new Delta().retain(target.index).delete(target.length).insert(text,{link:link.url}),'user');
    q.setSelection(target.index+text.length,0,'silent');q.history.cutoff();close();
  }
  function applyTable(){try{if(selected.current?.kind==='aicaTable')replace({rows});else insert('aicaTable',{rows});close();}catch(e){setError(messageOf(e));}}
  function clean() {
    if(!q)return;const target=range.current,[line,offset]=q.getLine(target.index);
    const start=target.length?target.index:target.index-offset,length=target.length||line?.length()||1;
    const delta=new Delta().retain(start);
    q.getContents(start,length).ops.forEach(op=>delta.retain(typeof op.insert==='string'?op.insert.length:1,
      typeof op.insert==='string'?Object.fromEntries(Object.keys(op.attributes||{}).map(k=>[k,null])):undefined));
    q.updateContents(delta,'user');
  }

  return <>
    <div className="rich-content-tools" role="toolbar" aria-label="본문 삽입 도구">
      <button type="button" disabled={!q||busy} onMouseDown={e=>e.preventDefault()} onClick={()=>fileInput.current?.click()}>＋ 사진·파일</button>
      <button type="button" disabled={!q||busy} onMouseDown={e=>e.preventDefault()} onClick={()=>open('library')}>보관함</button>
      {extraTools}
      <button type="button" disabled={!q} onMouseDown={e=>e.preventDefault()} onClick={startLink}>링크</button>
      <button type="button" data-secondary="true" disabled={!q} onMouseDown={e=>e.preventDefault()} onClick={()=>{selected.current=null;setRows([['','',''],['','',''],['','','']]);setRowCount(3);setColumnCount(3);open('table');}}>표</button>
      <button type="button" data-secondary="true" disabled={!q} onMouseDown={e=>e.preventDefault()} onClick={()=>insert('divider',true)}>구분선</button>
      <button type="button" data-secondary="true" disabled={!q} onMouseDown={e=>e.preventDefault()} onClick={clean}>서식 지우기</button>
      <input ref={fileInput} hidden type="file" multiple accept={accepted} aria-label="본문 사진·파일 업로드" onChange={e=>{void upload(Array.from(e.target.files||[]));e.target.value='';}}/>
    </div>
    {busy&&<p className="editor-tool-message" role="status">파일 업로드 중…</p>}{notice&&<p className="editor-tool-message" role="status">{notice}</p>}
    {!modal&&error&&<p className="error-box" role="alert">{error}</p>}
    <dialog ref={dialog} className="content-dialog" aria-label={modal==='library'?'미디어 보관함':modal==='link'?'링크 넣기':modal==='table'?'표 편집':'사진·첨부 설정'} onCancel={()=>setModal(null)} onClose={()=>setModal(null)}>
      <div className="dialog-heading"><h2>{modal==='library'?'미디어 보관함':modal==='link'?'링크 넣기':modal==='table'?'표 편집':'사진·첨부 설정'}</h2><button type="button" onClick={close} aria-label="편집 창 닫기">×</button></div>
      {error&&<p className="error-box" role="alert">{error}</p>}
      {modal==='library'&&<><input aria-label="보관함 검색" value={query} onChange={e=>setQuery(e.target.value)} placeholder="파일 이름 검색"/>
        {loading?<p role="status">불러오는 중…</p>:<div className="library-files">{library.filter(f=>f.name.toLowerCase().includes(query.toLowerCase())).map(file=><button key={file.id} type="button" onClick={()=>{insertMedia(file);close();}}>
          {file.mime.startsWith('image/')?<img src={'/admin/media/'+file.id+'/file'} alt={file.alt}/>:<span>문서</span>}<span>{file.name}</span></button>)}</div>}
        {!loading&&!library.length&&<p className="muted">등록된 파일이 없습니다. 사진·파일 버튼으로 업로드하세요.</p>}
      </>}
      {modal==='link'&&<form onSubmit={e=>{e.preventDefault();applyLink();}}><label>연결 주소<input type="url" required maxLength={1000} value={link.url} onChange={e=>setLink({...link,url:e.target.value})}/></label><label>표시할 문구<input maxLength={300} value={link.text} onChange={e=>setLink({...link,text:e.target.value})}/></label><div className="panel-actions"><button type="button" onClick={close}>취소</button><button className="primary">링크 적용</button></div></form>}
      {modal==='embed'&&<form onSubmit={e=>{e.preventDefault();applyEmbed();}}>
        {selected.current?.kind==='aicaImage'?<><div className="two-fields"><label>사진 크기<select value={options.width} onChange={e=>setOptions({...options,width:e.target.value})}>{['100','75','50','25'].map(v=><option key={v} value={v}>{v}%</option>)}</select></label><label>사진 정렬<select value={options.align} onChange={e=>setOptions({...options,align:e.target.value})}><option value="left">왼쪽</option><option value="center">가운데</option><option value="right">오른쪽</option></select></label></div><label>대체 텍스트<input maxLength={300} value={options.alt||''} onChange={e=>setOptions({...options,alt:e.target.value})}/></label><label>사진 설명<input maxLength={300} value={options.caption||''} onChange={e=>setOptions({...options,caption:e.target.value})}/></label></>
          :<label>파일 표시 이름<input required maxLength={200} value={options.label||''} onChange={e=>setOptions({...options,label:e.target.value})}/></label>}
        <div className="panel-actions"><button type="button" onClick={()=>moveEmbed(-1)}>위로 이동</button><button type="button" onClick={()=>moveEmbed(1)}>아래로 이동</button><button type="button" className="danger-link" onClick={removeEmbed}>본문에서 제거</button></div>
        <div className="panel-actions"><button type="button" onClick={close}>취소</button><button className="primary">설정 적용</button></div>
      </form>}
      {modal==='table'&&<form onSubmit={e=>{e.preventDefault();applyTable();}}><div className="table-dimensions"><label>행<input type="number" min={1} max={20} value={rowCount} onChange={e=>setRowCount(Number(e.target.value))}/></label><label>열<input type="number" min={1} max={8} value={columnCount} onChange={e=>setColumnCount(Number(e.target.value))}/></label><button type="button" onClick={()=>{const h=Math.max(1,Math.min(20,rowCount||1)),w=Math.max(1,Math.min(8,columnCount||1));setRowCount(h);setColumnCount(w);setRows(Array.from({length:h},(_,r)=>Array.from({length:w},(_,c)=>rows[r]?.[c]||'')));}}>크기 적용</button></div>
        <div className="table-scroll"><table className="table-inputs"><tbody>{rows.map((row,r)=><tr key={r}>{row.map((value,c)=><td key={c}><input aria-label={`표 ${r+1}행 ${c+1}열`} maxLength={500} value={value} onChange={e=>setRows(old=>old.map((line,i)=>i===r?line.map((cell,j)=>j===c?e.target.value:cell):line))}/></td>)}</tr>)}</tbody></table></div>
        <div className="panel-actions"><button type="button" onClick={close}>취소</button>{selected.current&&<button type="button" className="danger-link" onClick={removeEmbed}>본문에서 제거</button>}<button className="primary">표 적용</button></div>
      </form>}
    </dialog>
  </>;
}
