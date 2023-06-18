package de.qaware.security.lab.oidc.middleware.server;

import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.Token;
import com.nimbusds.oauth2.sdk.token.TokenTypeURI;
import com.nimbusds.oauth2.sdk.tokenexchange.TokenExchangeGrant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.stereotype.Service;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

@Service
public class TokenExchangeHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    private final ClientTokenFetcher clientTokenFetcher;
    private final ConfigProperties configProperties;

    private final ConcurrentMap<AbstractOAuth2Token, AccessToken> tokenCache = new ConcurrentReferenceHashMap<>(100);

    @Autowired
    public TokenExchangeHttpRequestInterceptor(ClientTokenFetcher clientTokenFetcher, ConfigProperties configProperties) {
        this.clientTokenFetcher = clientTokenFetcher;
        this.configProperties = configProperties;
    }

    private Optional<AbstractOAuth2Token> findAuthenticatedToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (authentication.getCredentials() instanceof AbstractOAuth2Token token) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    private AccessToken exchangeToken(AbstractOAuth2Token token) throws IOException {
        try {
            Token tokenValue = new BearerAccessToken(token.getTokenValue());
            TokenExchangeGrant tokenExchangeGrant = new TokenExchangeGrant(
                tokenValue, TokenTypeURI.ACCESS_TOKEN,
                clientTokenFetcher.getToken(), TokenTypeURI.ACCESS_TOKEN,
//                null, null,
                TokenTypeURI.ACCESS_TOKEN, List.of()
            );

            TokenRequest tokenRequest = new TokenRequest(
                configProperties.getTokenEndpoint(),
                new ClientSecretBasic(new ClientID(configProperties.getClientId()), new Secret(configProperties.getClientSecret())),
                tokenExchangeGrant
            );
            HTTPResponse response = tokenRequest.toHTTPRequest().send();
            TokenResponse tokenResponse = TokenResponse.parse(response);

            if (tokenResponse instanceof AccessTokenResponse accessTokenResponse) {
                AccessToken accessToken = accessTokenResponse.getTokens().getAccessToken();
                tokenCache.put(token, accessToken);
                return accessToken;
            }
            throw new IOException("Unexpected response: " + tokenResponse.toErrorResponse().toJSONObject().toString());
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    @Override
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
        Optional<AbstractOAuth2Token> authenticatedToken = findAuthenticatedToken();

        if (authenticatedToken.isEmpty()) {
            return execution.execute(request, body);
        }

        AccessToken accessToken;
        if (tokenCache.containsKey(authenticatedToken.get())) {
            accessToken = tokenCache.get(authenticatedToken.get());
        } else {
            accessToken = exchangeToken(authenticatedToken.get());
        }
        request.getHeaders().setBearerAuth(accessToken.getValue());

        return execution.execute(request, body);
    }
}
