(defproject io.github.algoflora/himmelsstuermer "0.1.0"
  :description "Himmelsstuermer - A framework for complex Telegram Bots development"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[aero/aero "1.1.6"]
                 [cheshire "5.13.0"]
                 [com.hyperfiddle/rcf "20220926-202227"]
                 [com.taoensso/telemere "1.0.0-beta25"]
                 [io.replikativ/datahike "0.6.1591"]
                 [http-kit/http-kit "2.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [metosin/malli "0.16.4"]
                 [missionary "b.40"]
                 [org.clojure/clojure "1.12.0"]
                 [resauce "0.2.0"]
                 [tick/tick "1.0"]]

  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "1.0.0-RC3"]]

  :source-paths ["src"]
  :resource-paths ["resources"]
  :main ^:skip-aot himmelsstuermer.core

  :target-path "target/%s"

  :profiles {:dev {:dependencies [[lambdaisland/kaocha "1.91.1392"
                                   :exclusions [net.incongru.watchservice/barbary-watchservice]]]
                   :source-paths ["src" "test"]
                   :resource-paths ["resources" "test/resources"]
                   :jvm-opts ["-Dhimmelsstuermer.malli.instrument=true"
                              "-Dhimmelsstuermer.profile=test"]}

             :native  {:aot [datahike.api datahike.api.impl datahike.api.specification datahike.array datahike.cli datahike.config datahike.connections datahike.connector datahike.constants datahike.core datahike.datom datahike.db datahike.db.interface datahike.db.search datahike.db.transaction datahike.db.utils datahike.experimental.gc datahike.experimental.versioning datahike.http.client datahike.http.writer datahike.impl.entity datahike.index datahike.index.hitchhiker-tree datahike.index.hitchhiker-tree.insert datahike.index.hitchhiker-tree.upsert datahike.index.interface datahike.index.persistent-set datahike.index.utils datahike.integration-test datahike.json datahike.lru datahike.middleware.query datahike.middleware.utils datahike.migrate datahike.norm.norm datahike.pod datahike.pull-api datahike.query datahike.query-stats datahike.readers datahike.remote datahike.schema datahike.spec datahike.store datahike.tools datahike.transit datahike.writer datahike.writing
                             himmelsstuermer.core
                             himmelsstuermer.api
                             himmelsstuermer.api.buttons
                             himmelsstuermer.api.texts
                             ;; himmelsstuermer.api.transactor
                             himmelsstuermer.core.config
                             himmelsstuermer.core.dispatcher
                             himmelsstuermer.core.init
                             himmelsstuermer.core.logging
                             ;; himmelsstuermer.core.state
                             ;; himmelsstuermer.core.user
                             himmelsstuermer.impl.api
                             himmelsstuermer.impl.buttons
                             himmelsstuermer.impl.callbacks
                             ;; himmelsstuermer.impl.error
                             ;; himmelsstuermer.impl.texts
                             himmelsstuermer.impl.transactor
                             himmelsstuermer.handler
                             himmelsstuermer.misc
                             ;; himmelsstuermer.user
                             ]
                       :uberjar-exclusions ["himmelsstuermer.e2e.*"
                                            "himmelsstuermer.aws.*"]
                       :uberjar-name "native.jar"
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                       :main himmelsstuermer.core}

             :analyze {:aot :all
                       :dependencies [[lambdaisland/kaocha "1.91.1392"
                                       :exclusions [net.incongru.watchservice/barbary-watchservice]]]
                       :source-paths ["test"]
                       :resource-paths ["test/resources"]
                       :uberjar-exclusions ["himmelsstuermer.aws.*"]
                       :uberjar-name "analyze.jar"
                       :main himmelsstuermer.e2e-test}}
  :aliases {"test"    ["with-profile" "dev" "run" "-m" "kaocha.runner" "--fail-fast"]
            "analyze" ["with-profile" "analyze" "uberjar"]
            "native"  ["with-profile" "native" "uberjar"]})
