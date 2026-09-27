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
    public record Menu(long id,String label,String kind,Long pageId,String slug,Long categoryId,String url,String apiHref) {}
}
