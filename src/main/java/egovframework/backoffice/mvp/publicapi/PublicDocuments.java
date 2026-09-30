package egovframework.backoffice.mvp.publicapi;

import java.time.LocalDateTime;
import java.util.List;

/** Read contract only. Never serialize admin documents or stored sections_json directly. */
public final class PublicDocuments {
    private PublicDocuments() {}
    public record Term(long id,String code,String name) {}
    public record Classification(String typeCode,String typeName,List<Term> cohorts,List<Term> topics) {}
    public record Media(long id,String name,String alt,String mime,int width,int height,long byteSize,String url) {}
    public record Restaurant(String address) {}
    public record Post(long id,String title,String content,String bodyHtml,Long categoryId,
                       Classification classification,Restaurant restaurant,List<Media> media,LocalDateTime publishedAt) {}
    public record Posts(List<Post> items,long total) {}
    public record Data(String heading,String body,String bodyHtml,Media image,String link,String label,
                       String sourceMode,Posts posts) {}
    public record Block(String id,int schemaVersion,String type,String variation,boolean visible,Data data) {}
    public record Page(int apiVersion,long id,String title,String slug,LocalDateTime publishedAt,List<Block> blocks) {}
    /**
     * key is unique within one response; parentKey is the key of the parent item (null at the top level).
     * Before the first site structure publication the list is the managed menus (flat, id = menu id). After it,
     * PAGE/GROUP items come from the published structure (id = area id) and LINK items (id = menu id) follow at
     * the end of the top level. kind GROUP is a label without a link.
     */
    public record Menu(long id,String label,String kind,Long pageId,String slug,Long categoryId,String url,String apiHref,String key,String parentKey) {}
    /** publishedAt is null until the first structure publication; items is then empty. */
    public record Structure(int apiVersion,LocalDateTime publishedAt,List<StructureNode> items) {}
    /** kind GROUP is a label without a link: a group, or a page that is not public now but has public areas below. */
    public record StructureNode(long id,String kind,String title,String label,Long pageId,String slug,String apiHref,
                                boolean menuVisible,String contentTypeCode,List<StructureNode> children) {}
}
