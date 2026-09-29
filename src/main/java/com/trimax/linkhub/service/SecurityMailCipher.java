package com.trimax.linkhub.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class SecurityMailCipher {
    private final byte[] key;
    public SecurityMailCipher(@Value("${linkhub.security.token-mail-key:}") String configured) {
        if(configured.isBlank()) {
            key=new byte[32];new SecureRandom().nextBytes(key);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("TOKEN_MAIL_KEY is unset; security email encryption uses an ephemeral key. Configure a persistent key before using multiple instances or restarting with queued security emails.");
        }else {key=Base64.getDecoder().decode(configured);if(key.length!=32)throw new IllegalArgumentException("TOKEN_MAIL_KEY must encode 32 bytes");}
    }
    public String encrypt(String text) {
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException("Cannot encrypt security email",e);}
    }
    public String decrypt(String text) {
        try {
            var parts=text.split(":",2);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Cannot decrypt security email with current key");}
    }
}
