package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.classification.ClassificationModels.Catalog;
import egovframework.backoffice.mvp.classification.ClassificationModels.Term;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 콘텐츠 작업 하위 항목 (V16). An operator builds the sub-navigation under a linked page by hand: each node
 * is a name, an order and one topic of the page's content type. Nothing is derived from the topic
 * dictionary, a page without nodes still lists and writes its type's posts, and removing a node deletes
 * only that row (posts and topics stay). Nodes are admin navigation only: never a page, never published,
 * never part of the public structure. "One representative area per type" applies to the page link, not here.
 */
@Service
public class ContentNodeService {
 /** Current operating limit per page, checked here rather than in the DB. */
 static final int MAX_NODES=20;
 private final CmsStore store; private final CmsAccess access; private final ActivityService audit; private final ClassificationService classifications;
 public ContentNodeService(CmsStore store,CmsAccess access,ActivityService audit,ClassificationService classifications) {
  this.store=store;this.access=access;this.audit=audit;this.classifications=classifications;
 }
 @Transactional(readOnly=true)
 public List<ContentNode> ofPage(AccountPrincipal principal,long pageId) {access.actor(principal);return store.all("contentNodesOfPage",pageId);}
 @Transactional
 public List<ContentNode> add(AccountPrincipal principal,long pageId,String name,Long topicId) {
  store.lock();var actor=access.structure(principal);
  Page page=linked(pageId);
  List<ContentNode> existing=store.all("contentNodesOfPage",pageId);
  if(existing.size()>=MAX_NODES) throw new BusinessException("하위 항목은 페이지당 최대 "+MAX_NODES+"개까지 둘 수 있습니다.");
  String title=InputRules.text(name,80,"하위 항목 이름");
  Term topic=topic(page,topicId);
  unique(existing,null,title,topic);
  long id=store.create("createContentNode",values("pageId",pageId,"name",title,"topicId",topic.id()));
  audit.record(actor,"콘텐츠 작업 하위 항목 추가","페이지 #"+pageId,page.title()+": "+title+" (주제 "+topic.name()+")");
  return store.all("contentNodesOfPage",pageId);
 }
 @Transactional
 public List<ContentNode> edit(AccountPrincipal principal,long id,String name,Long topicId) {
  store.lock();var actor=access.structure(principal);
  ContentNode node=required(id);Page page=linked(node.pageId());
  String title=InputRules.text(name,80,"하위 항목 이름");
  Term topic=topic(page,topicId);
  unique(store.all("contentNodesOfPage",node.pageId()),id,title,topic);
  if(title.equals(node.name()) && Objects.equals(topic.id(),node.topicId())) return store.all("contentNodesOfPage",node.pageId());
  store.change("editContentNode",values("id",id,"name",title,"topicId",topic.id()));
  audit.record(actor,"콘텐츠 작업 하위 항목 변경","페이지 #"+node.pageId(),page.title()+": "+node.name()+" → "+title+" (주제 "+topic.name()+")");
  return store.all("contentNodesOfPage",node.pageId());
 }
 /** Saves the order of one page's nodes; the ids must be exactly that page's nodes. */
 @Transactional
 public List<ContentNode> reorder(AccountPrincipal principal,long pageId,List<Long> ids) {
  store.lock();var actor=access.structure(principal);
  Page page=linked(pageId);
  List<ContentNode> nodes=store.all("contentNodesOfPage",pageId);
  var actual=nodes.stream().map(ContentNode::id).toList();
  if(ids==null || ids.size()!=actual.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(actual).equals(new HashSet<>(ids)))
   throw new BusinessException("하위 항목 목록이 바뀌었습니다. 목록을 새로 고친 뒤 다시 정렬하세요.");
  for(int i=0;i<ids.size();i++) store.change("contentNodeSortOrder",values("id",ids.get(i),"sortOrder",i));
  audit.record(actor,"콘텐츠 작업 하위 항목 순서 변경","페이지 #"+pageId,page.title()+" 아래 "+ids.size()+"개");
  return store.all("contentNodesOfPage",pageId);
 }
 /** Deletes the node row only. Posts keep their topics; the dictionary is untouched. */
 @Transactional
 public List<ContentNode> remove(AccountPrincipal principal,long id) {
  store.lock();var actor=access.structure(principal);
  ContentNode node=required(id);Page page=store.one("page",node.pageId());
  store.change("deleteContentNode",id);
  audit.record(actor,"콘텐츠 작업 하위 항목 제거","페이지 #"+node.pageId(),(page==null?"":page.title()+": ")+node.name()+" (글과 주제는 유지)");
  return store.all("contentNodesOfPage",node.pageId());
 }
 private ContentNode required(long id) {
  ContentNode node=store.one("contentNode",id);
  if(node==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"하위 항목을 찾을 수 없습니다.");
  return node;
 }
 /** Nodes hang only under a PAGE area that is linked to a content type. */
 private Page linked(long pageId) {
  Page page=store.one("page",pageId);
  if(page==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"페이지를 찾을 수 없습니다.");
  if(page.group()) throw new BusinessException("묶음에는 하위 항목을 둘 수 없습니다. 콘텐츠 작업에 연결된 페이지를 선택하세요.");
  if(page.contentTypeCode()==null) throw new BusinessException("콘텐츠 작업에 연결된 페이지에만 하위 항목을 둘 수 있습니다. 먼저 글 종류를 연결하세요.");
  return page;
 }
 /** The node's topic must be an active dictionary topic allowed for the page's content type. */
 private Term topic(Page page,Long topicId) {
  if(topicId==null) throw new BusinessException("하위 항목에 보여 줄 주제를 선택하세요.");
  Catalog catalog=classifications.catalog();
  Term topic=catalog.topics().stream().filter(t->t.id()==topicId).findFirst().orElseThrow(()->new BusinessException("주제를 다시 선택하세요."));
  if(!topic.active()) throw new BusinessException("'"+topic.name()+"' 주제는 사용 중지되었습니다. 다른 주제를 선택하세요.");
  if(catalog.allowedTopics().stream().noneMatch(a->a.typeCode().equals(page.contentTypeCode())&&a.topicId()==topicId))
   throw new BusinessException("'"+topic.name()+"' 주제는 이 페이지의 글 종류에 허용되지 않습니다.");
  return topic;
 }
 private static void unique(List<ContentNode> existing,Long self,String title,Term topic) {
  for(ContentNode other:existing) {
   if(self!=null && other.id()==self) continue;
   if(other.name().equals(title)) throw new BusinessException("같은 이름의 하위 항목이 이미 있습니다: "+title);
   if(Objects.equals(other.topicId(),topic.id())) throw new BusinessException("'"+topic.name()+"' 주제는 이미 '"+other.name()+"' 항목이 보여 줍니다.");
  }
 }
}
