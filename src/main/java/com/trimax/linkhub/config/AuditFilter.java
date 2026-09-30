package com.trimax.linkhub.config;

import com.trimax.linkhub.service.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Audit only allowlisted operations, never raw URLs, query strings, headers or request bodies. */
public class AuditFilter extends OncePerRequestFilter {
    private final AuditService audit;private final AccountSecurityService accounts;
    public AuditFilter(AuditService audit,AccountSecurityService accounts){this.audit=audit;this.accounts=accounts;}
    record Operation(String action,String resource,String id){}
    static Operation operation(String method,String path){
        if(path.equals("/api/ops/audit-logs")&&method.equals("GET"))return new Operation("OPS_ACCESS","AUDIT_LOG",null);
        if(!Set.of("POST","PUT","PATCH","DELETE").contains(method))return null;
        String key=method+" "+path;
        var exact=Map.ofEntries(
            Map.entry("POST /api/auth/register","ACCOUNT_REGISTER"),Map.entry("POST /api/auth/login","ACCOUNT_LOGIN"),Map.entry("POST /api/auth/logout","ACCOUNT_LOGOUT"),
            Map.entry("POST /api/auth/password-reset/request","PASSWORD_RESET_REQUEST"),Map.entry("POST /api/auth/password-reset/confirm","PASSWORD_RESET_CONFIRM"),
            Map.entry("POST /api/auth/email-verification/confirm","EMAIL_VERIFY_CONFIRM"),Map.entry("POST /api/me/email-verification","EMAIL_VERIFY_REQUEST"),
            Map.entry("PUT /api/me/profile","PROFILE_UPDATE"),Map.entry("PUT /api/me/availability","AVAILABILITY_UPDATE"));
        if(exact.containsKey(key))return new Operation(exact.get(key),path.contains("availability")?"AVAILABILITY":path.contains("profile")?"PROFILE":"ACCOUNT",null);
        Matcher owned=Pattern.compile("^/api/me/(links|services|sections|contacts|bookings)(?:/([0-9a-fA-F-]{36}))?$").matcher(path);
        if(owned.matches()){
            String resource=Map.of("links","LINK","services","SERVICE","sections","SECTION","contacts","CONTACT","bookings","BOOKING").get(owned.group(1));
            String verb=Map.of("POST","CREATE","PUT","UPDATE","PATCH","UPDATE","DELETE","DELETE").get(method);
            return new Operation(resource+"_"+verb,resource,owned.group(2));
        }
        Matcher guest=Pattern.compile("^/api/public/([a-zA-Z0-9][a-zA-Z0-9_]{2,29})/(contacts|bookings)$").matcher(path);
        if(method.equals("POST")&&guest.matches()){String resource=guest.group(2).equals("contacts")?"CONTACT":"BOOKING";return new Operation(resource+"_CREATE",resource,guest.group(1));}
        return null;
    }
    private UUID actor(HttpServletRequest request){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken)return null;
        try{var session=request.getSession(false);if(session==null)return null;Object stored=session.getAttribute("AUTH_VERSION");long version=stored instanceof Number n?n.longValue():0;UUID id=UUID.fromString(auth.getName());return accounts.version(id)==version?id:null;}catch(RuntimeException ignored){return null;}
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getRequestURI().substring(request.getContextPath().length());Operation op=operation(request.getMethod(),path);
        if(op==null){chain.doFilter(request,response);return;}
        UUID before=op.action().equals("ACCOUNT_LOGOUT")?actor(request):null;long start=System.nanoTime();boolean threw=false;
        try{chain.doFilter(request,response);}catch(IOException|ServletException|RuntimeException e){threw=true;throw e;}
        finally{
            int status=threw?500:response.getStatus();
            // Avoid recording the dashboard's successful audit polling as new events.
            if(!op.action().equals("OPS_ACCESS")||status>=400){
                String requestId=response.getHeader("X-Request-ID");if(requestId==null||!requestId.matches("[a-zA-Z0-9-]{1,64}"))requestId=UUID.randomUUID().toString();
                UUID who=op.action().equals("ACCOUNT_LOGOUT")?before:actor(request);
                if(op.action().equals("ACCOUNT_LOGIN")&&status>=400)who=null;
                try{audit.record(who,op.action(),op.resource(),op.id(),status,requestId,(System.nanoTime()-start)/1000000);}catch(RuntimeException ignored){audit.unavailable(requestId);}
            }
        }
    }
}
