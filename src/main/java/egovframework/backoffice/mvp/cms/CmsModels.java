package egovframework.backoffice.mvp.cms;
import java.time.LocalDateTime;
import java.util.List;
public final class CmsModels {
    private CmsModels() {}
    public record Category(long id, String name, int sortOrder) {}
    public record Media(long id, String name, String alt, String mime, int width, int height,
                        long byteSize, long ownerId, String ownerName, LocalDateTime createdAt) {}
    public record MediaFile(String mime, byte[] data) {}
    /**
     * parentId is null for a top-level page; siblings are ordered by sortOrder, then id. areaKind GROUP is a
     * structure node without a screen; contentTypeCode marks the type's representative work area.
     * inStructure FALSE means removed from the site structure (V15); menuVisible only matters inside it.
     */
    public record Page(long id, String title, String slug, String sectionsJson, String status,
                       long revision, Long publishedRevision, long authorId, LocalDateTime updatedAt, Long parentId, int sortOrder,
                       String areaKind, String contentTypeCode, boolean menuVisible, String menuLabel, boolean inStructure) {
        public boolean group() { return "GROUP".equals(areaKind); }
        public boolean pending() { return publishedRevision != null && revision != publishedRevision; }
    }
    public record Section(String type, String heading, String body, Long imageId, Long categoryId,
                          String link, String label, boolean visible, String bodyDoc,
                          String id, Integer schemaVersion, String variation, String sourceMode, PostsQuery query, PostsManual manual) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void unsupported(String name,com.fasterxml.jackson.databind.JsonNode value) {
            throw new egovframework.backoffice.mvp.common.BusinessException("지원하지 않는 블록 필드입니다: "+name);
        }
    }
    /** A 콘텐츠 작업 sub-navigation entry under a linked page (V16): a name, an order and one topic. */
    public record ContentNode(long id, long pageId, String name, Long topicId, int sortOrder) {}
    public record PublishedPage(long pageId, String title, String slug, String sectionsJson, long revision,
                                LocalDateTime publishedAt) {}
    public record Menu(long id, String label, String kind, Long targetId, String url, int sortOrder, boolean visible) {}
    /** A public menu row read from site_menus (before the first structure publication, and LINK rows after it). */
    public record PublicMenuRow(long id, String label, String kind, Long pageId, String slug, Long categoryId, String url, String apiHref) {}
    /** A published site structure (V14). snapshotJson is a SiteStructure.Snapshot. */
    public record StructurePublication(long id, String snapshotJson, String fingerprint, String reason, Long sourcePublicationId,
                                       long publishedBy, String publisherName, LocalDateTime publishedAt) {}
    public record StructureLabel(long id, String title) {}
    public record Link(long id, String label, String url, int sortOrder) {}
    public record Setting(String settingKey, String settingValue) {}
    public record Activity(long id, long actorId, String actorName, String action, String target,
                           String detail, LocalDateTime createdAt) {
        public String actionLabel() {return action.replace("콘텐츠","글").replace("미디어","파일");}
        public String targetLabel() {return java.util.Map.of("basic","기본 정보","system","기본 정보","style","디자인 설정","components","디자인 설정","design","디자인 설정","categories","카테고리","menus","메뉴","links","외부 링크").getOrDefault(target,target.replace("콘텐츠","글").replace("미디어","파일"));}
        public String detailLabel() {
            if(!action.equals("사이트 설정 변경"))return detail;
            var names=java.util.Map.ofEntries(java.util.Map.entry("siteName","사이트 이름"),java.util.Map.entry("description","사이트 소개"),java.util.Map.entry("contactEmail","문의 이메일"),java.util.Map.entry("homePageId","첫 화면"),java.util.Map.entry("postsPerPage","목록당 글 수"),java.util.Map.entry("primaryColor","강조색"),java.util.Map.entry("headerColor","상단 배경색"),java.util.Map.entry("radius","모서리"),java.util.Map.entry("logoId","로고"),java.util.Map.entry("headerNote","상단 문구"),java.util.Map.entry("footerText","하단 문구"));
            return java.util.Arrays.stream(detail.split(", ")).map(key->names.getOrDefault(key,key)).collect(java.util.stream.Collectors.joining(", "));
        }
    }
    public record PublicPost(long id, String title, String content, Long categoryId, String categoryName,
                             LocalDateTime publishedAt, String richContent) {}
    public record PostsQuery(String typeCode,List<Long> cohortIds,List<Long> topicIds,String sort,Integer limit) {
        public PostsQuery { if(cohortIds!=null)cohortIds=List.copyOf(cohortIds);if(topicIds!=null)topicIds=List.copyOf(topicIds); }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void unsupported(String name,com.fasterxml.jackson.databind.JsonNode value) {
            throw new egovframework.backoffice.mvp.common.BusinessException("지원하지 않는 콘텐츠 조건입니다: "+name);
        }
    }
    public record PostsManual(List<Long> postIds) {
        public PostsManual { if(postIds!=null)postIds=List.copyOf(postIds); }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void unsupported(String name,com.fasterxml.jackson.databind.JsonNode value) {
            throw new egovframework.backoffice.mvp.common.BusinessException("지원하지 않는 직접 선택 필드입니다: "+name);
        }
    }
    public record SelectedPost(long id,String title,String status,String publicationTitle) {}
    public record SectionView(Section section, List<PublicPost> posts,long total) {}
}

