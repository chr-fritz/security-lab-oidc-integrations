package de.qaware.security.lab.oidc.middleware.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController("/api")
public class SampleResource {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${hello.backend.url}")
    private String helloBackendUrl;

    @GetMapping("/hello")
    public String hello() {
        String backend = restTemplate.getForObject(helloBackendUrl, String.class);
        return "Hello " + backend + "!";
    }
}
