package main

import (
	"fmt"
	"github.com/MicahParks/keyfunc/v2"
	"github.com/golang-jwt/jwt/v5"
	"github.com/sirupsen/logrus"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

var jwks *keyfunc.JWKS
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
	if err := updateJwks(); err != nil {
		panic(err)
	}
	parser = jwt.NewParser(jwt.WithAudience("backend-service"), jwt.WithIssuer(os.Getenv("ISSUER")))

	mux := http.NewServeMux()
	mux.HandleFunc("/hello", hello)
	mux.HandleFunc("/health", health)

	if err := http.ListenAndServe(":8081", mux); err != nil {
		logrus.Error(err)
	}
}

func hello(writer http.ResponseWriter, request *http.Request) {
	token, err := validateAuthentication(request)
	if err != nil || !token.Valid {
		if err != nil {
			logrus.Warnf("invalid authorization: %s", err)
		}
		writer.WriteHeader(403)
		_, _ = writer.Write([]byte("invalid authorization"))
		return
	}

	claims, ok := token.Claims.(*keycloakClaims)
	if !ok {
		logrus.Error("unexpected claims")
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
	return parser.ParseWithClaims(parts[1], &keycloakClaims{}, jwks.Keyfunc)
}

func updateJwks() error {
	ticker := time.NewTicker(1 * time.Hour)
	// Get the JWKS URL from an environment variable.
	jwksURL := os.Getenv("JWKS_URL")

	// Confirm the environment variable is not empty.
	if jwksURL == "" {
		return fmt.Errorf("JWKS_URL environment variable must be populated")
	}
	go func() {
		for {
			tmpJwks, err := keyfunc.Get(jwksURL, keyfunc.Options{}) // See recommended options in the examples directory.
			if err != nil {
				logrus.Errorf("failed to get the JWKS from the given URL.\nError: %s", err)
			}
			mutex.Lock()
			jwks = tmpJwks
			mutex.Unlock()
			<-ticker.C
		}
	}()
	return nil
}
