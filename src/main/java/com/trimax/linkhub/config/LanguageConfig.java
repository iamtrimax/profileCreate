package com.trimax.linkhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import java.util.List;
import java.util.Locale;

@Configuration
public class LanguageConfig {
    @Bean public LocaleResolver localeResolver() {
        var resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("vi")));
        resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
        return resolver;
    }
}
