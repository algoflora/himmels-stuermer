#!/usr/bin/env bash

printf "\n\nBuild start...\n\n" &&

    java -jar ./test/resources/dynamodb/DynamoDBLocal.jar -inMemory &

    export HIMMELSSTUERMER_PROFILE=test &&
    # export HIMMELSSTUERMER_USE_LOCAL_DYNAMODB=true &&
    lein test &&

    # printf "\n\nBuilding and analyzing test uberjar...\n\n" &&
    
    # lein clean &&
    # lein with-profiles +test,+uber-test uberjar &&
    # java -agentlib:native-image-agent=config-output-dir=./ -jar target/uberjar/himmelsstuermer-test.jar &&

    # printf "\n\nAdding reachability-metadata.json to META-INF...\n\n" &&
    
    # cp ./reachability-metadata.json resources/META-INF/native-image/io.github.algoflora/himmelsstuermer/reachability-metadata.json &&

    printf "\n\nBuilding test native image...\n\n" &&
    
    lein clean &&
    lein with-profiles +test,+uber-test,+native native-image &&
    
    printf "\n\nTesting native image...\n\n" &&

    ./target/test+uber-test+native/lambda

    # printf "\n\nBuilding native image...\n\n" &&

    # export HIMMELSSTUERMER_PROFILE=aws &&
    # lein with-profiles +uber,+native native-image &&

    # printf "\n\nRunning native image... (must fail!)\n\n" &&
    
    # ./target/uber+native/lambda

bash
