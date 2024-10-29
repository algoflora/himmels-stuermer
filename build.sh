#!/usr/bin/env bash

export HIMMELSSTUERMER_PROFILE=test
clj -M:test
# clojure -X:analyze :jar analyze.jar :aliases '[:test]'
# java -agentlib:native-image-agent=config-output-dir=./native-image-config -jar analyze.jar
export HIMMELSSTUERMER_PROFILE=aws
clj -M:native
    
./lambda # grep initialize-at-run-time build_output.log

bash

# native-image \
#         --initialize-at-build-time \
#         --initialize-at-run-time=missionary.core.,missionary.impl. \
#         -H:IncludeResources=".*" \
#         --report-unsupported-elements-at-runtime \
#         --verbose \
#         -jar target/uberjar/native.jar \
#         -H:Name=lambda
