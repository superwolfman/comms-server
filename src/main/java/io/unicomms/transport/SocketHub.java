package io.unicomms.transport;
import com.fasterxml.jackson.databind.*;
import io.unicomms.api.ApiException;
import io.unicomms.auth.AuthService;
import io.unicomms.chat.ChatService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class SocketHub extends TextWebSocketHandler {
 private static final class Client {
  final WebSocketSession socket; final long opened=System.currentTimeMillis();
  volatile AuthService.User user; volatile String token; volatile long lastSeen=opened;
  long window=opened; int count;
  Client(WebSocketSession s) {socket=new ConcurrentWebSocketSessionDecorator(s,5000,256*1024);}
 }
 private final Map<String,Client> clients=new ConcurrentHashMap<>();
 private final ObjectMapper json; private final AuthService auth; private final ChatService chat; private final MeterRegistry metrics;
 public SocketHub(ObjectMapper json,AuthService auth,ChatService chat,MeterRegistry metrics) {
  this.json=json;this.auth=auth;this.chat=chat;this.metrics=metrics;
  metrics.gauge("comms.connections",clients,Map::size);
 }
 @Override public void afterConnectionEstablished(WebSocketSession s) throws Exception {
  if(clients.size()>=2000) {s.close(CloseStatus.SERVICE_OVERLOAD);return;}
  s.setTextMessageSizeLimit(16384);clients.put(s.getId(),new Client(s));
 }
 @Override protected void handleTextMessage(WebSocketSession s,TextMessage text) {
  Client c=clients.get(s.getId()); if(c==null)return;
  String requestId="";
  try {
   JsonNode f=json.readTree(text.getPayload());
   if(!f.isObject()||f.path("v").asInt()!=1||!f.path("type").isTextual()) throw ApiException.invalid("Protocol v1 frame required");
   requestId=f.path("requestId").asText(""); if(requestId.length()>80) throw ApiException.invalid("requestId too long");
   long now=System.currentTimeMillis();c.lastSeen=now;
   if(now-c.window>1000) {c.window=now;c.count=0;}
   if(++c.count>40) throw new ApiException(429,"RATE_LIMITED","Maximum 40 frames per second",true);
   String type=f.path("type").asText(); JsonNode p=f.path("payload");
   if(type.equals("auth")) {
    if(c.user!=null) throw ApiException.invalid("Socket already authenticated");
    c.token=p.path("token").asText(); c.user=auth.token(c.token);
    send(c,"ready",requestId,Map.of("user",c.user,"heartbeatMs",15000)); return;
   }
   if(c.user==null) throw new ApiException(401,"UNAUTHORIZED","Authenticate first");
   auth.token(c.token);
   switch(type) {
    case "ping" -> send(c,"pong",requestId,Map.of("serverTime",now));
    case "message.send" -> {
     var input=json.treeToValue(p,ChatService.Send.class);
     long start=System.nanoTime(); var message=chat.send(c.user,input);
     metrics.timer("comms.message.commit").record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);
     metrics.counter("comms.message.accepted").increment(); send(c,"ack",requestId,message);
    }
    default -> throw new ApiException(400,"UNKNOWN_FRAME","Unsupported frame type");
   }
  } catch(ApiException e) {
   send(c,"error",requestId,Map.of("code",e.code,"message",e.getMessage(),"retryable",e.retryable));
   if(e.status==401) close(c,CloseStatus.POLICY_VIOLATION);
  } catch(com.fasterxml.jackson.core.JsonProcessingException e) {
   send(c,"error",requestId,Map.of("code","INVALID_ARGUMENT","message","Invalid frame payload","retryable",false));
  } catch(Exception e) {
   metrics.counter("comms.message.failed").increment();
   send(c,"error",requestId,Map.of("code","INTERNAL_ERROR","message","Operation failed; retry with the same client message id","retryable",true));
  }
 }
 private void send(Client c,String type,String requestId,Object payload) {
  try {c.socket.sendMessage(new TextMessage(json.writeValueAsString(Map.of("v",1,"type",type,"requestId",requestId,"traceId",UUID.randomUUID().toString(),"payload",payload))));}
  catch(Exception e) {close(c,CloseStatus.SESSION_NOT_RELIABLE);}
 }
 public void broadcast(String conversationId,String eventType,JsonNode payload,String eventId) {
  Set<String> members=new HashSet<>(chat.memberIds(conversationId));
  for(Client c:clients.values()) if(c.user!=null&&members.contains(c.user.id())) send(c,"event","",Map.of("eventId",eventId,"eventType",eventType,"conversationId",conversationId,"data",payload));
 }
 @Scheduled(fixedDelay=5000) public void sweep() {
  long now=System.currentTimeMillis();
  for(Client c:clients.values()) {
   if(c.user==null&&now-c.opened>5000||now-c.lastSeen>45000) {close(c,CloseStatus.SESSION_NOT_RELIABLE);continue;}
  }
 }
 private void close(Client c,CloseStatus status) {clients.remove(c.socket.getId());try{c.socket.close(status);}catch(Exception ignored){}}
 @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus status) {clients.remove(s.getId());}
 @Override public void handleTransportError(WebSocketSession s,Throwable error) {Client c=clients.remove(s.getId());if(c!=null)close(c,CloseStatus.SESSION_NOT_RELIABLE);}
}
