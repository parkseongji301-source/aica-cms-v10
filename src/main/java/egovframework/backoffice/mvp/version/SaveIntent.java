package egovframework.backoffice.mvp.version;
import egovframework.backoffice.mvp.common.BusinessException;
public enum SaveIntent {
 AUTOSAVE, MANUAL_DRAFT, LEGACY;
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(SaveIntent.class);
 public static SaveIntent request(String input){
  if(input==null){LOG.warn("Legacy save request without saveIntent: saving without a draft version");return LEGACY;}
  if("AUTOSAVE".equals(input))return AUTOSAVE;
  if("MANUAL_DRAFT".equals(input))return MANUAL_DRAFT;
  throw new BusinessException("저장 목적은 AUTOSAVE 또는 MANUAL_DRAFT여야 합니다.");
 }
}

