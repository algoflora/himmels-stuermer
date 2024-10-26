(ns himmelsstuermer.impl.buttons
  (:require
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.api.texts :refer [txt txti]]
    [himmelsstuermer.impl.callbacks :as clb]))


(defrecord XButton
  [])


(extend-protocol b/KeyboardButton
  clojure.lang.PersistentArrayMap
  (to-map [this _ _] this)

  clojure.lang.PersistentHashMap
  (to-map [this _ _] this)

  himmelsstuermer.api.buttons.TextButton
  (to-map [this state user]
    {:text (:text this)
     :callback_data
     (str (clb/set-callback state user (:func this) (:args this)))})

  himmelsstuermer.api.buttons.TxtButton
  (to-map [this state user]
    {:text (txt state (:txt this))
     :callback_data
     (str (clb/set-callback state user (:func this) (:args this)))})

  himmelsstuermer.api.buttons.TxtiButton
  (to-map [this state user]
    {:text (txti state (:txt this) (:lang this))
     :callback_data
     (str (clb/set-callback state user (:func this) (:args this)))})

  himmelsstuermer.api.buttons.HomeButton
  (to-map [this state user]
    (let [text (cond
                 (nil? (:text this))    (txt state user [:home])
                 (vector? (:text this)) (txt state user (:text this))
                 :else                  (:text this))]
      {:text text
       :callback_data
       (str (clb/set-callback state user (:himmelsstuermer/main-handler state) {}))}))

  himmelsstuermer.api.buttons.PayButton
  (to-map [this _ _]
    {:text (:text this)
     :pay true})

  himmelsstuermer.api.buttons.UrlButton
  (to-map [this _ _]
    {:text (:text this)
     :url (:url this)})

  XButton
  (to-map [_ state user]
    {:text "✖️"
     :callback_data
     (str (clb/set-callback state user 'himmelsstuermer.handler/delete-this-message {} true))}))
