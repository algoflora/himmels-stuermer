(ns himmelsstuermer.api.buttons)


(defn add-ns
  [ns sym]
  (if (qualified-symbol? sym) sym
      (symbol (-> ns ns-name name) (name sym))))


(defprotocol KeyboardButton

  (to-map [this state user]))


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
