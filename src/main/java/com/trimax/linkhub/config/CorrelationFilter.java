package com.trimax.linkhub.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class CorrelationFilter extends OncePerRequestFilter {
    private static final Logger log=LoggerFactory.getLogger(CorrelationFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String id=request.getHeader("X-Request-ID");if(id==null||!id.matches("[a-zA-Z0-9-]{1,64}"))id=UUID.randomUUID().toString();
        response.setHeader("X-Request-ID",id);long start=System.nanoTime();
        try(MDC.MDCCloseable ignored=MDC.putCloseable("requestId",id)){try{chain.doFilter(request,response);}finally{log.info("HTTP {} status={} durationMs={}",request.getMethod(),response.getStatus(),(System.nanoTime()-start)/1000000);}}
    }
}
