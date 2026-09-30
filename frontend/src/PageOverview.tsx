import type {Menu,PageRow,PageTarget} from './types';
import {Empty,Feedback,Heading,Status} from './ui';
import {NavigationIcon} from './NavigationIcon';
import './pageOverview.css';
import {blockLabel} from './pagePresentation';

type Props={
  page:PageRow|undefined;
  menus:Menu[];
  outline:PageTarget|undefined;
  loading:boolean;
  error:string;
  blockId:string|null;
  onSelectBlock:(id:string)=>void;
  onEdit:(blockId?:string)=>void;
  onRetry:()=>void;
};

export function PageOverview({page,menus,outline,loading,error,blockId,onSelectBlock,onEdit,onRetry}:Props) {
  if(!page)return <section><Heading title="페이지를 찾을 수 없습니다"/><Empty>메뉴와 데이터를 새로고침하거나 다른 페이지를 선택하세요.</Empty></section>;
  const links=menus.filter(menu=>menu.kind==='PAGE'&&menu.targetId===page.id);
  const selected=blockId!==null?outline?.blocks.find(block=>block.blockId===blockId):undefined;
  const ready=!!outline&&!loading&&!error;
  return <section className="page-overview" aria-label="페이지 구조 요약">
    <Heading title={page.title} note="이 페이지의 위치와 블록 구성을 확인합니다." badge="블록 보기"/>
    <div className="page-overview-grid">
      <section className="card page-overview-summary" aria-labelledby="page-summary-heading">
        <header className="page-overview-card-heading"><h2 id="page-summary-heading"><NavigationIcon name="page"/>페이지 정보</h2><span>조회 전용</span></header>
        <dl className="page-overview-facts">
          <div><dt>상태</dt><dd><Status value={page.status} pending={page.pending} pageWording/></dd></div>
          <div><dt>URL</dt><dd><span>홈페이지 연결 전</span><small>슬러그 <code>{page.slug}</code></small></dd></div>
          <div><dt>위치</dt><dd><span>{links.length?'최상위 메뉴':'메뉴 미연결'}</span>{links.map(menu=><small key={menu.id}>홈페이지 메뉴 / {menu.label}{!menu.visible?' · 메뉴 숨김':''}</small>)}</dd></div>
          <div><dt>구성</dt><dd><span>{ready?`${outline.blocks.length}개 블록 · 표시 ${outline.blocks.filter(block=>block.visible).length}개`:loading?'불러오는 중…':'확인할 수 없음'}</span><small>저장된 작성본 기준</small></dd></div>
        </dl>
        <footer className="page-overview-action"><button className="primary" onClick={()=>onEdit()}>페이지 편집으로 이동 <NavigationIcon name="arrow"/></button><p>사이트 관리에서 이 페이지를 편집합니다.</p></footer>
      </section>
      <section className="card page-overview-blocks" aria-labelledby="page-blocks-heading">
        <header className="page-overview-card-heading"><h2 id="page-blocks-heading"><NavigationIcon name="block"/>페이지 구성</h2><span>{ready?`${outline.blocks.length}개 블록`:'저장된 초안 기준'}</span></header>
        <div className="page-overview-flow" aria-label="페이지 연결 관계"><span>{links.length?'홈페이지 메뉴':'메뉴 밖 페이지'}</span><NavigationIcon name="chevron"/><strong>{page.title}</strong><NavigationIcon name="chevron"/><span>블록</span></div>
        <Feedback loading={loading} error={error||outline?.issue||''}/>
        {error&&<button className="page-overview-retry" onClick={onRetry}>다시 불러오기</button>}
        {ready&&<>
          {outline.blocks.length===0?<Empty>이 페이지에는 아직 블록이 없습니다.</Empty>:<ol className="page-overview-block-list">{outline.blocks.map((block,index)=><li key={block.blockId??`unlinked-${index}`}>
            <button className={'page-overview-block'+(selected===block?' is-selected':'')} disabled={!block.blockId} aria-pressed={selected===block} onClick={()=>{if(block.blockId)onSelectBlock(block.blockId);}}>
              <span className="page-overview-block-number">{String(index+1).padStart(2,'0')}</span><span className="page-overview-block-name"><strong>{block.label}</strong><small>{blockLabel(block.type)}{!block.visible?' · 숨김':''}{!block.blockId?' · 직접 이동 불가':''}</small></span><span className="page-overview-block-state">{block.visible?'표시':'숨김'}</span>
            </button><button type="button" className="overview-edit-block" disabled={!block.blockId} aria-label={block.label+' 블록 편집'} onClick={()=>block.blockId&&onEdit(block.blockId)}>편집</button>
          </li>)}</ol>}
          {blockId!==null&&!selected&&<div className="page-overview-selection"><Feedback error="선택한 블록이 현재 저장된 구성에 없습니다. 다른 블록을 선택하세요."/></div>}
          {selected?.blockId&&<div className="page-overview-selection"><span>선택한 블록 · {selected.label}</span><button onClick={()=>onEdit(selected.blockId!)}>선택한 블록 편집 →</button></div>}
        </>}
        <p className="page-overview-footnote">블록은 저장된 순서대로 표시됩니다. 발행본과 구성이 다를 수 있습니다.</p>
      </section>
    </div>
  </section>;
}
