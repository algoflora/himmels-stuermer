(ns himmelsstuermer.logging
  (:require
    [cheshire.core :refer [generate-string]]
    [clojure.stacktrace :as st]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.impl.timer :refer [millis-passed]]
    [me.raynes.fs :as fs]
    [taoensso.timbre :as timbre]))


(defonce ^:private lambda-context (atom nil))


(defn set-lambda-context!
  [context]
  (reset! lambda-context context))


(defn- process-vargs
  [vargs]
  (let [cnt (count vargs)
        [arg1 arg2] vargs
        argn (last vargs)]
    (cond
      (not (or (keyword? arg1) (nil? arg1)))
      (process-vargs (apply conj [nil] vargs))

      (not (or (string? arg2) (nil? arg2)))
      (process-vargs (apply conj [arg1 ""] (rest vargs)))

      (nil? arg2)
      (process-vargs (apply conj [arg1 ""] (drop 2 vargs)))

      (= cnt (+ 2 (count (re-seq #"(?<!%)%(\.\d+)?[sdfx]" arg2))))
      (process-vargs (conj vargs {}))

      (and (= cnt (+ 3 (count (re-seq #"(?<!%)%(\.\d+)?[sdfx]" arg2))))
           (map? argn))
      (merge {:event-name arg1
              :message-text (let [text (apply format (rest vargs))]
                              (if (< 1024 (count text))
                                (str (subs text 0 1023) "...")
                                text))}
             argn)

      :else (throw (ex-info "Bad log arguments!" {:log-arguments vargs})))))


(def lambda-stdout-appender
  {:enabled?   (= conf/profile :test)
   :async?     true
   :min-level  :info
   :rate-limit nil
   :output-fn  :inherit
   :fn         (fn [{:keys [level ?err vargs ?ns-str
                            ?file hostname_ timestamp_ ?line]}]
                 (let [data (process-vargs vargs)]
                   (println (format "%s [%s] <%s:%s:%s> - %s%s"
                                    @timestamp_
                                    (-> level name str/upper-case)
                                    @hostname_
                                    (or ?ns-str ?file "?")
                                    (or ?line "?")
                                    (if (not-empty (:message-text data))
                                      (:message-text data)
                                      (str (:event-name data)))
                                    (if ?err
                                      (str "\n" (with-out-str (st/print-stack-trace ?err)))
                                      "")))))})


(defn- check-json
  [data]
  (postwalk #(try (generate-string %)
                  %
                  (catch java.lang.Exception _
                    (str/trimr (prn-str %))))
            data))


(defn- obj-prepare
  [event]
  (let [data (select-keys (merge event (process-vargs (:vargs event)))
                          [:instant :message-text :event-name :vargs
                           :?err :?file :?line])
        data (assoc data :__millis-passed (millis-passed))
        vargs (:vargs data)]
    (-> data
        (dissoc :vargs)
        (assoc :data (last vargs)))))


(def lambda-json-println-appender
  {:enabled? (= conf/profile :aws)
   :async? true
   :min-level :debug
   :fn (fn [event]
         (println (generate-string (check-json (obj-prepare event)))))})


(def lambda-json-spit-appender
  {:enabled? (= conf/profile :test)
   :min-level :debug
   :fn (fn [event]
         (spit "logs.json" (str (generate-string (check-json (obj-prepare event))) "\n") :append true))})


(defn- edn-prepare
  [obj]
  (postwalk (fn [x]
              (cond
                (instance? java.util.regex.Pattern x) (prn-str x)
                :else x))
            obj))


(def lambda-edn-spit-appender
  {:enabled? (= conf/profile :test)
   :async? false
   :min-level :debug
   :fn (fn [event]
         (with-open [w (java.io.FileWriter. "logs.edn" true)]
           (let [line  (with-out-str (print-method (-> event obj-prepare #_edn-prepare) *out*))
                 line' (-> line
                           (str/replace #"#\"[^\"]+\"" "<REGEX>")
                           (str/replace #"#'" "'"))]
             (.write w (str line' "\n")))))})


(defn delete-if-exists
  [f]
  (when (fs/exists? f)
    (fs/delete f)))


(delete-if-exists "logs.json")


(delete-if-exists "logs.edn")


(timbre/merge-config! {:appenders (merge {:println lambda-stdout-appender
                                          ;; :json-file lambda-json-spit-appender
                                          :edn-file lambda-edn-spit-appender
                                          :json-print lambda-json-println-appender})})


(defn inject-lambda-context!
  []
  (timbre/merge-config!
    {:middleware [#(assoc % :lambda-context @lambda-context)]}))
