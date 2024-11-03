#!/usr/bin/env bash

IMAGE_NAME="himmelsstuermer-dev-docker" #"$(LC_CTYPE=C tr -dc 'a-z0-9' </dev/urandom | head -c 12)"

docker build -f Dockerfile.dev -t $IMAGE_NAME . && \
    docker run --rm -v ~/.m2:/root/.m2 -ti $IMAGE_NAME
