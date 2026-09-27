package egovframework.backoffice.mvp.config;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@Profile("local")
public class LocalDevelopmentInitializer implements ApplicationRunner {
 private final AccountMapper accounts; private final PasswordEncoder encoder; private final PostService posts;private final ObjectMapper json;
 private final String importPath;
 public LocalDevelopmentInitializer(AccountMapper accounts,PasswordEncoder encoder,PostService posts,ObjectMapper json,
  DataSourceProperties source,@Value("${server.address:}") String address,@Value("${backoffice.local.import:}") String importPath) {
  if(!"127.0.0.1".equals(address)||!"jdbc:h2:file:./.local-data/aica-local;DB_CLOSE_ON_EXIT=FALSE".equals(source.getUrl()))
   throw new IllegalStateException("local 프로필은 지정된 로컬 파일 DB와 127.0.0.1 주소에서만 실행됩니다.");
  this.accounts=accounts;this.encoder=encoder;this.posts=posts;this.json=json;this.importPath=importPath;
 }
 @Override @Transactional
 public void run(ApplicationArguments args) throws Exception {
  accounts.lockChanges();
  if(accounts.count()!=0)return;
  String hash=encoder.encode("1234");
  long id=accounts.create("1234","운영 관리자",hash,Role.SUPER_ADMIN);
  accounts.changePassword(id,hash,false);
  var root=new AccountPrincipal(accounts.findById(id));
  // One-time import of content explicitly exported from the previous local session.
  if(!importPath.isBlank() && Files.isRegularFile(Path.of(importPath))) {
   var items=json.readTree(Files.readString(Path.of(importPath)));
   if(!items.isArray() || items.size()>1000) throw new IllegalStateException("로컬 이전 파일 형식을 확인하세요.");
   AccountPrincipal editor=null;
   for(var item:items) {
    AccountPrincipal author=root;
    if("콘텐츠 담당자".equals(item.path("authorName").asText())) {
     if(editor==null) {
      String editorHash=encoder.encode("Aica-Preview-2026!");
      long editorId=accounts.create("editor@aica.local","콘텐츠 담당자",editorHash,Role.ADMIN);
      accounts.changePassword(editorId,editorHash,false);
      editor=new AccountPrincipal(accounts.findById(editorId));
     }
     author=editor;
    }
    posts.create(author,item.path("title").asText(),item.path("content").asText());
   }
  }
 }
}

