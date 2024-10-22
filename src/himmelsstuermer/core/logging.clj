(ns himmelsstuermer.core.logging
  (:require
    [cheshire.core :as json]
    [cheshire.generate :as gen]
    [me.raynes.fs :as fs]
    [taoensso.telemere :as tt]))


(defonce ^:private nano-timer nil)


(defn reset-nano-timer!
  []
  (reset! nano-timer (System/nanoTime)))


(gen/add-encoder Object
                 (fn [obj ^com.fasterxml.jackson.core.JsonGenerator json-generator]
                   (.writeString json-generator (str obj))))


(def ^:private console-json-handler
  (tt/handler:console {:output-fn (tt/pr-signal-fn {:pr-fn json/generate-string})}))


(def ^:private file-edn-disposable-handler
  (tt/handler:file {:output-fn (tt/pr-signal-fn {:pr-fn :edn})
                    :path "./logs.edn"}))


(defn init-logging!
  [project-info profile]
  (tt/set-ctx! {:project project-info :profile profile})
  (tt/set-middleware! #(assoc % :millis-passed (-> (System/nanoTime) (- @nano-timer) (* 0.000001))))
  (tt/add-handler! :console-json console-json-handler)
  (fs/delete "./logs.edn")
  (tt/add-handler! :file-edn-disposable file-edn-disposable-handler)
  (Thread/sleep 1000)
  (tt/event! ::test-event))
