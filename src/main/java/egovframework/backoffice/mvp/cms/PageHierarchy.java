package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;
import static egovframework.backoffice.mvp.cms.CmsModels.Page;

/**
 * Page hierarchy rules. The database only stores a general parent_id; the current operating limits
 * (depth, a fixed top-level home page) live here so they can change without a migration.
 */
final class PageHierarchy {
 /** Current operating limit: three levels (for example a group › a page › its sub-page). */
 static final int MAX_DEPTH=3;
 /** Siblings are ordered by sort_order, then id; the SQL list uses the same order. */
 static final Comparator<Page> ORDER=Comparator.comparingInt(Page::sortOrder).thenComparingLong(Page::id);
 private PageHierarchy() {}

 static Optional<Page> find(List<Page> pages,long id) {return pages.stream().filter(p->p.id()==id).findFirst();}
 static List<Page> children(List<Page> pages,Long parentId) {
  return pages.stream().filter(p->Objects.equals(p.parentId(),parentId)).sorted(ORDER).toList();
 }
 /** 1 for a top-level page. Walks the ancestors with a guard so corrupted data cannot loop. */
 static int depth(List<Page> pages,long id) {
  int depth=0;Long current=id;
  while(current!=null) {
   if(++depth>pages.size())throw new IllegalStateException("page hierarchy contains a cycle");
   long at=current;current=find(pages,at).map(Page::parentId).orElse(null);
  }
  return depth;
 }
 /** Levels in the subtree of the page, the page included: 1 without child pages. */
 static int height(List<Page> pages,long id) {
  return 1+children(pages,id).stream().mapToInt(child->height(pages,child.id())).max().orElse(0);
 }
 static boolean ancestorOrSelf(List<Page> pages,long candidate,long pageId) {
  Long current=candidate;int steps=0;
  while(current!=null) {
   if(current==pageId)return true;
   if(++steps>pages.size())throw new IllegalStateException("page hierarchy contains a cycle");
   long at=current;current=find(pages,at).map(Page::parentId).orElse(null);
  }
  return false;
 }
 /** Validates placing a page (null for a page being created) under parentId (null for top level). */
 static void requirePlacement(List<Page> pages,Long homePageId,Long pageId,Long parentId) {
  if(pageId!=null && pageId.equals(homePageId) && parentId!=null)throw new BusinessException("홈(첫 화면) 페이지는 최상위에 고정됩니다.");
  if(parentId==null)return;
  if(find(pages,parentId).isEmpty())throw new BusinessException("상위 페이지를 찾을 수 없습니다. 목록을 새로 고친 뒤 다시 선택하세요.");
  if(parentId.equals(homePageId))throw new BusinessException("홈(첫 화면) 페이지 아래에는 하위 페이지를 둘 수 없습니다.");
  if(pageId!=null && ancestorOrSelf(pages,parentId,pageId))throw new BusinessException("페이지를 자기 자신이나 자기 하위 페이지 아래로 옮길 수 없습니다.");
  int levels=pageId==null?1:height(pages,pageId);
  if(depth(pages,parentId)+levels>MAX_DEPTH)
   throw new BusinessException(levels>1?"페이지 계층은 최대 "+MAX_DEPTH+"단계입니다. 이 페이지와 그 하위 페이지를 그 아래로 옮기면 단계를 넘습니다."
    :"페이지 계층은 최대 "+MAX_DEPTH+"단계입니다. 그 페이지 아래에는 더 둘 수 없습니다.");
 }
}
