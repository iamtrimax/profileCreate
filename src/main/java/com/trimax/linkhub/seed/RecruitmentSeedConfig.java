package com.trimax.linkhub.seed;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@Configuration @Profile("seed-recruitment") @RequiredArgsConstructor
public class RecruitmentSeedConfig {
    private static final Logger log=LoggerFactory.getLogger(RecruitmentSeedConfig.class);
    private final Environment environment;
    @Bean ApplicationRunner recruitmentSeedRunner(RecruitmentSeedService service) {
        return args->{
            if(environment.matchesProfiles("prod","production"))throw new IllegalStateException("Không chạy seed demo cùng profile production.");
            var result=service.seed();
            log.info("Recruitment demo seeded: {} accounts created, {} demo accounts, {} demo jobs. Login: demo_recruiter@linkhub.test. See docs/recruitment-seed.md.",result.accountsCreated(),result.accountsTotal(),result.jobsTotal());
        };
    }
    @EventListener void ready(ApplicationReadyEvent event) {
        if(environment.getProperty("linkhub.seed.exit",Boolean.class,false))event.getApplicationContext().close();
    }
}
