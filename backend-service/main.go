package main

import (
	"context"
	"fmt"
	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
	"log/slog"
	"net"
	"net/http"
	"os"
	"strings"
	"sync"
)

var jwksFunc keyfunc.Keyfunc
var mutex = sync.RWMutex{}
var parser *jwt.Parser

type keycloakClaims struct {
	PreferredUsername string `json:"preferred_username,omitempty"`
	GivenName         string `json:"given_name,omitempty"`
	FamilyName        string `json:"family_name,omitempty"`
	Email             string `json:"email,omitempty"`
	EmailVerified     bool   `json:"email_verified,omitempty"`

	jwt.RegisteredClaims
}

func main() {
	ctx, cancelFunc := context.WithCancel(context.Background())
	defer cancelFunc()
	var err error
	jwksFunc, err = getJwksFunc(ctx)
	if err != nil {
		panic(err)
	}
	parser = jwt.NewParser(jwt.WithAudience("backend-service"), jwt.WithIssuer(os.Getenv("ISSUER")))

	mux := http.NewServeMux()
	mux.HandleFunc("/hello", hello)
	mux.HandleFunc("/health", health)

	srv := http.Server{
		Addr:    ":8081",
		Handler: mux,
		BaseContext: func(listener net.Listener) context.Context {
			return context.WithValue(ctx, "listenerAddr", listener.Addr())
		},
	}

	if err := srv.ListenAndServe(); err != nil {
		slog.Warn("Error while handling http requests", "err", err)
	}
}

func hello(writer http.ResponseWriter, request *http.Request) {
	token, err := validateAuthentication(request)
	if err != nil || !token.Valid {
		if err != nil {
			slog.Warn("Can't validate authorization", "err", err)
		}
		writer.WriteHeader(403)
		_, _ = writer.Write([]byte("invalid authorization"))
		return
	}

	claims, ok := token.Claims.(*keycloakClaims)
	if !ok {
		slog.Error("Unexpected claims")
		writer.WriteHeader(401)
		_, _ = writer.Write([]byte("invalid authorization"))
		return
	}

	writer.WriteHeader(200)
	_, _ = writer.Write([]byte(claims.PreferredUsername))
}

func health(writer http.ResponseWriter, _ *http.Request) {
	writer.WriteHeader(200)
	_, _ = writer.Write([]byte("Service is healthy"))
}

func validateAuthentication(request *http.Request) (*jwt.Token, error) {
	mutex.RLock()
	defer mutex.RUnlock()
	authorizationHeader := request.Header.Get("authorization")
	parts := strings.Split(authorizationHeader, " ")
	if len(parts) != 2 {
		return nil, fmt.Errorf("wrong authorization header format. expecting two parts got %d", len(parts))
	}
	if strings.ToLower(parts[0]) != "bearer" {
		return nil, fmt.Errorf("expecting bearer token, but not found")
	}
	return parser.ParseWithClaims(parts[1], &keycloakClaims{}, jwksFunc.Keyfunc)
}

func getJwksFunc(ctx context.Context) (keyfunc.Keyfunc, error) {
	// Get the JWKS URL from an environment variable.
	jwksURL := os.Getenv("JWKS_URL")

	// Confirm the environment variable is not empty.
	if jwksURL == "" {
		return nil, fmt.Errorf("JWKS_URL environment variable must be populated")
	}
	return keyfunc.NewDefaultCtx(ctx, []string{jwksURL}) // See recommended options in the examples directory.
}
