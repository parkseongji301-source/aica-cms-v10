package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.version.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class PageService {
 private final CmsStore store; private final CmsAccess access; private final MediaService media;
 private final ActivityService audit; private final ObjectMapper json; private final RichTextService rich; private final PageBlockService identities; private final PublishedPostQueryService postQueries;
 private final VersionHistoryService history;
 public PageService(CmsStore store,CmsAccess access,MediaService media,ActivityService audit,ObjectMapper json,RichTextService rich,PageBlockService identities,PublishedPostQueryService postQueries,VersionHistoryService history) {
  this.history=history;
  this.store=store;this.access=access;this.media=media;this.audit=audit;this.json=json;this.rich=rich;this.identities=identities;this.postQueries=postQueries;
 }
 @Transactional(readOnly=true)
 public List<Page> list(AccountPrincipal actor) {access.manager(actor);return store.all("pages",null);}
 public record BlockTarget(String kind,String label,long pageId,String blockId,String type,boolean visible) {}
 public record PageTarget(String kind,String label,long pageId,List<BlockTarget> blocks,String issue) {}
 @Transactional(readOnly=true)
 public List<PageTarget> structure(AccountPrincipal actor) {
  return list(actor).stream().filter(page->!page.group()).map(page->{
   try {
    var blocks=sections(page.sectionsJson());
    if(blocks==null||blocks.stream().anyMatch(Objects::isNull))throw new BusinessException("블록 형식을 확인하세요.");
    var counts=new HashMap<String,Integer>();blocks.forEach(s->counts.merge(Objects.toString(s.id(),""),1,Integer::sum));
    var targets=blocks.stream().map(s->{
     String id=PageBlockService.hasStableId(s.id())&&counts.get(s.id())==1?s.id():null;
     String label=s.heading()==null||s.heading().isBlank()?PageComponentRegistry.definitions().stream().filter(d->d.type().equals(s.type())).map(PageComponentRegistry.Definition::label).findFirst().orElse(s.type()):s.heading();
     return new BlockTarget("block",label,page.id(),id,s.type(),s.visible());
    }).toList();
    return new PageTarget("page",page.title(),page.id(),targets,targets.stream().anyMatch(t->t.blockId()==null)?"ID가 없거나 중복된 블록은 직접 이동할 수 없습니다.":null);
   } catch(BusinessException error) {
    return new PageTarget("page",page.title(),page.id(),List.of(),"블록 구성을 읽을 수 없습니다. 페이지에서 확인하세요.");
   }
  }).toList();
 }
 @Transactional(readOnly=true)
 public Page get(AccountPrincipal actor,long id) {access.manager(actor);return required(id);}
 public List<PageComponentRegistry.Definition> componentDefinitions(AccountPrincipal actor) {
  access.manager(actor);return PageComponentRegistry.definitions();
 }
 @Transactional(readOnly=true)
 public List<SelectedPost> selectedPosts(AccountPrincipal actor,List<Long> ids) {
  access.manager(actor);return postQueries.selectedPosts(ids);
 }
 private Page required(long id) {
  Page page=store.one("page",id);
  if(page==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"페이지를 찾을 수 없습니다.");
  return page;
 }
 public List<Section> sections(String text) {
  try {return json.readerFor(new TypeReference<List<Section>>(){}).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(text);}
  catch(Exception error) {throw new BusinessException("섹션 구성을 읽을 수 없습니다.");}
 }
 private List<Section> validated(AccountPrincipal actor,String source,boolean publishing,boolean newPage) {
  if(source==null || source.length()>1000000) throw new BusinessException("섹션 구성이 너무 큽니다.");
  var raw=PageBlockService.metadata(sections(source),newPage);
  if(raw==null || raw.size()>30) throw new BusinessException("섹션은 최대 30개까지 추가할 수 있습니다.");
  var result=new ArrayList<Section>();
  for(var s:raw) {
   var definition=PageComponentRegistry.required(s.type());
   Long imageId=definition.fields().contains("imageId")?s.imageId():null;
   Long categoryId=definition.fields().contains("categoryId")?s.categoryId():null;
   if(imageId!=null) { media.validate(actor,List.of(imageId)); if(!media.required(imageId).mime().startsWith("image/")) throw new BusinessException("이미지 파일을 선택하세요."); }
   if(publishing && s.type().equals("IMAGE") && imageId==null) throw new BusinessException("이미지 섹션에 이미지를 선택하세요.");
   if(categoryId!=null && store.one("category",categoryId)==null) throw new BusinessException("카테고리를 다시 선택하세요.");
   String link=definition.fields().contains("link")?CmsRules.url(s.link(),publishing && s.type().equals("CTA")):"";
   var doc=rich.validate(actor,s.bodyDoc());
   result.add(new Section(s.type(),CmsRules.optional(s.heading(),200,"섹션 제목"),doc.json()==null?CmsRules.optional(s.body(),20000,"섹션 내용"):doc.text(),
       imageId,categoryId,link,CmsRules.optional(s.label(),80,"버튼 이름"),s.visible(),doc.json(),s.id(),s.schemaVersion(),s.variation(),s.sourceMode(),postQueries.validateBlock(s),postQueries.validateManual(s.manual())));
  }
  return result;
 }
 @Transactional(readOnly=true)
 public List<Section> previewSections(AccountPrincipal actor,String source) {
  access.manager(actor);return validated(actor,source,false,false);
 }
 @Transactional
 public long save(AccountPrincipal principal,Long id,Long revision,String title,String slug,String sectionsJson,String action) {
  return saveInternal(principal,id,revision,title,slug,sectionsJson,action,SaveIntent.LEGACY,false,null);
 }
 @Transactional
 public long save(AccountPrincipal principal,Long id,Long revision,String title,String slug,String sectionsJson,String action,SaveIntent intent) {
  return saveInternal(principal,id,revision,title,slug,sectionsJson,action,intent,false,null);
 }
 /** parentId places a new page under a top-level page (at the end); existing pages move with place(). */
 @Transactional
 public long save(AccountPrincipal principal,Long id,Long revision,String title,String slug,String sectionsJson,String action,SaveIntent intent,Long parentId) {
  return saveInternal(principal,id,revision,title,slug,sectionsJson,action,intent,false,parentId);
 }
 /** Complete history snapshots intentionally replace fields; ordinary client omission guards remain in effect. */
 @Transactional
 public void restoreDraft(AccountPrincipal principal,long id,long revision,String title,String sectionsJson) {
  saveInternal(principal,id,revision,title,required(id).slug(),sectionsJson,"save",SaveIntent.LEGACY,true,null);
 }
 private long saveInternal(AccountPrincipal principal,Long id,Long revision,String title,String slug,String sectionsJson,String action,SaveIntent intent,boolean restoring,Long parentId) {
  store.lock();var actor=access.manager(principal);
  if(!Set.of("save","publish").contains(action)) throw new BusinessException("저장 방식을 확인하세요.");
  var existing=id==null?null:required(id);
  if(existing!=null && existing.group()) throw new BusinessException("묶음은 화면이 없는 구조 항목이라 내용을 저장하거나 게시할 수 없습니다. 이름은 사이트 구성에서 바꿉니다.");
  if(existing==null) access.structure(principal);
  if(existing!=null && parentId!=null) throw new BusinessException("기존 페이지의 위치는 사이트 구조 화면에서 옮깁니다.");
  if(existing==null) PageHierarchy.requirePlacement(store.all("pages",null),homePageId(),null,parentId);
  if(existing!=null && revision==null) throw new BusinessException("저장 버전을 확인할 수 없습니다. 다시 열어 주세요.");
  if(existing!=null) CmsRules.revision(existing.revision(),revision);
  String name=InputRules.text(title,200,"페이지 제목");
  String path=(slug==null || slug.isBlank()) ? (existing==null ? "page-"+UUID.randomUUID().toString().replace("-","") : existing.slug()) : InputRules.text(slug,100,"페이지 주소").toLowerCase(Locale.ROOT);
  if(existing!=null && !existing.slug().equals(path)) access.structure(principal);
  if(!path.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new BusinessException("페이지 주소는 영문 소문자·숫자·하이픈으로 입력하세요.");
  var blocks=validated(principal,sectionsJson,action.equals("publish"),existing==null);
  var previous=existing==null?List.<Section>of():sections(existing.sectionsJson());
  for(var block:blocks) {
   var old=previous.stream().filter(s->s.id().equals(block.id())).findFirst().orElse(null);
   if(!restoring && old!=null && old.query()!=null && "POSTS".equals(block.type()) && block.sourceMode()==null)
    throw new BusinessException("콘텐츠 연결 조건이 누락되었습니다. 페이지를 다시 열거나 React 관리자에서 편집하세요.");
  }
  for(var block:blocks) {
   var old=previous.stream().filter(s->s.id().equals(block.id())).findFirst().orElse(null);
   if(!restoring && old!=null && old.manual()!=null && "POSTS".equals(block.type()) && block.manual()==null)
    throw new BusinessException("직접 선택 목록이 누락되었습니다. 다시 열어 주세요. 비우려면 선택 항목을 직접 해제하세요.");
  }
  // Legacy categories are retired: a block keeps a category it already had, but none is newly set (restores included).
  for(var block:blocks) {
   if(block.categoryId()==null) continue;
   var old=previous.stream().filter(s->s.id().equals(block.id())).findFirst().orElse(null);
   if(old==null||!Objects.equals(old.categoryId(),block.categoryId())) throw new BusinessException("레거시 카테고리는 새로 지정할 수 없습니다. 콘텐츠 유형·주제 조건을 사용하세요.");
  }
  identities.validate(id,previous,blocks);
  if(existing!=null && action.equals("save") && intent==SaveIntent.MANUAL_DRAFT && existing.title().equals(name) && existing.slug().equals(path) && previous.equals(blocks)){
   history.capture(actor,VersionKind.PAGE,id,"MANUAL_DRAFT",null,null,false);return id;
  }
  if(action.equals("publish") && blocks.stream().noneMatch(Section::visible)) throw new BusinessException("발행하려면 표시할 섹션을 하나 이상 추가하세요.");
  try {
   String document=json.writeValueAsString(blocks);
   var values=values("id",id,"title",name,"slug",path,"sectionsJson",document,"authorId",actor.id(),"parentId",parentId,"areaKind","PAGE");
   if(id==null) id=store.create("createPage",values); else store.change("editPage",values);
   identities.synchronize(id,previous,blocks);
   attach(id,blocks,false);
   if(action.equals("publish")) {
    store.change("clearPagePublication",id);store.change("publishPage",id);
    attach(id,blocks,true); store.change("pageVisibility",values("id",id,"status","PUBLISHED"));
   }
   audit.record(actor,action.equals("publish")?"페이지 발행":"페이지 임시저장","페이지 #"+id,name);
   if(action.equals("publish"))history.capture(actor,VersionKind.PAGE,id,"PUBLISH",null,null,true);
   else if(intent==SaveIntent.MANUAL_DRAFT)history.capture(actor,VersionKind.PAGE,id,"MANUAL_DRAFT",null,null,false);
   return id;
  } catch(DuplicateKeyException error) {throw new BusinessException("이미 사용 중인 페이지 주소입니다. 다른 주소를 입력하세요.");}
    catch(com.fasterxml.jackson.core.JsonProcessingException error) {throw new BusinessException("섹션을 저장할 수 없습니다.");}
 }
 private void attach(long id,List<Section> blocks,boolean published) {
  store.change("clearPageMedia",values("pageId",id,"published",published));
  for(long image:blocks.stream().filter(s->!published||s.visible()).flatMap(s->{ var ids=new ArrayList<Long>(rich.mediaIds(s.bodyDoc())); if(s.imageId()!=null)ids.add(s.imageId()); return ids.stream(); }).distinct().toList())
   store.change("attachPageMedia",values("pageId",id,"mediaId",image,"published",published));
 }
 @Transactional
 public Page saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String slug,String sectionsJson,String action) {
  return required(save(actor,id,revision,title,slug,sectionsJson,action));
 }
 @Transactional
 public Page saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String slug,String sectionsJson,String action,SaveIntent intent) {
  return required(save(actor,id,revision,title,slug,sectionsJson,action,intent));
 }
 @Transactional
 public Page saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String slug,String sectionsJson,String action,SaveIntent intent,Long parentId) {
  return required(save(actor,id,revision,title,slug,sectionsJson,action,intent,parentId));
 }
 @Transactional
 public void unpublish(AccountPrincipal principal,long id,Long revision) {
  store.lock();var actor=access.manager(principal);var page=required(id);
  if(page.group()) throw new BusinessException("묶음은 화면이 없는 구조 항목이라 내용을 저장하거나 게시할 수 없습니다. 이름은 사이트 구성에서 바꿉니다.");
  CmsRules.revision(page.revision(),revision);
  store.change("withdrawPage",id);
  audit.record(actor,"페이지 비공개","페이지 #"+id,page.title());
 }
 @Transactional
 public void delete(AccountPrincipal principal,long id,Long revision) {
  access.permanentDelete(principal);
  store.lock();var actor=access.manager(principal);var page=required(id);CmsRules.revision(page.revision(),revision);
  long children=PageHierarchy.children(store.all("pages",null),id).size();
  if(children>0) throw new BusinessException("하위 페이지 "+children+"개가 있습니다. 하위 페이지를 먼저 다른 곳으로 옮기거나 삭제하세요.");
  if(store.<Long>one("pageUsage",id)>0) throw new BusinessException("메뉴나 홈페이지 첫 화면에서 사용 중입니다. 연결을 해제한 후 삭제하세요.");
  if(store.<Long>one("publishedStructureReferences",id)>0) throw new BusinessException("현재 게시된 사이트 구성에 포함되어 있습니다(메뉴 숨김 포함). 사이트 구조 화면에서 구조에서 뺀 뒤 홈페이지에 다시 반영하면 삭제할 수 있습니다.");
  store.change("deletePage",id);audit.record(actor,"페이지 삭제","페이지 #"+id,page.title());
 }
 /**
  * Moves a page under parentId (null = top level) at the end of its new siblings and renumbers both sibling
  * groups contiguously. expectedParentId is the parent the operator saw; a different current parent means
  * someone else moved it first. Placement is structure, so the page revision (document edits) is untouched.
  */
 @Transactional
 public List<Page> place(AccountPrincipal principal,long id,Long parentId,Long expectedParentId) {
  store.lock();var actor=access.structure(principal);
  List<Page> all=store.all("pages",null);
  var page=PageHierarchy.find(all,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"페이지를 찾을 수 없습니다."));
  if(!Objects.equals(page.parentId(),expectedParentId)) throw new BusinessException("다른 작업에서 변경되었습니다. 다른 사용자가 먼저 이 페이지의 위치를 바꿨습니다. 목록을 새로 고친 뒤 다시 시도하세요.");
  if(Objects.equals(page.parentId(),parentId)) return all;
  PageHierarchy.requirePlacement(all,homePageId(),id,parentId);
  var source=PageHierarchy.children(all,page.parentId()).stream().filter(p->p.id()!=id).toList();
  var target=PageHierarchy.children(all,parentId);
  store.change("placePage",values("id",id,"parentId",parentId,"sortOrder",target.size()));
  renumber(source);renumber(target);
  audit.record(actor,"페이지 위치 변경","페이지 #"+id,page.title()+": "+parentLabel(all,page.parentId())+" → "+parentLabel(all,parentId));
  return store.all("pages",null);
 }
 /** Saves the order of the pages directly under parentId (null = top level); the ids must be exactly those pages. */
 @Transactional
 public List<Page> reorder(AccountPrincipal principal,Long parentId,List<Long> ids) {
  store.lock();var actor=access.structure(principal);
  List<Page> all=store.all("pages",null);
  if(parentId!=null && PageHierarchy.find(all,parentId).isEmpty()) throw new BusinessException("상위 페이지를 찾을 수 없습니다. 목록을 새로 고친 뒤 다시 정렬하세요.");
  var actual=PageHierarchy.children(all,parentId).stream().map(Page::id).toList();
  if(ids==null || ids.size()!=actual.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(actual).equals(new HashSet<>(ids)))
   throw new BusinessException("페이지 목록이 바뀌었습니다. 목록을 새로 고친 뒤 다시 정렬하세요.");
  for(int i=0;i<ids.size();i++) store.change("pageSortOrder",values("id",ids.get(i),"sortOrder",i));
  audit.record(actor,"페이지 순서 변경",parentId==null?"최상위 페이지":"페이지 #"+parentId,parentLabel(all,parentId)+" 아래 "+ids.size()+"개");
  return store.all("pages",null);
 }
 private void renumber(List<Page> siblings) {
  for(int i=0;i<siblings.size();i++) if(siblings.get(i).sortOrder()!=i) store.change("pageSortOrder",values("id",siblings.get(i).id(),"sortOrder",i));
 }
 private static String parentLabel(List<Page> all,Long parentId) {
  return parentId==null?"최상위":PageHierarchy.find(all,parentId).map(Page::title).orElse("페이지 #"+parentId);
 }
 /** Adds a GROUP area: a structure node with a name and a place, no document, publication or versions. */
 @Transactional
 public List<Page> createGroup(AccountPrincipal principal,String name,Long parentId) {
  store.lock();var actor=access.structure(principal);
  String title=InputRules.text(name,200,"묶음 이름");
  PageHierarchy.requirePlacement(store.all("pages",null),homePageId(),null,parentId);
  long id=store.create("createPage",values("title",title,"slug","group-"+UUID.randomUUID().toString().replace("-",""),"sectionsJson","[]","authorId",actor.id(),"parentId",parentId,"areaKind","GROUP"));
  audit.record(actor,"묶음 추가","페이지 #"+id,title);
  return store.all("pages",null);
 }
 /**
  * Site composition of one area: its content-work link (the type's representative work area), menu
  * visibility and menu label, and a GROUP's name. The link never limits where that type's posts appear.
  * One representative area per type is the current operating rule, checked here rather than in the DB.
  */
 @Transactional
 public List<Page> compose(AccountPrincipal principal,long id,String contentTypeCode,boolean menuVisible,String menuLabel,String name) {
  store.lock();var actor=access.structure(principal);
  List<Page> all=store.all("pages",null);
  var page=PageHierarchy.find(all,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"페이지를 찾을 수 없습니다."));
  String type=contentTypeCode==null||contentTypeCode.isBlank()?null:contentTypeCode.trim();
  if(type!=null && page.group()) throw new BusinessException("묶음에는 콘텐츠 작업을 연결할 수 없습니다. 실제 화면이 있는 페이지에 연결하세요.");
  if(type!=null && !type.equals(page.contentTypeCode()) && !page.inStructure()) throw new BusinessException("구성에서 제거된 영역에는 콘텐츠 작업을 연결할 수 없습니다. 먼저 구성에 다시 포함하세요.");
  if(type!=null && !type.equals(page.contentTypeCode())) {
   String typeName=store.one("activeContentTypeName",type);
   if(typeName==null) throw new BusinessException("사용할 수 있는 콘텐츠 유형을 선택하세요.");
   var other=all.stream().filter(p->p.id()!=id && type.equals(p.contentTypeCode())).findFirst();
   if(other.isPresent()) throw new BusinessException("'"+typeName+"' 유형은 이미 '"+other.get().title()+"' 영역이 대표 작업 영역입니다. 그 영역의 연결을 먼저 해제하세요.");
  }
  String label=menuLabel==null||menuLabel.isBlank()?null:InputRules.text(menuLabel,80,"메뉴 표시명");
  String title=page.title();
  if(page.group()) title=InputRules.text(name,200,"묶음 이름");
  else if(name!=null && !name.isBlank() && !name.trim().equals(page.title())) throw new BusinessException("페이지 제목은 페이지 편집기에서 바꿉니다.");
  var changes=new ArrayList<String>();
  if(!Objects.equals(type,page.contentTypeCode())) changes.add("콘텐츠 작업 연결 "+Objects.toString(page.contentTypeCode(),"없음")+" → "+Objects.toString(type,"없음"));
  if(menuVisible!=page.menuVisible()) changes.add(menuVisible?"메뉴 노출":"메뉴 숨김");
  if(!Objects.equals(label,page.menuLabel())) changes.add("메뉴 표시명 "+Objects.toString(label,"제목 사용"));
  if(!title.equals(page.title())) changes.add("이름 "+page.title()+" → "+title);
  if(changes.isEmpty()) return all;
  // 콘텐츠 작업 하위 항목 (V16) belong to the linked type: another type or no link removes them (posts and topics stay).
  if(!Objects.equals(type,page.contentTypeCode())) {
   int nodes=store.<ContentNode>all("contentNodesOfPage",id).size();
   if(nodes>0) {store.change("deleteContentNodesOfPage",id);changes.add("하위 항목 "+nodes+"개 제거(글·주제 유지)");}
  }
  store.change("pageComposition",values("id",id,"contentTypeCode",type,"menuVisible",menuVisible,"menuLabel",label));
  if(!title.equals(page.title())) store.change("groupName",values("id",id,"title",title));
  audit.record(actor,"사이트 구성 변경","페이지 #"+id,title+": "+String.join(", ",changes));
  return store.all("pages",null);
 }
 /**
  * 구성에서 제거 / 다시 포함 (V15). Removal is a draft change: the public structure follows at the next
  * structure publication, and the page, its publication and its content are kept. An area can be removed
  * only without children left in the structure and without a content-work link; it comes back only under a
  * parent that is in the structure. Permanent deletion additionally waits until the latest published
  * structure no longer refers to it.
  */
 @Transactional
 public List<Page> membership(AccountPrincipal principal,long id,boolean inStructure) {
  store.lock();var actor=access.structure(principal);
  List<Page> all=store.all("pages",null);
  var page=PageHierarchy.find(all,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"페이지를 찾을 수 없습니다."));
  if(page.inStructure()==inStructure) return all;
  if(!inStructure) {
   long children=PageHierarchy.children(all,id).stream().filter(Page::inStructure).count();
   if(children>0) throw new BusinessException("구성에 남은 하위 영역이 "+children+"개 있습니다. 하위 영역을 먼저 다른 곳으로 옮기거나 구성에서 제거하세요.");
   if(page.contentTypeCode()!=null) throw new BusinessException("콘텐츠 작업에 연결된 영역입니다. 구성에서 연결을 먼저 해제하세요. 글은 그대로 남습니다.");
  } else if(page.parentId()!=null && PageHierarchy.find(all,page.parentId()).map(p->!p.inStructure()).orElse(false))
   throw new BusinessException("상위 영역이 구성에서 제거되어 있습니다. 상위 영역을 먼저 구성에 다시 포함하세요.");
  store.change("pageMembership",values("id",id,"inStructure",inStructure));
  audit.record(actor,"사이트 구성 변경","페이지 #"+id,page.title()+": "+(inStructure?"구성에 다시 포함":"구성에서 제거"));
  return store.all("pages",null);
 }
 /** A content type's representative work area as the 콘텐츠 작업 sidebar needs it: no document, no status. */
 public record ContentArea(long pageId,String typeCode,String label,List<String> groups,List<ContentNodeView> nodes) {}
 /** One operator-made sub-navigation entry (V16): the sidebar shows exactly these, in their saved order. */
 public record ContentNodeView(long id,String name,Long topicId) {}
 /** Linked PAGE areas in site-structure order for every signed-in role; groups are the ancestor titles. */
 @Transactional(readOnly=true)
 public List<ContentArea> contentAreas(AccountPrincipal principal) {
  access.actor(principal);
  List<Page> all=store.all("pages",null);var out=new ArrayList<ContentArea>();
  var nodes=new HashMap<Long,List<ContentNodeView>>();
  for(ContentNode node:store.<ContentNode>all("contentNodes",null)) nodes.computeIfAbsent(node.pageId(),k->new ArrayList<>()).add(new ContentNodeView(node.id(),node.name(),node.topicId()));
  collectAreas(all,null,new ArrayList<>(),out,0,nodes);
  return out;
 }
 private void collectAreas(List<Page> all,Long parentId,List<String> groups,List<ContentArea> out,int depth,Map<Long,List<ContentNodeView>> nodes) {
  if(depth>all.size())return;
  for(Page page:PageHierarchy.children(all,parentId)) {
   if(!page.inStructure()) continue;
   if(!page.group() && page.contentTypeCode()!=null) out.add(new ContentArea(page.id(),page.contentTypeCode(),page.title(),List.copyOf(groups),List.copyOf(nodes.getOrDefault(page.id(),List.of()))));
   var next=new ArrayList<>(groups);next.add(page.title());
   collectAreas(all,page.id(),next,out,depth+1,nodes);
  }
 }
 /** The first-screen page (site setting homePageId), or null. */
 public Long homePageId() {
  for(Setting setting:store.<Setting>all("settings",null))
   if(setting.settingKey().equals("homePageId") && setting.settingValue().matches("[1-9][0-9]{0,18}")) return Long.valueOf(setting.settingValue());
  return null;
 }
 @Transactional(readOnly=true)
 public PublishedPage publication(AccountPrincipal actor,long id) {
  get(actor,id);PublishedPage snapshot=store.one("anyPagePublication",id);
  if(snapshot==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"발행본이 없습니다.");
  return snapshot;
 }
 public List<SectionView> views(String document) {
  return sections(document).stream().filter(Section::visible).map(s->{var result=postQueries.find(s);return new SectionView(s,result.items(),result.total());}).toList();
 }
}

