package com.trimax.linkhub.config;

import com.trimax.linkhub.service.AccountSecurityService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.util.UUID;

public class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountSecurityService accounts;
    public AccountSessionFilter(AccountSecurityService accounts){this.accounts=accounts;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();var session=req.getSession(false);
        if(auth!=null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) && session!=null) {
            var value=session.getAttribute("AUTH_VERSION");long version=value instanceof Number n?n.longValue():0;
            boolean valid;
            try{valid=accounts.version(UUID.fromString(auth.getName()))==version;}catch(org.springframework.dao.EmptyResultDataAccessException|IllegalArgumentException e){valid=false;}
            if(!valid){session.invalidate();SecurityContextHolder.clearContext();}
        }
        chain.doFilter(req,res);
    }
}
