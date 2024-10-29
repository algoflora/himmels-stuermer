#!/usr/bin/env bash

IMAGE_NAME="himmelsstuermer-build-docker" #"$(LC_CTYPE=C tr -dc 'a-z0-9' </dev/urandom | head -c 12)"

docker build -f Dockerfile.build -t $IMAGE_NAME . && \
    docker run --rm -v ./.build/.m2:/root/.m2 -ti $IMAGE_NAME
