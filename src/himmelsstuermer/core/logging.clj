(ns himmelsstuermer.core.logging
  (:require
    [cheshire.core :as json]
    [cheshire.generate :as gen]
    [clojure.walk :refer [postwalk]]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.core.state :as s]
    [me.raynes.fs :as fs]
    [taoensso.telemere :as tt]))


(gen/add-encoder Object
                 (fn [obj ^com.fasterxml.jackson.core.JsonGenerator json-generator]
                   (.writeString json-generator (str obj))))


(defonce ^:private nano-timer (atom nil))


(defn reset-nano-timer!
  []
  (reset! nano-timer (System/nanoTime)))


(def console-json-handler
  (tt/handler:console {:output-fn (tt/pr-signal-fn {:pr-fn json/generate-string})}))


(def file-json-disposable-handler
  (do
    (fs/delete "./logs/log.json")
    (tt/handler:file {:output-fn (tt/pr-signal-fn {:pr-fn json/encode})
                      :path "./logs.json"})))


(def file-edn-disposable-handler
  (do
    (fs/delete "./log.edn")
    (tt/handler:file {:output-fn (tt/pr-signal-fn {:pr-fn :edn})
                      :path "./logs.edn"})))


(defn init-logging!
  [project-info profile]
  (when (= :aws profile)
    (tt/add-handler! :console-json console-json-handler))
  (when (= :test profile)
    (tt/add-handler! :file-json-disposable file-json-disposable-handler)
    (tt/add-handler! :file-edn-disposable file-edn-disposable-handler))
  (tt/set-ctx! {:project project-info :profile profile})
  (tt/set-middleware! (fn [signal]
                        (let [signal' (postwalk #(if (or (instance? clojure.lang.Var %)
                                                         (instance? java.util.regex.Pattern %)) (str %) %) signal)
                              nt @nano-timer]
                          (cond-> signal'
                            (some? nt) (assoc :millis-passed
                                              (-> (System/nanoTime) (- nt) (* 0.000001)))))))
  (tt/event! ::logging-initialized {:handlers (tt/get-handlers)}))


(init-logging! (s/project-info) @conf/profile)
