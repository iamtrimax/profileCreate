package com.trimax.linkhub.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.sql.Timestamp;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AccountSecurityService {
    private final JdbcTemplate jdbc;private final Clock clock;private final PasswordEncoder encoder;private final SecurityMailCipher cipher;private final String baseUrl;
    public AccountSecurityService(JdbcTemplate jdbc,Clock clock,PasswordEncoder encoder,SecurityMailCipher cipher,@Value("${linkhub.base-url}") String baseUrl){this.jdbc=jdbc;this.clock=clock;this.encoder=encoder;this.cipher=cipher;this.baseUrl=baseUrl.replaceAll("/+$","");}
    public long version(UUID owner){return jdbc.queryForObject("select auth_version from accounts where id=?",Long.class,owner);}
    @Transactional public void requestReset(String email) {
        var ids=jdbc.query("select id from accounts where email=?",(r,n)->r.getObject(1,UUID.class),email.strip().toLowerCase(Locale.ROOT));
        if(!ids.isEmpty())issue(ids.getFirst(),"RESET");
    }
    @Transactional public void requestVerification(UUID owner){issue(owner,"VERIFY");}
    private void issue(UUID owner,String purpose) {
        String email=jdbc.queryForObject("select email from accounts where id=? for update",String.class,owner);
        if(purpose.equals("VERIFY") && Boolean.TRUE.equals(jdbc.queryForObject("select email_verified from accounts where id=?",Boolean.class,owner)))return;
        Instant now=clock.instant();
        if(jdbc.queryForObject("select count(*) from account_tokens where owner_id=? and purpose=? and created_at>?",Integer.class,owner,purpose,Timestamp.from(now.minusSeconds(60)))>0)return;
        jdbc.update("update account_tokens set used_at=? where owner_id=? and purpose=? and used_at is null",Timestamp.from(now),owner,purpose);
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expires=now.plusSeconds(purpose.equals("RESET")?1800:86400);
        jdbc.update("insert into account_tokens(token_hash,owner_id,purpose,expires_at,created_at) values (?,?,?,?,?)",hash(token),owner,purpose,Timestamp.from(expires),Timestamp.from(now));
        String title=purpose.equals("RESET")?"Đặt lại mật khẩu LinkHub":"Xác minh email LinkHub";
        String body=title+"\n\nMở liên kết sau và xác nhận trên trang:\n"+baseUrl+"/app#"+(purpose.equals("RESET")?"reset":"verify")+"="+token+"\n\nLiên kết chỉ dùng một lần, có hiệu lực "+(purpose.equals("RESET")?"30 phút":"24 giờ")+". Nếu không yêu cầu, bạn có thể bỏ qua email này.";
        UUID notification=UUID.randomUUID();
        jdbc.update("insert into notifications(id,owner_id,event_key,kind,title,body,target) values (?,?,?,?,?,?,?)",notification,owner,"security:"+UUID.randomUUID(),"ACCOUNT_SECURITY",title,"Hướng dẫn đã được đưa vào hàng đợi email. Nếu bạn không yêu cầu, hãy kiểm tra bảo mật tài khoản.","account");
        jdbc.update("insert into mail_outbox(id,notification_id,recipient,subject,body,encrypted,expires_at) values (?,?,?,?,?,true,?)",UUID.randomUUID(),notification,email,title,cipher.encrypt(body),Timestamp.from(expires));
    }
    @Transactional public void verify(String token){UUID owner=consume(token,"VERIFY");jdbc.update("update accounts set email_verified=true where id=?",owner);}
    @Transactional public void reset(String token,String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72)throw invalid("Mật khẩu tối đa 72 byte UTF-8.");
        UUID owner=consume(token,"RESET");jdbc.update("update accounts set password_hash=?,auth_version=auth_version+1 where id=?",encoder.encode(password),owner);
        jdbc.update("update account_tokens set used_at=? where owner_id=? and purpose='RESET' and used_at is null",Timestamp.from(clock.instant()),owner);
    }
    private UUID consume(String token,String purpose) {
        String digest=hash(token);var ids=jdbc.query("select owner_id from account_tokens where token_hash=? and purpose=?",(r,n)->r.getObject(1,UUID.class),digest,purpose);
        if(ids.isEmpty())throw invalid("Liên kết không hợp lệ, đã dùng hoặc hết hạn.");
        UUID owner=ids.getFirst();jdbc.queryForObject("select id from accounts where id=? for update",UUID.class,owner);
        int changed=jdbc.update("update account_tokens set used_at=? where token_hash=? and purpose=? and used_at is null and expires_at>?",Timestamp.from(clock.instant()),digest,purpose,Timestamp.from(clock.instant()));
        if(changed!=1)throw invalid("Liên kết không hợp lệ, đã dùng hoặc hết hạn.");return owner;
    }
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static ResponseStatusException invalid(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
