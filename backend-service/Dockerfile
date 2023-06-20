FROM scratch
ARG TARGETARCH
ADD --chmod=755 backend-service-$TARGETARCH /backend-service
ENTRYPOINT ["/backend-service"]
CMD [ ]
