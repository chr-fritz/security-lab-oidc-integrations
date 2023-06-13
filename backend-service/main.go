package main

import "net/http"
import "github.com/sirupsen/logrus"

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/hello", hello)

	if err := http.ListenAndServe(":8081", mux); err != nil {
		logrus.Error(err)
	}
}

func hello(writer http.ResponseWriter, _ *http.Request) {
	writer.WriteHeader(200)
	_, _ = writer.Write([]byte("unauthorized user"))
}
