package io.unicomms.auth;
import io.unicomms.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public class AuthService {
 public record User(String id,String tenant,String username,String displayName) {}
 public record Session(String token,User user,long expiresAt) {}
 private final JdbcTemplate db; private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(11);
 @Value("${comms.registration-enabled}") boolean registration;
 @Value("${comms.token-hours}") int tokenHours;
 public AuthService(JdbcTemplate db) { this.db=db; }
 public Session register(String tenant,String username,String password,String displayName) {
  if(!registration) throw new ApiException(403,"REGISTRATION_DISABLED","Registration is disabled");
  validate(tenant,username,password);
  if(displayName==null||displayName.isBlank()||displayName.length()>80) throw ApiException.invalid("Invalid display name");
  String id=UUID.randomUUID().toString();
  try { db.update("INSERT INTO comms_user VALUES (?,?,?,?,?,?)",id,tenant,username,displayName,passwords.encode(password),System.currentTimeMillis()); }
  catch(DuplicateKeyException e) { throw new ApiException(409,"USER_EXISTS","Username already exists"); }
  return issue(new User(id,tenant,username,displayName));
 }
 public Session login(String tenant,String username,String password) {
  validate(tenant,username,password);
  var rows=db.queryForList("SELECT * FROM comms_user WHERE tenant=? AND username=?",tenant,username);
  if(rows.isEmpty()||!passwords.matches(password,(String)rows.getFirst().get("password_hash"))) throw new ApiException(401,"INVALID_CREDENTIALS","Invalid credentials");
  var r=rows.getFirst(); return issue(new User((String)r.get("id"),tenant,username,(String)r.get("display_name")));
 }
 private void validate(String tenant,String username,String password) {
  if(tenant==null||!tenant.matches("[a-zA-Z0-9_-]{1,64}")||username==null||!username.matches("[a-zA-Z0-9_-]{1,64}")||password==null||password.length()<10||password.getBytes(StandardCharsets.UTF_8).length>72)
   throw ApiException.invalid("Tenant/username must be 1–64 identifier characters; password must be 10–72 characters");
 }
 private Session issue(User user) {
  byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  long expires=System.currentTimeMillis()+tokenHours*3600000L;
  db.update("INSERT INTO auth_token VALUES (?,?,?)",hash(token),user.id(),expires); return new Session(token,user,expires);
 }
 public User authenticate(String bearer) {
  if(bearer==null||!bearer.startsWith("Bearer ")) throw new ApiException(401,"UNAUTHORIZED","Bearer token required");
  return token(bearer.substring(7));
 }
 public User token(String raw) {
  if(raw==null||raw.length()>256) throw new ApiException(401,"UNAUTHORIZED","Invalid token");
  var rows=db.query("SELECT u.id,u.tenant,u.username,u.display_name FROM comms_user u JOIN auth_token t ON u.id=t.user_id WHERE t.token_hash=? AND t.expires_at>?",
   (rs,n)->new User(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),hash(raw),System.currentTimeMillis());
  if(rows.isEmpty()) throw new ApiException(401,"UNAUTHORIZED","Session expired or revoked"); return rows.getFirst();
 }
 public void revoke(String bearer) { authenticate(bearer); db.update("DELETE FROM auth_token WHERE token_hash=?",hash(bearer.substring(7))); }
 public List<User> users(User user) { return db.query("SELECT id,tenant,username,display_name FROM comms_user WHERE tenant=? ORDER BY username LIMIT 100",(r,n)->new User(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),user.tenant()); }
 private static String hash(String raw) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
  catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
}
