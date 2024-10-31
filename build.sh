#!/usr/bin/env bash

printf "\n\nBuild start...\n\n" &&

    export HIMMELSSTUERMER_PROFILE=test &&
    lein test &&

    printf "\n\nBuilding test native image...\n\n" &&
    
    # cp -r test/resources/* resources/ &&
    lein with-profiles +test,+uber-test,+native native-image &&
    
    printf "\n\nTesting native image...\n\n" &&

    ./target/test+uber-test+native/himmelsstuermer-native &&

    # printf "\n\nBuilding native image...\n\n" &&

    # export HIMMELSSTUERMER_PROFILE=aws &&
    # lein with-profiles +uber,+native native-image &&

    # printf "\n\nRunning native image... (must fail!)\n\n" &&
    
    # ./target/uber+native/himmelsstuermer-native

bash
