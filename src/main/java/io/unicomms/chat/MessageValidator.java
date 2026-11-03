package io.unicomms.chat;
import com.fasterxml.jackson.databind.JsonNode;
import io.unicomms.api.ApiException;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Set;
@Component
public class MessageValidator {
 public void validate(String kind,JsonNode payload) {
  if(kind==null||payload==null||!payload.isObject()||payload.toString().length()>12000) throw ApiException.invalid("Invalid message payload");
  switch(kind) {
   case "text" -> { exact(payload,"text"); text(payload,"text",4000); }
   case "image" -> { exact(payload,"url","alt"); url(payload); text(payload,"alt",200); }
   case "audio" -> { exact(payload,"url","duration"); url(payload); double duration=payload.path("duration").asDouble(Double.NaN); if(!payload.path("duration").isNumber()||!Double.isFinite(duration)||duration<0||duration>3600) throw ApiException.invalid("Invalid audio duration"); }
   case "location" -> {
    exact(payload,"latitude","longitude","label");
    if(!payload.path("latitude").isNumber()||!payload.path("longitude").isNumber()||Math.abs(payload.path("latitude").asDouble())>90||Math.abs(payload.path("longitude").asDouble())>180) throw ApiException.invalid("Invalid coordinates");
    text(payload,"label",200);
   }
   default -> throw new ApiException(422,"UNSUPPORTED_MESSAGE","Message kind is not registered");
  }
 }
 private void exact(JsonNode payload,String... allowed) {
  Set<String> expected=Set.of(allowed); payload.fieldNames().forEachRemaining(key->{if(!expected.contains(key)) throw ApiException.invalid("Unexpected message field");});
  if(payload.size()!=expected.size()) throw ApiException.invalid("Missing message field");
 }
 private void text(JsonNode p,String key,int max) { if(!p.path(key).isTextual()||p.path(key).asText().isBlank()||p.path(key).asText().length()>max) throw ApiException.invalid("Invalid "+key); }
 private void url(JsonNode p) {
  text(p,"url",2048);
  try { var u=URI.create(p.path("url").asText()); if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null) throw ApiException.invalid("Media URL must use HTTPS without credentials"); }
  catch(IllegalArgumentException e) { throw ApiException.invalid("Invalid URL"); }
 }
}
