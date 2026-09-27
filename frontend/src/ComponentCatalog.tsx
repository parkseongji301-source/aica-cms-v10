import type {ComponentDefinition} from './types';
import {Feedback,useRemote} from './ui';
import './page-editor.css';

export function ComponentCatalog({active,version}:{active:boolean;version:number}) {
 const result=useRemote<ComponentDefinition[]>('/page-components',active,version);
 return <section className="card component-catalog" aria-label="등록된 페이지 컴포넌트"><header><h2>등록된 페이지 컴포넌트</h2><p>페이지의 블록 추가와 같은 목록입니다. 각 페이지에서 표현 방식을 선택할 수 있습니다.</p></header><Feedback {...result}/>{result.data&&<div className="table-scroll"><table className="data-table"><thead><tr><th>컴포넌트</th><th>사용 가능한 Variation</th><th>상태</th></tr></thead><tbody>{result.data.map(d=><tr key={d.type}><td><strong>{d.label}</strong><small>{d.type} · {d.description}</small></td><td>{d.variations.map(v=><span className="variation-chip" key={v.value} title={v.description}>{v.label} · {v.value}</span>)}</td><td>사용 가능</td></tr>)}</tbody></table></div>}</section>;
}
