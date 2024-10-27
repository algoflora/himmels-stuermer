#!/usr/bin/env bash

docker build -f Dockerfile.build -t himmelsstuermer-build . && docker run -v ./.build/.m2:/root/.m2 -ti himmelsstuermer-build
