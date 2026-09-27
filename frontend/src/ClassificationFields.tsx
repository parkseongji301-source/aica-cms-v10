import type {Classification,ClassificationCatalog,ClassificationSelection} from './types';
import {allowedTopicIds,invalidTopicIds,toggleId,topicLabel} from './classification';
import './classification.css';

export function ClassificationSummary({value}:{value:Classification}) {
  return <dl className="classification-summary"><div><dt>유형</dt><dd>{value.typeName}</dd></div>
    <div><dt>기수</dt><dd>{value.cohorts.map(t=>t.name).join(', ')||'선택 없음'}</dd></div>
    <div><dt>주제</dt><dd>{value.topics.map(t=>t.name).join(', ')||'선택 없음'}</dd></div></dl>;
}
export function ClassificationFields({value,catalog,baseline,problem,onChange}:{value:Classification;catalog:ClassificationCatalog;baseline:ClassificationSelection;problem:string;onChange:(patch:Partial<ClassificationSelection>)=>void}) {
  const allowed=allowedTopicIds(catalog,value.typeCode),invalid=invalidTopicIds(value,catalog);
  return <div className="classification-fields" aria-label="콘텐츠 유형과 분류">
    <label>콘텐츠 유형<select value={value.typeCode} onChange={e=>onChange({typeCode:e.target.value})}>
      {!catalog.types.some(t=>t.code===value.typeCode)&&<option value={value.typeCode}>{value.typeCode} · 확인 필요</option>}
      {catalog.types.map(t=><option key={t.code} value={t.code} disabled={!t.active&&baseline.typeCode!==t.code}>{t.name}{!t.active?' · 비활성':''}</option>)}
    </select></label>
    <fieldset><legend>기수 <small>복수 선택 · 선택 없음 허용</small></legend><div className="classification-options">
      {catalog.cohorts.map(t=><label key={t.id}><input type="checkbox" checked={value.cohortIds.includes(t.id)} disabled={!t.active&&!baseline.cohortIds.includes(t.id)&&!value.cohortIds.includes(t.id)} onChange={()=>onChange({cohortIds:toggleId(value.cohortIds,t.id)})}/>{t.name}{!t.active?' · 비활성':''}</label>)}
      {!catalog.cohorts.length&&<span className="muted">등록된 기수가 없습니다.</span>}
    </div></fieldset>
    <fieldset><legend>주제 <small>복수 선택 · 선택 없음 허용</small></legend><div className="classification-options">
      {catalog.topics.filter(t=>allowed.includes(t.id)).map(t=><label key={t.id}><input type="checkbox" checked={value.topicIds.includes(t.id)} disabled={!t.active&&!(baseline.typeCode===value.typeCode&&baseline.topicIds.includes(t.id))&&!value.topicIds.includes(t.id)} onChange={()=>onChange({topicIds:toggleId(value.topicIds,t.id)})}/>{t.name}{!t.active?' · 비활성':''}</label>)}
      {!allowed.length&&<span className="muted">이 유형에 등록된 주제가 없습니다.</span>}
    </div></fieldset>
    {problem&&<div className="classification-warning" role="alert"><p>{problem}</p>
      {invalid.map(id=><div className="invalid-topic" key={id}><span>{topicLabel(id,catalog)}</span><button type="button" onClick={()=>onChange({topicIds:value.topicIds.filter(v=>v!==id)})}>{topicLabel(id,catalog)} 해제</button></div>)}
      <small>확인 전에는 초안 저장과 자동저장을 진행하지 않습니다.</small>
    </div>}
  </div>;
}
