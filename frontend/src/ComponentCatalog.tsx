import type {ComponentDefinition} from './types';
import {Feedback,useRemote} from './ui';
import './page-editor.css';

export function ComponentCatalog({active,version}:{active:boolean;version:number}) {
 const result=useRemote<ComponentDefinition[]>('/page-components',active,version);
 return <section className="card component-catalog" aria-label="사용 가능한 페이지 블록"><header><h2>사용 가능한 페이지 블록</h2><p>페이지의 블록 추가와 같은 목록입니다. 이곳은 안내 화면입니다. 추가·배치 선택은 페이지 편집에서 진행하세요.</p></header><Feedback {...result}/>{result.data&&<div className="table-scroll"><table className="data-table"><thead><tr><th>컴포넌트</th><th>선택할 수 있는 배치</th><th>상태</th></tr></thead><tbody>{result.data.map(d=><tr key={d.type}><td><strong>{d.label}</strong><small>{d.description}</small></td><td>{d.variations.map(v=><span className="variation-chip" key={v.value} title={v.description}>{v.label}</span>)}</td><td>사용 가능</td></tr>)}</tbody></table></div>}</section>;
}
