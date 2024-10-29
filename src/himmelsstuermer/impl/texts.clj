(ns himmelsstuermer.impl.texts
  (:require
    [himmelsstuermer.misc :refer [read-resource-dir]]
    [missionary.core :as m]))


(def ^:private texts
  (delay (into {} (->> (m/? (read-resource-dir "texts")) ; TODO: Think about wrapping all this to tasks
                       (map #(into {} [%]))
                       (apply merge))))) ; TODO: Think about moving this to state


(defmulti txti (fn [_ path & _] (seqable? path)))


(defmethod txti false
  [state lang path & args]
  (apply txti state lang (vector path) args))


(defmethod txti true
  [state lang path & args]
  (let [default-lang (-> state :bot :default-language-code)
        path' (if (sequential? path) path [path])
        [path# form] (if (-> path' last int?)
                       [(->> path' (drop-last 1) vec) (last path')]
                       [(vec path') 0])
        lang-map (get-in @texts path#)]
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
