package de.qaware.security.lab.oidc.middleware.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(c -> c
                .requestMatchers("/actuator/**").anonymous()
                .requestMatchers("/api", "/hello").authenticated()
            )
            .oauth2ResourceServer(rs -> rs.jwt(withDefaults()))
            .build();
    }
}
