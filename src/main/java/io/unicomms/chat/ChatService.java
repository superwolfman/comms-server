package io.unicomms.chat;
import com.fasterxml.jackson.databind.*;
import io.unicomms.api.ApiException;
import io.unicomms.auth.AuthService.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class ChatService {
 public record Message(String id,String conversationId,String senderId,String clientMessageId,long seq,String kind,JsonNode payload,long createdAt) {}
 public record Conversation(String id,String title,long lastSeq,long readSeq,List<String> memberIds) {}
 public record Page(List<Message> items,long nextCursor,boolean hasMore) {}
 public record Send(String conversationId,String clientMessageId,String kind,JsonNode payload) {}
 public record Receipt(String conversationId,String userId,long seq) {}
 private final JdbcTemplate db; private final ObjectMapper json; private final MessageValidator validator;
 public ChatService(JdbcTemplate db,ObjectMapper json,MessageValidator validator) { this.db=db;this.json=json;this.validator=validator; }
 private JsonNode parse(String s) { try {return json.readTree(s);}catch(Exception e){throw new IllegalStateException("Corrupt stored JSON",e);} }
 private final RowMapper<Message> mapper=(r,n)->new Message(r.getString("id"),r.getString("conversation_id"),r.getString("sender_id"),r.getString("client_message_id"),r.getLong("seq"),r.getString("kind"),parse(r.getString("payload")),r.getLong("created_at"));
 public void requireMember(User user,String id) {
  Integer count=db.queryForObject("SELECT COUNT(*) FROM conversation_member m JOIN conversation c ON c.id=m.conversation_id WHERE c.id=? AND c.tenant=? AND m.user_id=?",Integer.class,id,user.tenant(),user.id());
  if(count==null||count==0) throw ApiException.denied();
 }
 public List<String> memberIds(String id) { return db.queryForList("SELECT user_id FROM conversation_member WHERE conversation_id=?",String.class,id); }
 public List<Conversation> conversations(User user) {
  return db.query("SELECT c.id,c.title,c.last_seq,m.read_seq FROM conversation c JOIN conversation_member m ON c.id=m.conversation_id WHERE c.tenant=? AND m.user_id=? ORDER BY c.created_at DESC LIMIT 100",
   (r,n)->new Conversation(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),memberIds(r.getString(1))),user.tenant(),user.id());
 }
 @Transactional public Conversation create(User user,String title,List<String> members) {
  if(title==null||title.isBlank()||title.length()>120||members==null||members.size()>49) throw ApiException.invalid("Invalid title or members");
  var ids=new TreeSet<>(members); ids.add(user.id()); if(ids.size()<2) throw ApiException.invalid("At least two members required");
  for(String id:ids) {
   Integer n=db.queryForObject("SELECT COUNT(*) FROM comms_user WHERE id=? AND tenant=?",Integer.class,id,user.tenant());
   if(n==null||n==0) throw ApiException.invalid("Unknown member in tenant");
  }
  String id=UUID.randomUUID().toString(); db.update("INSERT INTO conversation VALUES (?,?,?,?,?)",id,user.tenant(),title,0,System.currentTimeMillis());
  for(String uid:ids) db.update("INSERT INTO conversation_member VALUES (?,?,0)",id,uid);
  event(id,"conversation.created",Map.of("conversationId",id));
  return new Conversation(id,title,0,0,List.copyOf(ids));
 }
 @Transactional public Message send(User user,Send input) {
  if(input==null||input.conversationId()==null||input.clientMessageId()==null||!input.clientMessageId().matches("[a-zA-Z0-9_-]{1,64}")) throw ApiException.invalid("Invalid message identifiers");
  validator.validate(input.kind(),input.payload()); requireMember(user,input.conversationId());
  // Serialize within a conversation: sequence allocation, idempotency and outbox commit atomically.
  Long seq=db.queryForObject("SELECT last_seq FROM conversation WHERE id=? FOR UPDATE",Long.class,input.conversationId());
  var duplicate=db.query("SELECT * FROM chat_message WHERE conversation_id=? AND sender_id=? AND client_message_id=?",mapper,input.conversationId(),user.id(),input.clientMessageId());
  if(!duplicate.isEmpty()) {
   Message prior=duplicate.getFirst();
   if(!prior.kind().equals(input.kind())||!prior.payload().equals(input.payload())) throw new ApiException(409,"IDEMPOTENCY_CONFLICT","Client message id was used with different content");
   return prior;
  }
  if(seq==null||seq>=9007199254740990L) throw new ApiException(409,"SEQUENCE_EXHAUSTED","Conversation sequence limit reached");
  Message m=new Message(UUID.randomUUID().toString(),input.conversationId(),user.id(),input.clientMessageId(),seq+1,input.kind(),input.payload(),System.currentTimeMillis());
  db.update("INSERT INTO chat_message VALUES (?,?,?,?,?,?,?,?)",m.id(),m.conversationId(),m.senderId(),m.clientMessageId(),m.seq(),m.kind(),m.payload().toString(),m.createdAt());
  db.update("UPDATE conversation SET last_seq=? WHERE id=?",m.seq(),m.conversationId());
  event(m.conversationId(),"message.created",m); return m;
 }
 public Page history(User user,String id,Long after,Long before,int limit) {
  requireMember(user,id);
  if(limit<1||limit>200||after!=null&&after<0||before!=null&&before<1||after!=null&&before!=null) throw ApiException.invalid("Invalid history cursor or limit");
  boolean forward=after!=null;
  List<Message> items=new ArrayList<>(db.query("SELECT * FROM chat_message WHERE conversation_id=? AND seq "+(forward?">":"<")+" ? ORDER BY seq "+(forward?"ASC":"DESC")+" LIMIT ?",mapper,id,forward?after:(before==null?Long.MAX_VALUE:before),limit+1));
  boolean more=items.size()>limit; if(more) items.removeLast();
  long next=items.isEmpty()?(forward?after:(before==null?0:before)):items.getLast().seq();
  if(!forward) Collections.reverse(items); return new Page(items,next,more);
 }
 @Transactional public Receipt read(User user,String id,long seq) {
  requireMember(user,id); Long last=db.queryForObject("SELECT last_seq FROM conversation WHERE id=?",Long.class,id);
  if(seq<0||last==null||seq>last) throw ApiException.invalid("Invalid receipt sequence");
  db.update("UPDATE conversation_member SET read_seq=GREATEST(read_seq,?) WHERE conversation_id=? AND user_id=?",seq,id,user.id());
  long actual=db.queryForObject("SELECT read_seq FROM conversation_member WHERE conversation_id=? AND user_id=?",Long.class,id,user.id());
  Receipt receipt=new Receipt(id,user.id(),actual); event(id,"receipt.updated",receipt); return receipt;
 }
 private void event(String conversationId,String type,Object value) {
  try {db.update("INSERT INTO event_outbox(id,conversation_id,event_type,payload,created_at,published) VALUES (?,?,?,?,?,FALSE)",UUID.randomUUID().toString(),conversationId,type,json.writeValueAsString(value),System.currentTimeMillis());}
  catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
 }
}
