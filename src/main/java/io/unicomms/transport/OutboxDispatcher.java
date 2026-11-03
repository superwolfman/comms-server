package io.unicomms.transport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
@Component
public class OutboxDispatcher {
 private static final Logger log=LoggerFactory.getLogger(OutboxDispatcher.class);
 private final JdbcTemplate db;private final SocketHub hub;private final ObjectMapper json;
 public OutboxDispatcher(JdbcTemplate db,SocketHub hub,ObjectMapper json) {this.db=db;this.hub=hub;this.json=json;}
 @Scheduled(fixedDelay=75) public void dispatch() {
  for(var r:db.queryForList("SELECT * FROM event_outbox WHERE published=FALSE ORDER BY id LIMIT 100")) {
   try {
    hub.broadcast((String)r.get("conversation_id"),(String)r.get("event_type"),json.readTree((String)r.get("payload")),(String)r.get("id"));
    db.update("UPDATE event_outbox SET published=TRUE WHERE id=?",r.get("id"));
   } catch(Exception e) {log.warn("Outbox dispatch failed for event {}: {}",r.get("id"),e.getClass().getSimpleName());break;}
  }
 }
 @Scheduled(fixedDelay=3600000) public void cleanup() {
  long now=System.currentTimeMillis();db.update("DELETE FROM auth_token WHERE expires_at<?",now);
  db.update("DELETE FROM event_outbox WHERE published=TRUE AND created_at<?",now-86400000L);
 }
}
