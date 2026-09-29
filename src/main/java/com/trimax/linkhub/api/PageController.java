package com.trimax.linkhub.api;

import com.trimax.linkhub.service.LinkHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller @RequiredArgsConstructor
public class PageController {
    private final LinkHubService service;
    @GetMapping({"/", "/app"}) @ResponseBody
    public ResponseEntity<Resource> app() { return page("app.html"); }
    @GetMapping("/ops/metrics") @ResponseBody
    public ResponseEntity<Resource> metrics() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource("static/assets/metrics.html")); }
    @GetMapping("/{username:[a-zA-Z0-9][a-zA-Z0-9_]{2,29}}") @ResponseBody
    public ResponseEntity<Resource> profile(@PathVariable String username) {
        service.publicProfile(username);
        return page("profile.html");
    }
    private ResponseEntity<Resource> page(String name) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new ClassPathResource("pages/" + name));
    }
}
