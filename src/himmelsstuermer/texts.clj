(ns himmelsstuermer.texts
  (:require
    [himmelsstuermer.dynamic :refer [*user*]]
    [himmelsstuermer.impl.texts :as impl]))


(defn txti

  "Gets string from .EDN files in resource folder 'texts' by `path` key if `path` is a keyword or by nested `path` if `path` is a sequence and `language-code`. If there no such language key in map on `path` then config field `:bot/default-language-code` would be used. The resulting string is formatted using `args` and `clojure.core/format` function."

  {:added "0.1.0"
   :see-also "txt"}

  [language-code path & args]
  (apply impl/txti language-code path args))


(defn txt

  "Gets string from .EDN files in resource folder 'texts' by `path` key if `path` is a keyword or by nested `path` if `path` is a sequence and `:language-code` field of `*user*` dynamic variable. If there no such language key in map on `path` then config field `:bot/default-language-code` would be used. The resulting string is formatted using `args` and `clojure.core/format` function."

  {:added "0.1.0"
   :see-also "txti"}

  [path & args]
  (apply txti (:user/language-code *user*) path args))
