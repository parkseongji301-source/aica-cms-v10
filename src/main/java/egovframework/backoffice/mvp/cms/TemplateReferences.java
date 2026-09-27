package egovframework.backoffice.mvp.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;
import org.springframework.stereotype.Service;

/** Read references from the original template JSON; no duplicate reference database. */
@Service
public class TemplateReferences {
 private final PageTemplateStore templates; private final ObjectMapper json;
 public TemplateReferences(PageTemplateStore templates,ObjectMapper json){this.templates=templates;this.json=json;}
 public List<UsageService.Usage> media(long mediaId) {
  var result=new ArrayList<UsageService.Usage>();
  for(var template:templates.list()) {
   try {
    boolean found=false;
    for(var block:json.readTree(template.blocksJson())) {
     if(block.path("imageId").asLong(-1)==mediaId)found=true;
     var body=block.get("bodyDoc");
     if(body!=null&&!body.isNull()) {
      var doc=body.isTextual()?json.readTree(body.asText()):body;
      for(var op:doc.path("ops")) for(String kind:List.of("aicaImage","aicaFile"))
       if(op.path("insert").path(kind).path("id").asLong(-1)==mediaId)found=true;
     }
    }
    if(found)result.add(new UsageService.Usage("공용 템플릿 · "+template.name()+(template.active()?" · 활성":" · 비활성"),"/admin-next/design/templates"));
   } catch(Exception error){throw new BusinessException("템플릿 참조를 확인할 수 없어 파일 삭제를 중단합니다. 템플릿 #"+template.id());}
  }
  return List.copyOf(result);
 }
}
