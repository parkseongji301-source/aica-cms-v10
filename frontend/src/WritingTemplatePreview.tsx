import type {WritingTemplateDocument} from './writingTemplateApi';
import './writing-templates.css';
export function WritingTemplatePreview({value}:{value:WritingTemplateDocument}) {
  return <section className="writing-template-preview" aria-label="템플릿 미리보기"><h3>{value.info.name}</h3><p className="muted">{value.info.description}</p><article className="rich-content" dangerouslySetInnerHTML={{__html:value.bodyHtml}}/></section>;
}
