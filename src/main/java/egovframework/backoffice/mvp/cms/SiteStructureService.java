package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.account.Account;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.publicapi.PublicDocuments;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import static egovframework.backoffice.mvp.cms.SiteStructure.*;

/**
 * 구성 게시 (site structure publication). The draft is the live composition in site_pages; publishing stores
 * a snapshot that the public structure and menus read. Until the first publication the public menus keep
 * coming from site_menus, so an empty structure never empties the homepage menu.
 */
@Service
public class SiteStructureService {
 public static final String PUBLIC_BASE="/api/public/v1";
 private final CmsStore store;private final CmsAccess access;private final ActivityService audit;private final ObjectMapper json;
 public SiteStructureService(CmsStore store,CmsAccess access,ActivityService audit,ObjectMapper json) {
  this.store=store;this.access=access;this.audit=audit;this.json=json;
 }

 public record Published(long id,String reason,Long sourcePublicationId,String publisherName,LocalDateTime publishedAt,int areas,int menuAreas) {}
 /** Everything the composition screen needs to decide on a publication. */
 public record Status(Published latest,String draftFingerprint,boolean changed,List<Change> changes,List<Issue> errors,List<Issue> warnings,
                      List<PublicDocuments.Menu> publicMenus,List<PublicDocuments.Menu> draftMenus,List<PublicDocuments.Menu> managedMenusLeaving) {}
 public record Republished(Status status,List<String> removed) {}
 public record ImportPlan(String draftFingerprint,List<Change> changes,List<String> notes) {}

 @Transactional(readOnly=true)
 public Status status(AccountPrincipal principal) {access.structure(principal);return status();}

 private Status status() {
  List<Page> pages=store.all("pages",null);
  var draft=draft(pages);var latest=latest();
  var published=latest==null?null:snapshot(latest);
  var names=names(pages,published);
  var heads=heads();
  var warnings=new ArrayList<>(SiteStructure.warnings(draft,heads,names::get));
  var leaving=leaving(draft,heads);
  if(!leaving.isEmpty()) warnings.add(new Issue(null,"현재 홈페이지 메뉴 중 "+leaving.size()+"개("+String.join(", ",leaving.stream().map(PublicDocuments.Menu::label).toList())
   +")는 구성 게시 뒤 메뉴에서 빠집니다. 페이지 메뉴는 '현재 메뉴에서 가져오기'로 옮길 수 있고, 카테고리 메뉴는 카테고리 이관 단계에서 옮깁니다."));
  String fingerprint=fingerprint(draft);
  return new Status(latest==null?null:published(latest,published),fingerprint,latest==null||!latest.fingerprint().equals(fingerprint),
   SiteStructure.diff(published,draft,names::get),SiteStructure.errors(draft,this::activeType,names::get),warnings,
   menus(),derived(draft),leaving);
 }

 /** Public menus now: site_menus until the first structure publication, then the published structure plus LINK rows. */
 @Transactional(readOnly=true)
 public List<PublicDocuments.Menu> menus() {
  var latest=latest();
  if(latest==null) return store.<PublicMenuRow>all("publicMenus",null).stream().map(m->menu(m,null)).toList();
  return derived(existing(snapshot(latest),labels()));
 }
 /** The public site structure: every public area in the published structure, including areas hidden from the menu. */
 @Transactional(readOnly=true)
 public PublicDocuments.Structure structure() {
  var latest=latest();
  if(latest==null) return new PublicDocuments.Structure(1,null,List.of());
  var labels=labels();
  return new PublicDocuments.Structure(1,latest.publishedAt(),resolve(existing(snapshot(latest),labels),heads(),labels::get,false).stream().map(this::node).toList());
 }
 /**
  * Dangling-reference guard: a published snapshot never names an area that no longer exists. Deletion is
  * refused while the latest publication refers to a page, so this only matters for data changed outside
  * the service; such an area is left out and its children move up, as in a republish.
  */
 private static Snapshot existing(Snapshot snapshot,Map<Long,String> pages) {
  var missing=new TreeSet<Long>();
  for(Area a:snapshot.areas()) if(!pages.containsKey(a.areaId())) missing.add(a.areaId());
  return SiteStructure.without(snapshot,missing);
 }
 private PublicDocuments.StructureNode node(Node n) {
  return new PublicDocuments.StructureNode(n.areaId(),n.kind(),n.title(),n.label(),n.pageId(),n.slug(),n.pageId()==null?null:PUBLIC_BASE+"/pages/"+n.pageId(),
   n.menuVisible(),n.contentTypeCode(),n.children().stream().map(this::node).toList());
 }
 private List<PublicDocuments.Menu> derived(Snapshot snapshot) {
  var out=new ArrayList<PublicDocuments.Menu>();
  flatten(resolve(snapshot,heads(),labels()::get,true),null,out);
  for(PublicMenuRow link:store.<PublicMenuRow>all("publicLinkMenus",null)) out.add(menu(link,null));
  return out;
 }
 private static void flatten(List<Node> nodes,String parentKey,List<PublicDocuments.Menu> out) {
  for(Node n:nodes) {
   String key="area:"+n.areaId();
   out.add(new PublicDocuments.Menu(n.areaId(),n.label(),n.kind(),n.pageId(),n.slug(),null,null,n.pageId()==null?null:PUBLIC_BASE+"/pages/"+n.pageId(),key,parentKey));
   flatten(n.children(),key,out);
  }
 }
 private static PublicDocuments.Menu menu(PublicMenuRow m,String parentKey) {
  return new PublicDocuments.Menu(m.id(),m.label(),m.kind(),m.pageId(),m.slug(),m.categoryId(),m.url(),m.apiHref(),"menu:"+m.id(),parentKey);
 }
 /** Current public site_menus PAGE/CATEGORY items the draft would not carry over (only before the first publication). */
 private List<PublicDocuments.Menu> leaving(Snapshot draft,Map<Long,PublishedHead> heads) {
  if(latest()!=null) return List.of();
  var carried=new HashSet<Long>();
  collect(resolve(draft,heads,id->"",true),carried);
  return store.<PublicMenuRow>all("publicMenus",null).stream()
   .filter(m->"CATEGORY".equals(m.kind())||"PAGE".equals(m.kind())&&!carried.contains(m.pageId()))
   .map(m->menu(m,null)).toList();
 }
 private static void collect(List<Node> nodes,Set<Long> pageIds) {
  for(Node n:nodes) {if(n.pageId()!=null)pageIds.add(n.pageId());collect(n.children(),pageIds);}
 }

 @Transactional
 public Status publish(AccountPrincipal principal,String fingerprint,Long expectedLatestId) {
  store.lock();var actor=access.structure(principal);
  List<Page> pages=store.all("pages",null);
  var draft=draft(pages);var latest=latest();
  requireCurrent(latest,expectedLatestId);
  if(!fingerprint(draft).equals(fingerprint)) throw conflict();
  if(latest!=null && latest.fingerprint().equals(fingerprint)) throw new BusinessException("게시된 구성과 달라진 점이 없습니다.");
  var names=names(pages,latest==null?null:snapshot(latest));
  var errors=SiteStructure.errors(draft,this::activeType,names::get);
  if(!errors.isEmpty()) throw new BusinessException(errors.get(0).message()+(errors.size()>1?" 외 "+(errors.size()-1)+"건을 먼저 고치세요.":" 먼저 고치세요."));
  long id=save(actor,draft,"PUBLISH",null);
  int changes=SiteStructure.diff(latest==null?null:snapshot(latest),draft,names::get).size();
  audit.record(actor,"사이트 구성 게시","사이트 구성 #"+id,"영역 "+draft.areas().size()+"개, 변경 "+changes+"건"+(latest==null?", 첫 게시":""));
  return status();
 }

 /**
  * Publishes an older structure again. The live draft is not changed. Areas that no longer exist are left
  * out (their remaining children move up) and reported.
  */
 @Transactional
 public Republished republish(AccountPrincipal principal,long sourceId,Long expectedLatestId) {
  store.lock();var actor=access.structure(principal);
  var latest=latest();requireCurrent(latest,expectedLatestId);
  StructurePublication source=store.one("structurePublication",sourceId);
  if(source==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"게시본을 찾을 수 없습니다.");
  if(latest!=null && latest.id()==sourceId) throw new BusinessException("이미 현재 게시된 구성입니다.");
  List<Page> pages=store.all("pages",null);
  var existing=new HashMap<Long,Page>();pages.forEach(p->existing.put(p.id(),p));
  var old=snapshot(source);
  var missing=new TreeSet<Long>();
  for(Area a:old.areas()) {Page p=existing.get(a.areaId());if(p==null||!p.areaKind().equals(a.kind()))missing.add(a.areaId());}
  var names=names(pages,old);
  var removed=missing.stream().map(names::get).toList();
  var snapshot=SiteStructure.without(old,missing);
  var errors=SiteStructure.errors(snapshot,this::activeType,names::get);
  if(!errors.isEmpty()) throw new BusinessException("이 게시본은 지금 다시 게시할 수 없습니다. "+errors.get(0).message());
  long id=save(actor,snapshot,"REPUBLISH",sourceId);
  audit.record(actor,"사이트 구성 다시 게시","사이트 구성 #"+id,"게시본 #"+sourceId+" 기준"+(removed.isEmpty()?"":", 없는 영역 "+removed.size()+"개 제외: "+String.join(", ",removed)));
  return new Republished(status(),removed);
 }

 @Transactional(readOnly=true)
 public List<Published> publications(AccountPrincipal principal) {
  access.structure(principal);
  return store.<StructurePublication>all("structurePublications",null).stream().map(p->published(p,snapshot(p))).toList();
 }

 /**
  * Carries the current managed PAGE menus into the draft: which pages are shown in the menu and their
  * order among siblings. Nothing is published; CATEGORY and LINK menus stay as they are.
  */
 @Transactional(readOnly=true)
 public ImportPlan importPlan(AccountPrincipal principal) {access.structure(principal);return plan(store.all("pages",null)).plan();}
 @Transactional
 public ImportPlan importMenus(AccountPrincipal principal,String fingerprint) {
  store.lock();var actor=access.structure(principal);
  List<Page> pages=store.all("pages",null);
  if(!fingerprint(draft(pages)).equals(fingerprint)) throw conflict();
  var work=plan(pages);
  if(work.plan().changes().isEmpty()) throw new BusinessException("현재 메뉴에서 가져올 변경이 없습니다.");
  for(Page p:work.visible()) store.change("pageComposition",values("id",p.id(),"contentTypeCode",p.contentTypeCode(),"menuVisible",true,"menuLabel",p.menuLabel()));
  work.order().forEach((id,position)->store.change("pageSortOrder",values("id",id,"sortOrder",position)));
  audit.record(actor,"현재 메뉴에서 가져오기","사이트 구성","변경 "+work.plan().changes().size()+"건");
  return new ImportPlan(fingerprint(draft(store.all("pages",null))),work.plan().changes(),work.plan().notes());
 }
 private record Work(ImportPlan plan,List<Page> visible,Map<Long,Integer> order) {}
 private Work plan(List<Page> pages) {
  var byId=new HashMap<Long,Page>();pages.forEach(p->byId.put(p.id(),p));
  var changes=new ArrayList<Change>();var notes=new ArrayList<String>();
  var shown=new LinkedHashMap<Long,Boolean>();var seen=new HashMap<Long,Integer>();
  for(Menu m:store.<Menu>all("menus",null)) {
   switch(m.kind()) {
    case "PAGE" -> {
     Page p=m.targetId()==null?null:byId.get(m.targetId());
     if(p==null||p.group()) {notes.add("메뉴 '"+m.label()+"'의 페이지를 찾을 수 없어 건너뜁니다.");continue;}
     if(!p.inStructure()) {notes.add("'"+p.title()+"'는 구성에서 제거된 영역이라 건너뜁니다. 구성에 다시 포함한 뒤 가져오세요.");continue;}
     seen.merge(p.id(),1,Integer::sum);
     shown.merge(p.id(),m.visible(),Boolean::logicalOr);
    }
    case "CATEGORY" -> notes.add("카테고리 메뉴 '"+m.label()+"'는 카테고리 이관 단계에서 옮깁니다. 이번에는 그대로 둡니다.");
    default -> notes.add("외부 링크 '"+m.label()+"'는 외부 링크로 계속 쓰입니다. 구성 게시 뒤에는 최상위 마지막에 붙습니다.");
   }
  }
  seen.forEach((id,count)->{if(count>1)notes.add("'"+byId.get(id).title()+"' 메뉴가 "+count+"개 있어 한 번만 반영합니다.");});
  var visible=new ArrayList<Page>();
  shown.forEach((id,on)->{
   Page p=byId.get(id);
   if(on && !p.menuVisible()) {visible.add(p);changes.add(new Change(id,p.title(),"메뉴 노출"));}
   if(!on && !p.menuVisible()) notes.add("'"+p.title()+"'는 기존 메뉴에서도 숨김이라 메뉴 숨김으로 둡니다.");
   Long parent=p.parentId();
   if(on && parent!=null && byId.containsKey(parent) && !byId.get(parent).menuVisible() && !Boolean.TRUE.equals(shown.get(parent)))
    notes.add("'"+p.title()+"'의 상위 '"+byId.get(parent).title()+"'가 메뉴에서 숨겨져 있어 메뉴에 나오지 않습니다.");
  });
  // Order: within each sibling group, the positions of menu pages are refilled in menu order.
  var order=new LinkedHashMap<Long,Integer>();
  var parents=new LinkedHashSet<Long>();shown.keySet().forEach(id->parents.add(byId.get(id).parentId()));
  for(Long parent:parents) {
   var siblings=PageHierarchy.children(pages,parent);
   var inMenu=shown.keySet().stream().filter(id->Objects.equals(byId.get(id).parentId(),parent)).toList();
   if(inMenu.size()<2) continue;
   var sequence=new ArrayList<Long>();var next=inMenu.iterator();
   for(Page sibling:siblings) sequence.add(shown.containsKey(sibling.id())?next.next():sibling.id());
   if(sequence.equals(siblings.stream().map(Page::id).toList())) continue;
   for(int i=0;i<sequence.size();i++) order.put(sequence.get(i),i);
   changes.add(new Change(parent,parent==null?"최상위":byId.get(parent).title(),"하위 순서를 메뉴 순서로: "+String.join(" · ",inMenu.stream().map(id->byId.get(id).title()).toList())));
  }
  return new Work(new ImportPlan(fingerprint(draft(pages)),changes,notes),visible,order);
 }

 private long save(Account actor,Snapshot snapshot,String reason,Long sourceId) {
  String document;
  try {document=json.writeValueAsString(snapshot);}
  catch(com.fasterxml.jackson.core.JsonProcessingException error) {throw new BusinessException("사이트 구성을 저장할 수 없습니다.");}
  long id=store.create("createStructurePublication",values("snapshotJson",document,"fingerprint",fingerprint(snapshot),"reason",reason,
   "sourcePublicationId",sourceId,"publishedBy",actor.id(),"publisherName",actor.displayName().substring(0,Math.min(actor.displayName().length(),80))));
  for(long pageId:pageIds(snapshot)) store.change("structurePublicationPage",values("publicationId",id,"pageId",pageId));
  return id;
 }
 private StructurePublication latest() {return store.one("latestStructurePublication",null);}
 private Snapshot snapshot(StructurePublication publication) {
  try {
   Snapshot snapshot=json.readValue(publication.snapshotJson(),Snapshot.class);
   if(snapshot.version()!=SNAPSHOT_VERSION) throw new IllegalStateException("unsupported site structure snapshot "+snapshot.version());
   return snapshot;
  } catch(com.fasterxml.jackson.core.JsonProcessingException error) {throw new IllegalStateException("site structure publication #"+publication.id()+" is unreadable",error);}
 }
 private Published published(StructurePublication p,Snapshot snapshot) {
  return new Published(p.id(),p.reason(),p.sourcePublicationId(),p.publisherName(),p.publishedAt(),snapshot.areas().size(),(int)snapshot.areas().stream().filter(Area::menuVisible).count());
 }
 private Map<Long,PublishedHead> heads() {
  var result=new HashMap<Long,PublishedHead>();
  store.<PublishedHead>all("publishedPageHeads",null).forEach(h->result.put(h.pageId(),h));return result;
 }
 private Map<Long,String> labels() {
  var result=new HashMap<Long,String>();store.<StructureLabel>all("structureLabels",null).forEach(l->result.put(l.id(),l.title()));return result;
 }
 /** Admin names: current titles, then names kept in the snapshot for areas that no longer exist. */
 private static Map<Long,String> names(List<Page> pages,Snapshot published) {
  var result=new HashMap<Long,String>() {
   @Override public String get(Object key) {return Objects.requireNonNullElse(super.get(key),"영역 #"+key);}
  };
  if(published!=null) published.areas().forEach(a->result.put(a.areaId(),a.groupName()!=null?a.groupName():"삭제된 페이지 #"+a.areaId()));
  pages.forEach(p->result.put(p.id(),p.title()));
  return result;
 }
 private String activeType(String code) {return store.one("activeContentTypeName",code);}
 private static void requireCurrent(StructurePublication latest,Long expectedLatestId) {
  if(!Objects.equals(latest==null?null:latest.id(),expectedLatestId)) throw conflict();
 }
 private static ResponseStatusException conflict() {
  return new ResponseStatusException(HttpStatus.CONFLICT,"다른 작업에서 사이트 구성이 바뀌었습니다. 새로 고친 뒤 변경 내용을 다시 확인하세요.");
 }
 /** Whether the site structure has been published at least once (menus then derive from it). */
 public boolean published() {return latest()!=null;}
}
