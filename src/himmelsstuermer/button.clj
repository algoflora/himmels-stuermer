(ns himmelsstuermer.button
  (:require
    [himmelsstuermer.impl.callbacks :as clb]
    [himmelsstuermer.impl.system.app :as app]
    [himmelsstuermer.texts :refer [txt txti]]))


(defn add-ns
  [ns sym]
  (if (qualified-symbol? sym) sym
      (symbol (-> ns ns-name name) (name sym))))


(defprotocol KeyboardButton

  (to-map [this user]))


(defrecord TextButton
  [text func args])


(defmacro text-btn
  ([text func]
   `(text-btn ~text ~func {}))
  ([text func args]
   `(->TextButton ~text (add-ns ~*ns* ~func) ~args)))


(defrecord TxtButton
  [txt func args])


(defmacro txt-btn
  ([txt func]
   `(txt-btn ~txt ~func {}))
  ([txt func args]
   `(->TxtButton ~txt (add-ns ~*ns* ~func) ~args)))


(defrecord TxtiButton
  [txt lang func args])


(defmacro txti-btn
  ([txt lang func]
   `(txti-btn ~txt ~lang ~func {}))
  ([txt lang func args]
   `(->TxtiButton ~txt ~lang (add-ns ~*ns* ~func) ~args)))


(defrecord HomeButton
  [text])


(defn home-btn
  ([] (home-btn nil))
  ([text]
   (->HomeButton text)))


(defrecord PayButton
  [text])


(defn pay-btn
  [text]
  (->PayButton text))


(defrecord UrlButton
  [text url])


(defn url-btn
  [text url]
  (->UrlButton text url))


(defrecord XButton
  [])


(extend-protocol KeyboardButton
  clojure.lang.PersistentArrayMap
  (to-map [this _] this)

  clojure.lang.PersistentHashMap
  (to-map [this _] this)

  TextButton
  (to-map [this user]
    {:text (:text this)
     :callback_data
     (str (clb/set-callback user (:func this) (:args this)))})

  TxtButton
  (to-map [this user]
    {:text (txt (:txt this))
     :callback_data
     (str (clb/set-callback user (:func this) (:args this)))})

  TxtiButton
  (to-map [this user]
    {:text (txti (:txt this) (:lang this))
     :callback_data
     (str (clb/set-callback user (:func this) (:args this)))})

  HomeButton
  (to-map [this user]
    (let [text (cond
                 (nil? (:text this))    (txt [:home])
                 (vector? (:text this)) (txt (:text this))
                 :else                  (:text this))]
      {:text text
       :callback_data
       (str (clb/set-callback user (symbol (app/handler-main)) {}))}))

  PayButton
  (to-map [this _]
    {:text (:text this)
     :pay true})

  UrlButton
  (to-map [this _]
    {:text (:text this)
     :url (:url this)})

  XButton
  (to-map [_ user]
    {:text "✖️"
     :callback_data
     (str (clb/set-callback user 'himmelsstuermer.handler/delete-this-message {} true))}))
