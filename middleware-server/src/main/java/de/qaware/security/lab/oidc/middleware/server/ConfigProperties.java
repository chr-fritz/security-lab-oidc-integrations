package de.qaware.security.lab.oidc.middleware.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Data
@Configuration
@ConfigurationProperties(prefix = "middleware")
public class ConfigProperties {
    private URI tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private boolean exchangeToken;
}
