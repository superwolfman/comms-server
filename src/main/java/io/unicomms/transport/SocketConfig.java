package io.unicomms.transport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket
public class SocketConfig implements WebSocketConfigurer {
 private final SocketHub hub; @Value("${comms.allowed-origins}") String origins;
 public SocketConfig(SocketHub hub) {this.hub=hub;}
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {registry.addHandler(hub,"/ws").setAllowedOrigins(origins.split(","));}
}
