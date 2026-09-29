import {useEffect,useRef,useState} from 'react';
import Quill, {Parchment} from 'quill';
import {RichContentTools} from './RichContentTools';

type ImageValue = {id:number;width:string;align:string;alt:string;caption:string};
const BlockEmbed = Quill.import('blots/block/embed') as typeof Parchment.EmbedBlot;
const Font = Quill.import('attributors/class/font') as Parchment.ClassAttributor;Font.whitelist=['sans','serif','mono'];Quill.register(Font,true);
const colors=['navy','blue','teal','red','purple','gray','yellow','white'];
Quill.register(new Parchment.ClassAttributor('color','rt-color',{scope:Parchment.Scope.INLINE,whitelist:colors}),true);
Quill.register(new Parchment.ClassAttributor('background','rt-background',{scope:Parchment.Scope.INLINE,whitelist:colors}),true);
class StoredEmbed extends BlockEmbed {
  static create(value:unknown){const node=super.create() as HTMLElement;node.dataset.value=JSON.stringify(value);node.setAttribute('contenteditable','false');return node;}
  static value(node:HTMLElement){return JSON.parse(node.dataset.value || 'null');}
}
class ImageEmbed extends StoredEmbed {
  static blotName='aicaImage';static tagName='figure';static className='rt-image';
  static create(value:unknown){const node=super.create(value),v=value as ImageValue;const img=document.createElement('img');img.src='/admin/media/'+Number(v.id)+'/file';img.alt=v.alt||'';node.append(img);node.classList.add('rt-width-'+(['25','50','75','100'].includes(v.width)?v.width:'100'),'rt-align-'+(['left','right','center'].includes(v.align)?v.align:'center'));if(v.caption){const caption=document.createElement('figcaption');caption.textContent=v.caption;node.append(caption);}return node;}
}
class FileEmbed extends StoredEmbed {
  static blotName='aicaFile';static tagName='div';static className='rt-file';
  static create(value:unknown){const node=super.create(value);node.textContent=(value as {label:string}).label;return node;}
}
class TableEmbed extends StoredEmbed {
  static blotName='aicaTable';static tagName='div';static className='rt-table';
  static create(value:unknown){const node=super.create(value),table=document.createElement('table');for(const row of (value as {rows:string[][]}).rows){const tr=document.createElement('tr');for(const cell of row){const td=document.createElement('td');td.textContent=cell;tr.append(td);}table.append(tr);}node.append(table);return node;}
}
class Divider extends StoredEmbed {static blotName='divider';static tagName='hr';static value(){return true;}}
Quill.register({'formats/aicaImage':ImageEmbed,'formats/aicaFile':FileEmbed,'formats/aicaTable':TableEmbed,'formats/divider':Divider},true);
const formats=['header','font','size','bold','italic','underline','strike','color','background','align','list','indent','blockquote','code-block','link','aicaImage','aicaFile','aicaTable','divider'];
export function RichEditor({document:source,plain,label,onChange,advanced=false,focusedLayout=false,onUploadState,onMediaChange,onFatalError}:{document:string|null;plain:string;label:string;onChange:(plain:string,document:string)=>void;advanced?:boolean;focusedLayout?:boolean;onUploadState?:(busy:boolean)=>void;onMediaChange?:()=>void;onFatalError?:(message:string)=>void}) {
 const host=useRef<HTMLDivElement>(null),quill=useRef<Quill|null>(null),change=useRef(onChange);change.current=onChange;
 const [editor,setEditor]=useState<Quill|null>(null);
 const [more,setMore]=useState(false);
 const lastRange=useRef({index:0,length:0});
 const fatal=useRef(onFatalError);fatal.current=onFatalError;
 const [error,setError]=useState(''),[activeFormat,setActiveFormat]=useState<Record<string,unknown>>({});
 useEffect(()=>{
  const element=document.createElement('div');host.current!.append(element);
  const q=new Quill(element,{formats,modules:{toolbar:false,history:{userOnly:true}},placeholder:'내용을 입력하세요.'});quill.current=q;
  q.root.setAttribute('role','textbox');q.root.setAttribute('aria-label',label);q.root.setAttribute('aria-multiline','true');
  try {if(source)q.setContents(JSON.parse(source),'silent');else q.setText(plain,'silent');q.history.clear();setEditor(q);}
  catch{const message='본문을 읽을 수 없어 편집을 중지했습니다. 기존 관리자에서 확인하세요.';setError(message);fatal.current?.(message);q.disable();}
  const refresh=()=>{const range=q.getSelection();if(range){lastRange.current=range;setActiveFormat(q.getFormat(range));}};
  const handler=()=>{change.current(q.getText().trimEnd(),JSON.stringify(q.getContents()));refresh();};q.on('text-change',handler);q.on('selection-change',refresh);
  return()=>{q.off('text-change',handler);q.off('selection-change',refresh);quill.current=null;host.current?.replaceChildren();};
 },[]);
 useEffect(()=>{quill.current?.root.setAttribute('aria-label',label);},[label]);
 const format=(name:string,value:unknown)=>{const q=quill.current;if(!q||!q.isEnabled())return;q.focus();q.setSelection(lastRange.current,'silent');q.format(name,value,'user');setActiveFormat(q.getFormat());};
 const selected=(name:string)=>typeof activeFormat[name]==='string'||typeof activeFormat[name]==='number'?String(activeFormat[name]):'';
 return <div className={'rich-field'+(focusedLayout?' focused-rich':'')+(more?' show-more':'')}>
  <div className="rich-toolbar" role="toolbar" aria-label={label+' 서식'}>
   <select aria-label={label+' 문단'} value={selected('header')} onChange={e=>format('header',e.target.value?Number(e.target.value):false)}><option value="">본문</option><option value="1">제목 1</option><option value="2">제목 2</option><option value="3">제목 3</option></select>
   <select data-secondary="true" aria-label={label+' 글꼴'} value={selected('font')} onChange={e=>format('font',e.target.value||false)}><option value="">고딕</option><option value="serif">명조</option><option value="mono">고정폭</option></select>
   <select data-secondary="true" aria-label={label+' 크기'} value={selected('size')} onChange={e=>format('size',e.target.value||false)}><option value="small">14</option><option value="">16</option><option value="large">20</option><option value="huge">28</option></select>
   {(['bold','italic','underline','strike'] as const).map((key,i)=><button key={key} data-secondary={key==='underline'||key==='strike'} type="button" aria-label={['굵게','기울임','밑줄','취소선'][i]} aria-pressed={activeFormat[key]===true} onMouseDown={e=>e.preventDefault()} onClick={()=>format(key,!quill.current?.getFormat()[key])}>{['B','I','U','S'][i]}</button>)}
   <select data-secondary="true" aria-label={label+' 글자색'} value={selected('color')} onChange={e=>format('color',e.target.value||false)}><option value="">글자색</option>{['navy','blue','teal','red','purple','gray','white'].map((c,i)=><option key={c} value={c}>{['남색','파랑','청록','빨강','보라','회색','흰색'][i]}</option>)}</select>
   <select data-secondary="true" aria-label={label+' 정렬'} value={selected('align')} onChange={e=>format('align',e.target.value||false)}><option value="">왼쪽</option><option value="center">가운데</option><option value="right">오른쪽</option><option value="justify">양쪽</option></select>
   <button type="button" aria-pressed={activeFormat.list==='bullet'} onMouseDown={e=>e.preventDefault()} onClick={()=>format('list',activeFormat.list==='bullet'?false:'bullet')}>• 목록</button>
   {advanced&&<>
    <select data-secondary="true" aria-label={label+' 배경색'} value={selected('background')} onChange={e=>format('background',e.target.value||false)}><option value="">배경색</option><option value="yellow">노랑</option><option value="blue">파랑</option><option value="teal">청록</option><option value="gray">회색</option></select>
    <button type="button" aria-pressed={activeFormat.list==='ordered'} onMouseDown={e=>e.preventDefault()} onClick={()=>format('list',activeFormat.list==='ordered'?false:'ordered')}>1. 목록</button>
    <button type="button" data-secondary="true" aria-pressed={!!activeFormat.blockquote} onMouseDown={e=>e.preventDefault()} onClick={()=>format('blockquote',!activeFormat.blockquote)}>인용문</button>
    <button type="button" data-secondary="true" aria-pressed={!!activeFormat['code-block']} onMouseDown={e=>e.preventDefault()} onClick={()=>format('code-block',activeFormat['code-block']?false:'plain')}>코드 블록</button>
   </>}
   <button type="button" aria-label="실행 취소" onMouseDown={e=>e.preventDefault()} onClick={()=>quill.current?.history.undo()}>↶</button>
   <button type="button" aria-label="다시 실행" onMouseDown={e=>e.preventDefault()} onClick={()=>quill.current?.history.redo()}>↷</button>
   {focusedLayout&&<button type="button" className="writer-format-toggle" aria-expanded={more} onMouseDown={e=>e.preventDefault()} onClick={()=>setMore(value=>!value)}>{more?'추가 서식 접기':'추가 서식'}</button>}
  </div>
  {advanced&&<RichContentTools editor={editor} onUploadState={onUploadState} onMediaChange={onMediaChange}/>}
  {error&&<p role="alert">{error}</p>}<div ref={host}/>
 </div>;
}
