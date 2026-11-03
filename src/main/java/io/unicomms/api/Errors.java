package io.unicomms.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ApiException.class)
 public ResponseEntity<?> api(ApiException e,HttpServletRequest req) {
  return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage(),"retryable",e.retryable,"traceId",req.getAttribute("traceId")));
 }
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<?> invalid(Exception e,HttpServletRequest req) { return api(ApiException.invalid("Malformed request"),req); }
}
