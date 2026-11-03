package io.unicomms.chat;
import io.unicomms.auth.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/conversations")
public class ChatController {
 record Create(String title,List<String> memberIds) {} record Read(long seq) {}
 private final AuthService auth; private final ChatService chat;
 public ChatController(AuthService auth,ChatService chat) {this.auth=auth;this.chat=chat;}
 @GetMapping public List<ChatService.Conversation> list(@RequestHeader(value="Authorization",required=false) String token) {return chat.conversations(auth.authenticate(token));}
 @PostMapping public ChatService.Conversation create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Create c) {if(c==null) throw io.unicomms.api.ApiException.invalid("Conversation details are required");return chat.create(auth.authenticate(token),c.title(),c.memberIds());}
 @GetMapping("/{id}/messages") public ChatService.Page history(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestParam(required=false) Long after,@RequestParam(required=false) Long before,@RequestParam(defaultValue="100") int limit) {return chat.history(auth.authenticate(token),id,after,before,limit);}
 @PostMapping("/{id}/messages") public ChatService.Message send(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody ChatService.Send s) {return chat.send(auth.authenticate(token),new ChatService.Send(id,s.clientMessageId(),s.kind(),s.payload()));}
 @PutMapping("/{id}/read") public ChatService.Receipt read(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Read r) {if(r==null) throw io.unicomms.api.ApiException.invalid("Receipt sequence is required");return chat.read(auth.authenticate(token),id,r.seq());}
}
