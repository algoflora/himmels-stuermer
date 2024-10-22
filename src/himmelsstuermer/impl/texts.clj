(ns himmelsstuermer.impl.texts
  (:require
    [himmelsstuermer.impl.state :refer [*state*]]
    [himmelsstuermer.misc :refer [read-resource-dir]]
    [missionary.core :as m]))


(def ^:private texts
  (into {} (->> (m/? (read-resource-dir "texts")) ; TODO: Think about wrapping all this to tasks
                (map #(into {} [%]))
                (apply merge))))


(defmulti txti (fn [_ path & _] (seqable? path)))


(defmethod txti false
  [lang path & args]
  (apply txti lang (vector path) args))


(defmethod txti true
  [lang path & args]
  (let [default-lang (-> *state* :bot :default-language-code)
        [path# form] (if (-> path last int?)
                       [(->> path (drop-last 1) vec) (last path)]
                       [(vec path) 0])
        lang-map (get-in texts path#)]
    (when (not (map? lang-map))
      (throw (ex-info "Not a map in texts by given path!"
                      {:event ::no-text-map-on-path :path path :lang-map lang-map})))
    (apply format
           (let [forms (or (and (some? lang) ((keyword lang) lang-map))
                           (default-lang lang-map)
                           (-> lang-map first val))]
             (if (vector? forms)
               (nth forms (min form (-> forms count dec)))
               forms))
           args)))
