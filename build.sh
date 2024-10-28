#!/usr/bin/env bash

export HIMMELSSTUERMER_PROFILE=test

lein deps && \
    # lein test && \
    # lein eastwood && \
    lein analyze && \
    java -agentlib:native-image-agent=config-output-dir=config_dir,experimental-class-define-support -jar target/uberjar/analyze.jar && \
    ls -lah config_dir && \
    lein uberjar && \
    native-image \
        --enable-url-protocols=https \
        -H:ConfigurationFileDirectories=config_dir \
        --no-fallback \
        --native-image-info \
        --verbose \
        -jar target/uberjar/uberjar.jar \
        -H:Name=lambda \
        -H:+ReportExceptionStackTraces \
        -H:Class=himmelsstuermer.core \
        -H:+UnlockExperimentalVMOptions \
        -H:+ReportUnsupportedElementsAtRuntime
# 2>&1 | tee build_output.log
# cat build_output.log | grep initialize-at-run-time
bash

# java -agentlib:native-image-agent=config-output-dir=config_dir -jar target/uberjar/uberjar.jar -m kaocha.runner --fail-fast && \


        # --initialize-at-run-time=taoensso.telemere.streams__ini,himmelsstuermer.core.config__init,clojure.lang.MultiFn,babashka.http_client.internal.multipart__init,babashka.http_client.interceptors__init,babashka.http_client.internal__init,clojure.core.async.impl.concurrent__init,clojure.core.rrb_vector.rrbt__init,clojure.core.async.impl.buffers__init,clojure.core.async.impl.exec.threadpool__init,clojure.core.rrb_vector__init,clojure.core.cache__init,clojure.core.server__init,datalog.parser.type__init,datalog.parser__init,clojure.data.fressian__init,clojure.pprint.dispatch__init,clojure.data__init,clojure.datafy__init,clojure.pprint__init,himmelsstuermer.api.texts__init,me.tonsky.persistent_sorted_set__init,cognitect.transit__init,datalog.parser.pull__init,clojure.core.async.impl.dispatch__init,clojure.core.cache.wrapped__init,clojure.core.reducers__init,clojure.core.rrb_vector.nodes__init,clojure.spec.alpha__init,clojure.stacktrace__init,datalog.parser.impl.proto__init,datalog.parser.impl.util__init,datalog.parser.impl__init,datalog.parser.util,datahike.config__init,clojure.core.rrb_vector.transients__init,clojure.core.async__init,clojure,core.async.impl.timers__init,clojure.core.async.impl.channels__init,himmelsstuermer.api__init,clojure.lang.Var,clojure.lang.Var.root \

# --initialize-at-run-time \
        # --initialize-at-build-time=clojure \
        # --initialize-at-build-time=com.fasterxml.jackson \

        # -J-Djdk.internal.vm.ci=disable \
