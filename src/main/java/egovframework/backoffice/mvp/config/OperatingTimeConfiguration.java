package egovframework.backoffice.mvp.config;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
/** Existing local timestamps are interpreted in the configured operating zone, never rewritten. */
@Configuration
public class OperatingTimeConfiguration {
 @Bean Clock operatingClock(@Value("${backoffice.time-zone:Asia/Seoul}") String zone){return Clock.system(ZoneId.of(zone));}
 @Bean Jackson2ObjectMapperBuilderCustomizer operatingTimestamps(Clock clock){
  return builder->builder.serializerByType(LocalDateTime.class,new JsonSerializer<LocalDateTime>(){
   @Override public void serialize(LocalDateTime value,JsonGenerator out,SerializerProvider provider)throws IOException{
    out.writeString(value.atZone(clock.getZone()).toOffsetDateTime().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
   }
  });
 }
}
