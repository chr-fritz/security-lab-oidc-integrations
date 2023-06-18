package de.qaware.security.lab.oidc.middleware.server;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.Token;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

@Service
public class ClientTokenFetcher {

    private final AtomicReference<Token> token = new AtomicReference<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final ConfigProperties properties;

    public ClientTokenFetcher(ConfigProperties properties) {
        this.properties = properties;
    }

    private Token doFetchToken() {
        try {
            TokenRequest tokenRequest = new TokenRequest(
                properties.getTokenEndpoint(),
                new ClientSecretBasic(new ClientID(properties.getClientId()), new Secret(properties.getClientSecret())),
                new ClientCredentialsGrant()
            );
            HTTPResponse response = tokenRequest.toHTTPRequest().send();
            TokenResponse tokenResponse = TokenResponse.parse(response);

            if (tokenResponse instanceof AccessTokenResponse accessTokenResponse) {
                AccessToken accessToken = accessTokenResponse.getTokens().getAccessToken();
                executorService.schedule(this::doFetchToken, Math.max(10, accessToken.getLifetime() - 10), SECONDS);
                token.set(accessToken);
                return accessToken;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParseException e) {
            throw new RuntimeException("Could not fetch token", e);
        }
        throw new RuntimeException("Could not fetch token");
    }

    public Token getToken() {
        if (token.get() == null) {
            Future<Token> future = executorService.submit(this::doFetchToken);
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException rtEx) {
                    throw rtEx;
                }

                throw new RuntimeException(cause);
            }
        }
        return token.get();
    }
}
