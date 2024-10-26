(ns himmelsstuermer.api.texts
  (:require
    [himmelsstuermer.impl.texts :as impl]))


(defn txti

  "Gets string from .EDN files in resource folder 'texts' by `path` key if `path` is a keyword or by nested `path` if `path` is a sequence and `language-code`. If there no such language key in map on `path` then config field `:bot/default-language-code` would be used. The resulting string is formatted using `args` and `clojure.core/format` function."

  {:added "0.1.0"
   :see-also "txt"}

  [state language-code path & args]
  (apply impl/txti state language-code path args))


(defn txt

  "Gets string from .EDN files in resource folder 'texts' by `path` key if `path` is a keyword or by nested `path` if `path` is a sequence and `:language-code` field of `usr` dynamic variable. If there no such language key in map on `path` then config field `:bot/default-language-code` would be used. The resulting string is formatted using `args` and `clojure.core/format` function."

  {:added "0.1.0"
   :see-also "txti"}

  [state path & args]
  (apply txti state (-> state :user :language-code) path args))
