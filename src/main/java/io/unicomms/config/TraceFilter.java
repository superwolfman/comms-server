package io.unicomms.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class TraceFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  String trace=UUID.randomUUID().toString(); req.setAttribute("traceId",trace); res.setHeader("X-Trace-Id",trace);
  res.setHeader("X-Content-Type-Options","nosniff"); res.setHeader("Cache-Control","no-store");
  chain.doFilter(req,res);
 }
}
