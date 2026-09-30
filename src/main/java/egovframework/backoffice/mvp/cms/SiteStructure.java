package egovframework.backoffice.mvp.cms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;
import static egovframework.backoffice.mvp.cms.CmsModels.Page;

/**
 * Site structure as a publication snapshot. Areas refer to pages by id only: no URL or page title is stored,
 * so a public link always follows the page's currently published slug and title. GROUP names are stored
 * because a GROUP has no page publication of its own.
 */
public final class SiteStructure {
 private SiteStructure() {}
 public static final int SNAPSHOT_VERSION=1;
 public record Area(long areaId,String kind,Long parentId,int sortOrder,boolean menuVisible,String menuLabel,String groupName,String contentTypeCode) {
  public boolean group() {return "GROUP".equals(kind);}
 }
 public record Snapshot(int version,List<Area> areas) {
  public Snapshot {areas=List.copyOf(areas);}
 }
 /** A problem found before publishing. Errors block the publication; warnings are shown for confirmation. */
 public record Issue(Long areaId,String message) {}
 public record Change(Long areaId,String label,String detail) {}
 /** A currently published page as the public side may link to it. */
 public record PublishedHead(long pageId,String title,String slug) {}
 /** One resolved public node. kind GROUP means a label without a link (a group, or a page that is not public now). */
 public record Node(long areaId,String kind,String title,String label,Long pageId,String slug,boolean menuVisible,String contentTypeCode,List<Node> children) {}

 /** The draft is the live composition in site_pages; sibling positions are normalized to 0..n-1. */
 public static Snapshot draft(List<Page> pages) {
  var areas=new ArrayList<Area>();
  var byParent=new LinkedHashMap<Long,List<Page>>();
  // Areas removed from the structure (V15) are not part of the draft; menu visibility does not decide this.
  for(Page p:pages) if(p.inStructure()) byParent.computeIfAbsent(p.parentId(),k->new ArrayList<>()).add(p);
  for(var siblings:byParent.values()) {
   siblings.sort(PageHierarchy.ORDER);
   for(int i=0;i<siblings.size();i++) {
    Page p=siblings.get(i);
    areas.add(new Area(p.id(),p.group()?"GROUP":"PAGE",p.parentId(),i,p.menuVisible(),p.menuLabel(),p.group()?p.title():null,p.contentTypeCode()));
   }
  }
  areas.sort(Comparator.comparingLong(Area::areaId));
  return new Snapshot(SNAPSHOT_VERSION,areas);
 }

 /** Stable fingerprint of a snapshot's content (areas in id order). */
 public static String fingerprint(Snapshot snapshot) {
  var text=new StringBuilder("v").append(snapshot.version());
  snapshot.areas().stream().sorted(Comparator.comparingLong(Area::areaId)).forEach(a->text.append('\n')
   .append(a.areaId()).append('|').append(a.kind()).append('|').append(a.parentId()).append('|').append(a.sortOrder()).append('|')
   .append(a.menuVisible()).append('|').append(escape(a.menuLabel())).append('|').append(escape(a.groupName())).append('|').append(a.contentTypeCode()));
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException error) {throw new IllegalStateException(error);}
 }
 private static String escape(String value) {return value==null?"~":value.replace("\\","\\\\").replace("|","\\|").replace("\n","\\n");}

 static Map<Long,List<Area>> children(Snapshot snapshot) {
  var result=new HashMap<Long,List<Area>>();
  for(Area a:snapshot.areas()) result.computeIfAbsent(a.parentId(),k->new ArrayList<>()).add(a);
  result.values().forEach(list->list.sort(Comparator.comparingInt(Area::sortOrder).thenComparingLong(Area::areaId)));
  return result;
 }

 /**
  * Blocking problems: a missing parent, a cycle, more than MAX_DEPTH levels, a GROUP with a content-work
  * link, an inactive content type, or one type linked to two areas (the current operating rule).
  */
 public static List<Issue> errors(Snapshot snapshot,Function<String,String> activeTypeName,Function<Long,String> name) {
  var issues=new ArrayList<Issue>();
  var byId=new HashMap<Long,Area>();snapshot.areas().forEach(a->byId.put(a.areaId(),a));
  for(Area a:snapshot.areas()) {
   if(a.parentId()!=null && !byId.containsKey(a.parentId())) {issues.add(new Issue(a.areaId(),"'"+name.apply(a.areaId())+"'의 상위 영역을 찾을 수 없습니다."));continue;}
   int depth=0;Long current=a.areaId();boolean cycle=false;
   while(current!=null) {
    if(++depth>byId.size()) {cycle=true;break;}
    Area at=byId.get(current);current=at==null?null:at.parentId();
   }
   if(cycle) issues.add(new Issue(a.areaId(),"'"+name.apply(a.areaId())+"'의 상하위 관계가 순환합니다."));
   else if(depth>PageHierarchy.MAX_DEPTH) issues.add(new Issue(a.areaId(),"'"+name.apply(a.areaId())+"'이(가) 최대 "+PageHierarchy.MAX_DEPTH+"단계를 넘습니다."));
  }
  var typeOwner=new HashMap<String,Long>();
  for(Area a:snapshot.areas()) {
   if(a.contentTypeCode()==null) continue;
   if(a.group()) {issues.add(new Issue(a.areaId(),"묶음 '"+name.apply(a.areaId())+"'에는 콘텐츠 작업을 연결할 수 없습니다."));continue;}
   if(activeTypeName.apply(a.contentTypeCode())==null) issues.add(new Issue(a.areaId(),"'"+name.apply(a.areaId())+"'이(가) 사용하지 않는 콘텐츠 유형("+a.contentTypeCode()+")에 연결되어 있습니다."));
   Long owner=typeOwner.putIfAbsent(a.contentTypeCode(),a.areaId());
   if(owner!=null) issues.add(new Issue(a.areaId(),"'"+name.apply(owner)+"'와(과) '"+name.apply(a.areaId())+"'이(가) 같은 콘텐츠 유형의 대표 작업 영역입니다. 하나만 연결하세요."));
  }
  return issues;
 }

 /** Warnings: nothing in the menu, menu areas that will not link, empty groups, menu areas under a hidden parent. */
 public static List<Issue> warnings(Snapshot snapshot,Map<Long,PublishedHead> published,Function<Long,String> name) {
  var issues=new ArrayList<Issue>();
  var byId=new HashMap<Long,Area>();snapshot.areas().forEach(a->byId.put(a.areaId(),a));
  var kids=children(snapshot);
  if(snapshot.areas().stream().noneMatch(Area::menuVisible)) issues.add(new Issue(null,"메뉴에 보이는 영역이 없습니다. 게시하면 홈페이지 메뉴에는 외부 링크만 남습니다."));
  for(Area a:snapshot.areas()) {
   if(a.group() && kids.getOrDefault(a.areaId(),List.of()).isEmpty()) issues.add(new Issue(a.areaId(),"묶음 '"+name.apply(a.areaId())+"'에 하위 영역이 없어 홈페이지에 나오지 않습니다."));
   if(!a.group() && a.menuVisible() && !published.containsKey(a.areaId()))
    issues.add(new Issue(a.areaId(),"'"+name.apply(a.areaId())+"'은(는) 아직 공개되지 않은 페이지라 메뉴에서 빠지거나 링크 없는 이름으로만 나옵니다. 페이지를 게시하면 다시 게시하지 않아도 링크가 생깁니다."));
   if(a.menuVisible()) {
    Long parent=a.parentId();int steps=0;
    while(parent!=null && byId.containsKey(parent) && steps++<byId.size()) {
     Area up=byId.get(parent);
     if(!up.menuVisible()) {issues.add(new Issue(a.areaId(),"'"+name.apply(a.areaId())+"'은(는) 상위 '"+name.apply(up.areaId())+"'이(가) 메뉴에서 숨겨져 있어 메뉴에 나오지 않습니다."));break;}
     parent=up.parentId();
    }
   }
  }
  return issues;
 }

 /** Differences between the published snapshot (null = never published) and the draft, in words. */
 public static List<Change> diff(Snapshot published,Snapshot draft,Function<Long,String> name) {
  var changes=new ArrayList<Change>();
  var before=new HashMap<Long,Area>();if(published!=null) published.areas().forEach(a->before.put(a.areaId(),a));
  var after=new HashMap<Long,Area>();draft.areas().forEach(a->after.put(a.areaId(),a));
  for(Area a:draft.areas()) {
   Area old=before.get(a.areaId());String label=name.apply(a.areaId());
   if(old==null) {changes.add(new Change(a.areaId(),label,"추가"));continue;}
   if(!Objects.equals(old.parentId(),a.parentId())) changes.add(new Change(a.areaId(),label,"위치 변경: "+parentName(old.parentId(),name)+" → "+parentName(a.parentId(),name)));
   if(old.menuVisible()!=a.menuVisible()) changes.add(new Change(a.areaId(),label,a.menuVisible()?"메뉴 노출":"메뉴 숨김"));
   if(!Objects.equals(old.menuLabel(),a.menuLabel())) changes.add(new Change(a.areaId(),label,"메뉴 표시명: "+Objects.toString(old.menuLabel(),"제목 사용")+" → "+Objects.toString(a.menuLabel(),"제목 사용")));
   if(!Objects.equals(old.groupName(),a.groupName()) && a.group() && old.group()) changes.add(new Change(a.areaId(),label,"이름 변경: "+old.groupName()+" → "+a.groupName()));
   if(!Objects.equals(old.contentTypeCode(),a.contentTypeCode())) changes.add(new Change(a.areaId(),label,"콘텐츠 작업 연결: "+Objects.toString(old.contentTypeCode(),"없음")+" → "+Objects.toString(a.contentTypeCode(),"없음")));
  }
  for(Area old:published==null?List.<Area>of():published.areas())
   if(!after.containsKey(old.areaId())) changes.add(new Change(old.areaId(),old.group()?old.groupName():name.apply(old.areaId()),"구성에서 제거"));
  // Order: one change per sibling group whose shared members changed their relative order.
  if(published!=null) {
   var oldKids=children(published);
   children(draft).forEach((parent,list)->{
    var now=list.stream().map(Area::areaId).filter(id->before.containsKey(id)&&Objects.equals(before.get(id).parentId(),parent)).toList();
    var then=oldKids.getOrDefault(parent,List.of()).stream().map(Area::areaId).filter(now::contains).toList();
    if(!now.equals(then)) changes.add(new Change(parent,parentName(parent,name),"하위 순서 변경"));
   });
  }
  return changes;
 }
 private static String parentName(Long parentId,Function<Long,String> name) {return parentId==null?"최상위":name.apply(parentId);}

 /**
  * Resolves the published snapshot against the pages that are public now. A PAGE links only while it is
  * published; otherwise, like a GROUP, it becomes a label if something public remains below it, and is
  * left out when nothing does. menuOnly additionally drops areas hidden from the menu with their subtree.
  * pageLabel names such a label-only page (its last published title, else its title).
  */
 public static List<Node> resolve(Snapshot snapshot,Map<Long,PublishedHead> published,Function<Long,String> pageLabel,boolean menuOnly) {
  return resolve(children(snapshot),null,published,pageLabel,menuOnly,0);
 }
 private static List<Node> resolve(Map<Long,List<Area>> kids,Long parentId,Map<Long,PublishedHead> published,Function<Long,String> pageLabel,boolean menuOnly,int depth) {
  if(depth>=PageHierarchy.MAX_DEPTH) return List.of();
  var nodes=new ArrayList<Node>();
  for(Area a:kids.getOrDefault(parentId,List.of())) {
   if(menuOnly && !a.menuVisible()) continue;
   var below=resolve(kids,a.areaId(),published,pageLabel,menuOnly,depth+1);
   PublishedHead head=a.group()?null:published.get(a.areaId());
   if(head==null && below.isEmpty()) continue;
   String title=head!=null?head.title():a.group()?a.groupName():Objects.toString(pageLabel.apply(a.areaId()),"");
   String label=a.menuLabel()!=null?a.menuLabel():title;
   nodes.add(new Node(a.areaId(),head==null?"GROUP":"PAGE",title,label,head==null?null:head.pageId(),head==null?null:head.slug(),a.menuVisible(),a.contentTypeCode(),below));
  }
  return nodes;
 }
 /**
  * Every area of a snapshot, shown in the menu or not: the latest publication protects all of them from
  * permanent deletion, so the public structure never refers to a page that no longer exists.
  */
 public static Set<Long> pageIds(Snapshot snapshot) {
  var ids=new TreeSet<Long>();snapshot.areas().forEach(a->ids.add(a.areaId()));return ids;
 }
 /**
  * An older snapshot without the areas that no longer exist. Children of a removed area move up to its
  * nearest remaining ancestor, keeping their order after that ancestor's own children.
  */
 public static Snapshot without(Snapshot snapshot,Set<Long> missing) {
  if(missing.isEmpty()) return snapshot;
  var byId=new HashMap<Long,Area>();snapshot.areas().forEach(a->byId.put(a.areaId(),a));
  var kept=new ArrayList<Area>();
  for(Area a:snapshot.areas()) {
   if(missing.contains(a.areaId())) continue;
   Long parent=a.parentId();int steps=0;
   while(parent!=null && missing.contains(parent) && steps++<byId.size()) parent=byId.containsKey(parent)?byId.get(parent).parentId():null;
   kept.add(new Area(a.areaId(),a.kind(),parent,a.sortOrder(),a.menuVisible(),a.menuLabel(),a.groupName(),a.contentTypeCode()));
  }
  // Renumber each sibling group: original siblings first, promoted areas after them.
  var groups=new LinkedHashMap<Long,List<Area>>();
  kept.stream().sorted(Comparator.comparing((Area a)->!Objects.equals(byId.get(a.areaId()).parentId(),a.parentId())).thenComparingInt(Area::sortOrder).thenComparingLong(Area::areaId))
   .forEach(a->groups.computeIfAbsent(a.parentId(),k->new ArrayList<>()).add(a));
  var result=new ArrayList<Area>();
  groups.values().forEach(list->{for(int i=0;i<list.size();i++){Area a=list.get(i);result.add(new Area(a.areaId(),a.kind(),a.parentId(),i,a.menuVisible(),a.menuLabel(),a.groupName(),a.contentTypeCode()));}});
  result.sort(Comparator.comparingLong(Area::areaId));
  return new Snapshot(snapshot.version(),result);
 }
}
