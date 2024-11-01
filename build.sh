#!/usr/bin/env bash

printf "\n\nBuild start...\n\n" &&

    export HIMMELSSTUERMER_PROFILE=test &&
    lein test &&

#     lein with-profiles +test,+uber-test uberjar &&
#     java -agentlib:native-image-agent=config-output-dir=./reflect-config -jar target/uberjar/himmelsstuermer-test.jar

# bash

    printf "\n\nBuilding test native image...\n\n" &&
    
    lein clean &&
    lein with-profiles +test,+uber-test,+native native-image &&
    
    printf "\n\nTesting native image...\n\n" &&

    ./target/test+uber-test+native/himmelsstuermer-native

    # printf "\n\nBuilding native image...\n\n" &&

    # export HIMMELSSTUERMER_PROFILE=aws &&
    # lein with-profiles +uber,+native native-image &&

    # printf "\n\nRunning native image... (must fail!)\n\n" &&
    
    # ./target/uber+native/himmelsstuermer-native

bash
