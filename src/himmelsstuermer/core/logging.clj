(ns himmelsstuermer.core.logging
  (:require
    [cheshire.core :as json]
    [cheshire.generate :as gen]
    [me.raynes.fs :as fs]
    [taoensso.telemere :as tt]))


(gen/add-encoder Object
                 (fn [obj json-generator]
                   ;; Custom logic for serializing unknown objects.
                   ;; As an example, we'll just serialize the object as a string.
                   (.writeString json-generator (str obj))))


(def ^:private console-json-handler
  (tt/handler:console {:output-fn (tt/pr-signal-fn {:pr-fn json/generate-string})}))


(def ^:private file-edn-disposable-handler
  (tt/handler:file {:output-fn (tt/pr-signal-fn {:pr-fn :edn})
                    :path "./logs.edn"}))


(defn init-logging!
  [project-info profile]
  (tt/set-ctx! {:project project-info :profile profile})
  (tt/add-handler! :console-json console-json-handler)
  (fs/delete "./logs.edn")
  (tt/add-handler! :file-edn-disposable file-edn-disposable-handler))
