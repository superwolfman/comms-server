package io.unicomms.api;
public class ApiException extends RuntimeException {
 public final int status; public final String code; public final boolean retryable;
 public ApiException(int status,String code,String message) { this(status,code,message,false); }
 public ApiException(int status,String code,String message,boolean retryable) {
  super(message); this.status=status; this.code=code; this.retryable=retryable;
 }
 public static ApiException invalid(String message) { return new ApiException(400,"INVALID_ARGUMENT",message); }
 public static ApiException denied() { return new ApiException(403,"FORBIDDEN","Conversation access denied"); }
}
