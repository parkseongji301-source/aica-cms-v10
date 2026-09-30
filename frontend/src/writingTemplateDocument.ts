export type TemplateMode='append'|'replace';
type Op={insert:string|Record<string,unknown>;attributes?:Record<string,unknown>};
type Document={ops:Op[]};
function read(source:string):Document {
  const value=JSON.parse(source) as Document;
  if(!Array.isArray(value.ops)||value.ops.some(op=>!op||!('insert' in op)))throw new Error('본문 구성을 읽을 수 없습니다. 다시 열어 주세요.');
  return value;
}
export function isEmptyWritingDocument(source:string){return read(source).ops.every(op=>typeof op.insert==='string'&&!op.insert.trim());}
/** Copy the saved body only. No title, classification, attachment or template record is changed. */
export function composeWritingTemplate(current:string,template:string,mode:TemplateMode):Document {
  const original=read(current),copy=read(template);
  const ops=mode==='replace'||isEmptyWritingDocument(current)?copy.ops:[...original.ops,...copy.ops];
  const result={ops};
  const text=ops.map(op=>typeof op.insert==='string'?op.insert:JSON.stringify(op.insert)).join('');
  if(text.length>20000||JSON.stringify(result).length>250000)throw new Error('템플릿을 넣으면 본문 크기 제한을 초과합니다. 기존 내용을 줄이거나 교체해 주세요.');
  return result;
}
