package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

@Service @RequiredArgsConstructor
public class NotificationStream {
    private final NotificationService notifications;
    private final Map<UUID,Client> clients=new ConcurrentHashMap<>();
    private static class Client {
        final UUID owner; final SseEmitter emitter; final BooleanSupplier authenticated;
        NotificationService.Inbox previous;
        Client(UUID owner,SseEmitter emitter,BooleanSupplier authenticated){this.owner=owner;this.emitter=emitter;this.authenticated=authenticated;}
    }
    public synchronized SseEmitter connect(UUID owner,BooleanSupplier authenticated) {
        if(clients.size()>=1000 || clients.values().stream().filter(c->c.owner.equals(owner)).count()>=5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Quá nhiều kết nối thông báo. Hãy đóng bớt tab.");
        UUID key=UUID.randomUUID();var emitter=new SseEmitter(60000L);var client=new Client(owner,emitter,authenticated);
        clients.put(key,client);
        emitter.onCompletion(()->clients.remove(key));emitter.onTimeout(()->{clients.remove(key);emitter.complete();});emitter.onError(e->clients.remove(key));
        try {emitter.send(SseEmitter.event().comment("connected").reconnectTime(5000));}catch(Exception e){clients.remove(key);emitter.complete();}
        return emitter;
    }
    @Scheduled(fixedDelayString="${linkhub.notifications.poll-ms:3000}")
    public void pulse() {
        Map<UUID,NotificationService.Inbox> snapshots=new HashMap<>();
        clients.forEach((key,client)->{
            try {
                if(!client.authenticated.getAsBoolean()){clients.remove(key);client.emitter.complete();return;}
                var current=snapshots.computeIfAbsent(client.owner,o->notifications.inbox(o,0,false));
                if(!current.equals(client.previous)) {
                    // Full snapshots on reconnect avoid cursor gaps when transactions commit out of order.
                    client.emitter.send(SseEmitter.event().name("notifications").data(current));client.previous=current;
                }else client.emitter.send(SseEmitter.event().comment("heartbeat"));
            }catch(Exception e){clients.remove(key);client.emitter.complete();}
        });
    }
}
