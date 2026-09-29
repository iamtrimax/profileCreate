package com.trimax.linkhub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component @ConditionalOnProperty(name="linkhub.mail.enabled",havingValue="true")
public class OutboxMailer {
    private final JavaMailSender sender;
    private final String from;
    private final SecurityMailCipher cipher;
    public OutboxMailer(JavaMailSender sender,@Value("${linkhub.mail.from}") String from,SecurityMailCipher cipher) {this.sender=sender;this.from=from;this.cipher=cipher;}
    public void send(MailOutboxStore.Job job) throws MessagingException {
        MimeMessage message=sender.createMimeMessage();
        var helper=new MimeMessageHelper(message,StandardCharsets.UTF_8.name());
        helper.setFrom(from);helper.setTo(job.recipient());helper.setSubject(job.subject());helper.setText(job.encrypted()?cipher.decrypt(job.body()):job.body(),false);
        message.setHeader("X-LinkHub-Event",job.id().toString());
        message.saveChanges();
        message.setHeader("Message-ID","<"+job.id()+"@linkhub.local>");
        sender.send(message);
    }
}
