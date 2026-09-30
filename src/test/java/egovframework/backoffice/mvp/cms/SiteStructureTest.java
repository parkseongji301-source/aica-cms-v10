package egovframework.backoffice.mvp.cms;

import java.util.*;
import org.junit.jupiter.api.Test;
import static egovframework.backoffice.mvp.cms.SiteStructure.*;
import static org.assertj.core.api.Assertions.*;

/** V14 step 2: snapshot rules without a database. Names are neutral fixtures, not an IA. */
class SiteStructureTest {
    private static Area page(long id,Long parent,int order,boolean menu){return new Area(id,"PAGE",parent,order,menu,null,null,null);}
    private static Area group(long id,Long parent,int order,boolean menu,String name){return new Area(id,"GROUP",parent,order,menu,null,name,null);}
    private static Snapshot snap(Area... areas){return new Snapshot(SNAPSHOT_VERSION,List.of(areas));}
    private static Map<Long,PublishedHead> heads(long... ids){var m=new HashMap<Long,PublishedHead>();for(long id:ids)m.put(id,new PublishedHead(id,"페이지"+id,"p"+id));return m;}
    private static String name(Long id){return "영역"+id;}

    @Test void theDraftNormalizesSiblingPositionsAndTheFingerprintFollowsContentOnly() {
        var pages=List.of(
            new CmsModels.Page(3,"B","b","[]","PUBLISHED",1,1L,1,null,null,40,"PAGE",null,true,null,true,false),
            new CmsModels.Page(2,"A","a","[]","PUBLISHED",1,1L,1,null,null,7,"PAGE",null,false,"에이",true,false),
            new CmsModels.Page(9,"묶음","group-x","[]","DRAFT",0,null,1,null,null,50,"GROUP",null,true,null,true,false));
        var draft=draft(pages);
        assertThat(draft.areas()).extracting(Area::areaId).containsExactly(2L,3L,9L);
        assertThat(draft.areas()).extracting(Area::sortOrder).containsExactly(0,1,2);
        assertThat(draft.areas().get(2).groupName()).isEqualTo("묶음");
        assertThat(draft.areas().get(0).groupName()).isNull();
        // Page titles and slugs are not part of the structure; group names and menu labels are.
        var renamed=List.of(new CmsModels.Page(3,"B2","b2","[]","PUBLISHED",2,2L,1,null,null,41,"PAGE",null,true,null,true,false),pages.get(1),pages.get(2));
        assertThat(fingerprint(draft(renamed))).isEqualTo(fingerprint(draft));
        // Areas removed from the structure (V15) leave the draft; hidden areas stay in it.
        var removed=new CmsModels.Page(4,"제거","gone","[]","PUBLISHED",1,1L,1,null,null,1,"PAGE",null,true,null,false,false);
        assertThat(draft(List.of(pages.get(1),removed)).areas()).extracting(Area::areaId).containsExactly(2L);
        assertThat(fingerprint(snap(page(1,null,0,true)))).isNotEqualTo(fingerprint(snap(page(1,null,0,false))));
        assertThat(fingerprint(snap(new Area(1,"PAGE",null,0,true,"a|b",null,null)))).isNotEqualTo(fingerprint(snap(new Area(1,"PAGE",null,0,true,"a",null,"b"))));
    }

    @Test void publicationErrorsCoverParentsCyclesDepthAndTypeRules() {
        var active=Set.of("REVIEW","FAQ");
        assertThat(errors(snap(page(1,null,0,true),page(2,1L,0,true),page(3,2L,0,true)),c->active.contains(c)?c:null,SiteStructureTest::name)).isEmpty();
        assertThat(errors(snap(page(1,null,0,true),page(2,1L,0,true),page(3,2L,0,true),page(4,3L,0,true)),c->c,SiteStructureTest::name))
            .extracting(Issue::message).containsExactly("'영역4'이(가) 최대 3단계를 넘습니다.");
        assertThat(errors(snap(page(1,2L,0,true),page(2,1L,0,true)),c->c,SiteStructureTest::name)).extracting(Issue::message).allMatch(m->m.contains("순환"));
        assertThat(errors(snap(page(1,99L,0,true)),c->c,SiteStructureTest::name)).extracting(Issue::message).containsExactly("'영역1'의 상위 영역을 찾을 수 없습니다.");
        var typed=snap(new Area(1,"PAGE",null,0,true,null,null,"REVIEW"),new Area(2,"PAGE",null,1,true,null,null,"REVIEW"),
            new Area(3,"PAGE",null,2,true,null,null,"OLD"),new Area(4,"GROUP",null,3,true,null,"묶음","FAQ"));
        assertThat(errors(typed,c->active.contains(c)?c:null,SiteStructureTest::name)).extracting(Issue::message)
            .anyMatch(m->m.contains("같은 콘텐츠 유형")).anyMatch(m->m.contains("사용하지 않는 콘텐츠 유형(OLD)")).anyMatch(m->m.contains("묶음 '영역4'"));
    }

    @Test void warningsExplainWhatThePublicWillNotShow() {
        var s=snap(page(1,null,0,false),group(2,null,1,true,"빈 묶음"),page(3,null,2,true),page(4,1L,0,true));
        var messages=warnings(s,heads(1,4),SiteStructureTest::name).stream().map(Issue::message).toList();
        assertThat(messages).anyMatch(m->m.contains("하위 영역이 없어")).anyMatch(m->m.contains("'영역3'은(는) 아직 공개되지 않은"))
            .anyMatch(m->m.contains("상위 '영역1'이(가) 메뉴에서 숨겨져"));
        assertThat(warnings(snap(page(1,null,0,false)),heads(1),SiteStructureTest::name)).extracting(Issue::message).anyMatch(m->m.contains("메뉴에 보이는 영역이 없습니다"));
    }

    @Test void resolutionLinksOnlyPublicPagesAndKeepsLabelsForPublicDescendants() {
        var s=snap(group(10,null,0,true,"묶음"),page(11,10L,0,true),new Area(12,"PAGE",null,1,true,"표시명",null,null),page(13,12L,0,true),
            page(14,null,2,false),group(15,null,3,true,"비어 있음"),page(16,15L,0,true));
        var menu=resolve(s,heads(11,13,14),id->"옛 제목"+id,true);
        assertThat(menu).extracting(Node::areaId).containsExactly(10L,12L);
        assertThat(menu.get(0).kind()).isEqualTo("GROUP");assertThat(menu.get(0).label()).isEqualTo("묶음");
        assertThat(menu.get(0).children()).extracting(Node::slug).containsExactly("p11");
        // An unpublished page with a public child is a label: its menu label, never a link.
        assertThat(menu.get(1).kind()).isEqualTo("GROUP");assertThat(menu.get(1).pageId()).isNull();assertThat(menu.get(1).label()).isEqualTo("표시명");
        assertThat(menu.get(1).title()).isEqualTo("옛 제목12");
        // The structure keeps pages hidden from the menu; it drops what has nothing public.
        assertThat(resolve(s,heads(11,13,14),id->"",false)).extracting(Node::areaId).containsExactly(10L,12L,14L);
        assertThat(resolve(s,heads(),id->"",false)).isEmpty();
        // A hidden parent hides its subtree from the menu.
        assertThat(resolve(snap(page(1,null,0,false),page(2,1L,0,true)),heads(1,2),id->"",true)).isEmpty();
    }

    @Test void theDiffNamesAddedRemovedMovedAndReorderedAreas() {
        var before=snap(page(1,null,0,true),page(2,null,1,true),page(3,null,2,false),group(4,null,3,true,"옛 묶음"));
        var after=snap(page(2,null,0,true),page(1,null,1,false),new Area(3,"PAGE",1L,0,true,"새 이름",null,null),page(5,null,2,true));
        var changes=diff(before,after,SiteStructureTest::name).stream().map(c->c.label()+": "+c.detail()).toList();
        assertThat(changes).contains("영역1: 메뉴 숨김","영역3: 위치 변경: 최상위 → 영역1","영역3: 메뉴 노출","영역3: 메뉴 표시명: 제목 사용 → 새 이름",
            "영역5: 추가","옛 묶음: 구성에서 제거","최상위: 하위 순서 변경");
        assertThat(diff(null,after,SiteStructureTest::name)).hasSize(4).allMatch(c->c.detail().equals("추가"));
        assertThat(diff(after,after,SiteStructureTest::name)).isEmpty();
    }

    @Test void republishingWithoutMissingAreasMovesTheirChildrenUp() {
        var s=snap(page(1,null,0,true),group(2,null,1,true,"묶음"),page(3,2L,0,true),page(4,2L,1,true),page(5,null,2,true));
        var kept=without(s,Set.of(2L,5L));
        assertThat(kept.areas()).extracting(Area::areaId).containsExactly(1L,3L,4L);
        assertThat(kept.areas()).extracting(Area::parentId).containsOnlyNulls();
        assertThat(kept.areas()).extracting(Area::sortOrder).containsExactly(0,1,2);
        assertThat(without(s,Set.of())).isSameAs(s);
        // Every area of the latest publication is protected, shown in the menu or not.
        assertThat(pageIds(snap(page(1,null,0,true),page(2,null,1,false)))).containsExactly(1L,2L);
    }
}
