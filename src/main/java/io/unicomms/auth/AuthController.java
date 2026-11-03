package io.unicomms.auth;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class AuthController {
 record Credentials(String tenant,String username,String password,String displayName) {}
 private final AuthService auth;
 public AuthController(AuthService auth) { this.auth=auth; }
 @PostMapping("/auth/register") public AuthService.Session register(@RequestBody Credentials c) { if(c==null) throw io.unicomms.api.ApiException.invalid("Credentials are required"); return auth.register(c.tenant(),c.username(),c.password(),c.displayName()); }
 @PostMapping("/auth/login") public AuthService.Session login(@RequestBody Credentials c) { if(c==null) throw io.unicomms.api.ApiException.invalid("Credentials are required"); return auth.login(c.tenant(),c.username(),c.password()); }
 @PostMapping("/auth/logout") public Map<String,Boolean> logout(@RequestHeader(value="Authorization",required=false) String token) { auth.revoke(token); return Map.of("ok",true); }
 @GetMapping("/users") public List<AuthService.User> users(@RequestHeader(value="Authorization",required=false) String token) { return auth.users(auth.authenticate(token)); }
}
