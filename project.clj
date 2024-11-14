(defproject io.github.algoflora/himmelsstuermer "0.1.3-SNAPSHOT"
  :description "Himmelsstuermer - A framework for complex Telegram Bots development"
  :url "https://github.com/algoflora"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[aero/aero "1.1.6"]
                 [cheshire "5.13.0"]
                 [com.taoensso/telemere "1.0.0-RC1"]
                 [com.taoensso/slf4j-telemere "1.0.0-beta21"]
                 [org.slf4j/slf4j-api "2.0.16"]
                 [http-kit/http-kit "2.8.0"]
                 [me.raynes/fs "1.4.6"
                  :exclusions [org.tukaani/xz]]
                 [metosin/malli "0.16.4"]
                 [missionary "b.40"]
                 [org.clojure/clojure "1.12.0"]
                 [selmer "1.12.61"]
                 [tick/tick "1.0"]

                 [datascript "1.7.3"]
                 [com.github.igrishaev/dynamodb "0.1.2"]
                 [com.taoensso/nippy "3.5.0-RC1"]
                 [org.clojure/data.codec "0.1.1"]
                 [com.amazonaws/aws-lambda-java-runtime-interface-client "2.6.0"]]

  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "1.0.0-RC3"]
            [io.taylorwood/lein-native-image "0.3.1"]]

  :native-image {:name "lambda"}

  :source-paths ["src"]
  :java-source-paths ["src"]
  :resource-paths ["resources"]
  :main ^:skip-aot himmelsstuermer.core
  :aot :all

  :target-path "target/%s"

  :profiles {:test      {:source-paths ["src" "test"]
                         :resource-paths ["resources" "test/resources"]
                         :dependencies [[lambdaisland/kaocha "1.91.1392"
                                         :exclusions [net.incongru.watchservice/barbary-watchservice]]]
                         :jvm-opts ["-Dhimmelsstuermer.malli.instrument=true"
                                    "-Dhimmelsstuermer.profile=test"]}

             :uber      {:main himmelsstuermer.core
                         :aot :all #_[himmelsstuermer.core]
                         :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                         :uberjar-name "himmelsstuermer.jar"}

             :uber-test {:main himmelsstuermer.test-runner
                         :aot [himmelsstuermer.test-runner]
                         :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                         :uberjar-name "himmelsstuermer-test.jar"}

             :native    {:dependencies [[com.github.clj-easy/graal-build-time "1.0.5"]]
                         :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}

  :aliases {"test" ["with-profile" "+test" "run" "-m" "himmelsstuermer.test-runner/kaocha" "--fail-fast"]
            "aws"  ["run" "-m" "himmelsstuermer.aws/deploy!" {:lamdba-name "testing"
                                                              :cluster "local-dev"}]})
