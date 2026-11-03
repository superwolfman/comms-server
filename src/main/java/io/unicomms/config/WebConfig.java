package io.unicomms.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class WebConfig implements WebMvcConfigurer {
 @Value("${comms.allowed-origins}") String origins;
 @Override public void addCorsMappings(CorsRegistry registry) {
  registry.addMapping("/api/**").allowedOrigins(origins.split(",")).allowedMethods("GET","POST","PUT","OPTIONS")
   .allowedHeaders("Authorization","Content-Type").exposedHeaders("X-Trace-Id").maxAge(600);
 }
}
