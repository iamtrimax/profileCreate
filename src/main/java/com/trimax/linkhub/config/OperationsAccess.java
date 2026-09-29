package com.trimax.linkhub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OperationsAccess {
    private final String token;
    public OperationsAccess(@Value("${linkhub.ops.token:}") String token){this.token=token;}
    public boolean allowed(HttpServletRequest request){
        String supplied=request.getHeader("Authorization");
        return token.length()>=32&&supplied!=null&&MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));
    }
}
