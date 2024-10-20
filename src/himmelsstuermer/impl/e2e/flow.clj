 (ns himmelsstuermer.impl.e2e.flow
   (:require
     [clojure.string :as str]
     [clojure.test :refer [is testing]]
     [himmelsstuermer.dynamic :refer [*dtlv*]]
     [himmelsstuermer.impl.e2e.client :as cl]
     [himmelsstuermer.impl.e2e.dummy :as dum]
     [himmelsstuermer.impl.errors :refer [handle-error]]
     [himmelsstuermer.impl.system :as sys]
     [himmelsstuermer.impl.system.app :as app]
     [himmelsstuermer.logging]
     [himmelsstuermer.spec.blueprint :as spec.bp]
     [himmelsstuermer.spec.commons :refer [Regexp]]
     [himmelsstuermer.spec.telegram :as spec.tg]
     [malli.core :as m]
     [taoensso.timbre :as log]
     [tick.core :as t]))


(m/=> str?->re [:-> [:or :string Regexp] Regexp])


(defn- str?->re
  [re?]
  (if (string? re?) (re-pattern (java.util.regex.Pattern/quote re?)) re?))


(defn- dump
  [dummy]
  (let [key   (-> dummy :username keyword)
        dummy (dum/get-by-key key)]
    (log/info ::dumping-dummy
              "Dumping dummy %s\n%s" key (with-out-str (clojure.pprint/pprint dummy))
              {:dummy dummy})))


(m/=> get-message [:=> [:cat spec.tg/User [:maybe [:int {:min 1}]]] spec.tg/Message])


(defn- get-message
  [dummy ?num]
  (if (pos-int? ?num)
    (as-> ?num $
          (dum/get-last-messages dummy $ false)
          (reverse $) (vec $) (get $ (dec ?num)))
    (dum/get-first-message dummy)))


(m/=> send-text [:=> [:cat spec.tg/User spec.bp/SendTextBlueprintEntryArgs] :any])


(defn- send-text
  ([dummy text] (send-text dummy text []))
  ([dummy text entities]
   (log/debug ::dummy-send-text
              "Dummy %s sendindg message: %s" (-> dummy :username keyword) text
              {:dummy dummy
               :text text})
   (log/debug ::dummy-sent-text {:response (cl/send-text dummy (str text) entities)})))


(m/=> click-btn [:=> [:cat spec.tg/User spec.bp/ClickBtnBlueprintEntryArgs] :any])


(defn- click-btn
  ([dummy btn-re] (click-btn dummy nil btn-re))
  ([dummy num? btn-re]
   (let [msg (get-message dummy num?)]
     (log/debug ::dummy-click-btn
                "Dummy %s clicking button '%s' in message '%s'"
                (-> dummy :username keyword) (str btn-re) (or (:text msg) (:caption msg))
                {:dummy dummy
                 :button btn-re
                 :message msg})
     (cl/click-btn dummy msg (str?->re btn-re)))))


(defmulti -check-message (fn [_ arg] (type arg)))


(defmethod -check-message java.lang.String
  [{:keys [text caption] :as msg} exp]
  (testing "text or caption"
    (cond
      (and (some? text) (nil? caption)) (is (= exp text))
      (and (some? caption) (nil? text)) (is (= exp caption))
      :else (throw (ex-info ":text and :caption in same Message!"
                            {:event ::text-and-caption-both-error
                             :message msg})))))


(defmethod -check-message java.util.regex.Pattern
  [{:keys [text caption] :as msg} exp]
  (testing (str "text or caption regex of " msg)
    (is (or (and (some? text) (some? (re-find exp text)))
            (and (some? caption) (some? (re-find exp caption)))))))


(defmethod -check-message clojure.lang.PersistentVector
  [msg exp]
  (let [kbd (-> msg :reply_markup :inline_keyboard)]
    (testing (str "buttons: " exp "in " kbd "\n")
      (is (= (count exp) (count kbd)) "Different rows count!")
      (doseq [[row-idx row] (map-indexed vector exp)]
        (is (= (count (get exp row-idx)) (count row))
            (format "Different columns count in row %d" row-idx))
        (doseq [[col-idx data] (map-indexed vector row)]
          (let [re       (str?->re data)
                btn      (get-in kbd [row-idx col-idx])
                re-found (re-find re (:text btn))]
            (testing (format "%s =|= %s" re (:text btn))
              (is (some? re-found)))))))))


(defmethod -check-message clojure.lang.PersistentHashSet
  [msg exp]
  (testing (format "text or caption entities %s %s" (:entities msg) exp)
    (is (or (= exp (set (:entities msg)))
            (= exp (set (:caption_entities msg)))))))


(m/=> check-msg [:=> [:cat spec.tg/User spec.bp/CheckMessageBlueprintEntryArgs] :nil])


(defn- check-msg
  [dummy & args]
  (let [[num args] (if (-> args first pos-int?) [(first args) (rest args)] [nil args])
        msg (get-message dummy num)]
    (testing "check-message"
      (doseq [arg args]
        (-check-message msg arg)))))


(m/=> check-invoice [:=> [:cat spec.tg/User [:? :int] spec.bp/CheckInvoiceBlueprintEntryArgs] :nil])


(defn- check-invoice
  ([dummy title description currency prices]
   (check-invoice dummy title description currency prices []))
  ([dummy title description currency prices buttons]
   (check-invoice dummy 1 title description currency prices buttons))
  ([dummy num title description currency prices buttons]
   (let [{:keys [invoice] :as msg} (get-message dummy num)]
     (is (some? invoice))
     (is (= title (:title invoice)))
     (is (= description (:description invoice)))
     (is (= currency (:currency invoice)))
     (is (= prices (:prices invoice)))
     (-check-message msg buttons))))


(defn- pay-invoice
  ([dummy] (pay-invoice dummy 1))
  ([dummy num]
   (let [{:keys [invoice]} (get-message dummy num)]
     (is (some? invoice))
     (is (map? invoice))
     (cl/send-pre-checkout-query dummy invoice)
     (click-btn dummy 1 "✖️"))))


(defn- approve-pre-checkout-query
  [dummy]
  (cl/send-successful-payment dummy))


(defonce vars (atom {}))


(defn- save-var-from-message
  [dummy & args]
  (let [[num args] (if (-> args first pos-int?) [(first args) (rest args)] [nil args])
        msg (get-message dummy num)]
    (swap! vars assoc (first args) (cond->> msg
                                     (contains? msg :caption) :caption
                                     (contains? msg :text) :text
                                     true (re-find (second args))
                                     true second))))


(defn- check-vars
  [_ f k1 k2]
  (let [v1 (k1 @vars)
        v2 (k2 @vars)]
    (testing (format "%s of %s (%s) and %s (%s)" f k1 v1 k2 v2)
      (is (f v1 v2)))))


(defn- check-no-temp-messages
  [dummy]
  (let [m-msg (get-message dummy nil)
        t-msg (get-message dummy 1)]
    (is (= m-msg t-msg))))


;; TODO: Find out what the hell!
;; (m/=> apply-blueprint [:-> spec.bp/Blueprint :nil])


(defn- apply-blueprint
  ([blueprint] (apply-blueprint blueprint 1))
  ([blueprint line]
   (when (not-empty blueprint)
     (let [key   (-> blueprint first namespace keyword)
           dummy (cond
                   (dum/exists? key) (-> key dum/get-by-key :dummy)

                   (= (-> key name (str/split #"\.") first)
                      (-> (app/handler-main) namespace (str/split #"\.") first))
                   nil

                   :else (-> key dum/new :dummy))
           symb  (-> blueprint first name)
           func  (resolve (symbol "himmelsstuermer.impl.e2e.flow" symb))
           args  (->> blueprint rest (take-while #(not (qualified-keyword? %))))]
       (testing (format "%4d | <%s/%s %s>\n" line key symb (str/join " " args))
         (apply func dummy args))
       (apply-blueprint (drop (+ 1 (count args)) blueprint) (inc line))))))


(defmulti ^:private sub-flow (fn [_ x & _] (cond (fn? x) :function (vector? x) :vector)))


(defmethod sub-flow :vector
  [_ blueprint]
  ;; TODO: Find out what the hell!
  #_(when-let [error (m/explain spec.bp/Blueprint blueprint)]
    (throw (ex-info "Wrong Blueprint in sub-flow method!"
                    {:event ::wrong-sub-flow-blueprint-error
                     :error-explain error})))
  (apply-blueprint blueprint))


(defmethod sub-flow :function
  [_ f & args]
  (let [blueprint (apply f args)]
    (sub-flow nil blueprint)))


(defn- check-and-close-only-temp
  [dummy & args]
  (let [ns (:username dummy)]
    (sub-flow dummy
              (vec (concat [(keyword ns "check-msg") 1] args
                           [(keyword ns "click-btn") 1 "✖️" (keyword ns "check-no-temp-messages")])))))


(defn- check-and-close-last-temp
  [dummy & args]
  (let [ns (:username dummy)]
    (sub-flow dummy
              (vec (concat [(keyword ns "check-msg") 1] args
                           [(keyword ns "click-btn") 1 "✖️"])))))


(defn- call!
  [_ f & args]
  (binding [*dtlv* (app/db-conn)]
    (apply (requiring-resolve f) args)))


(defn- action!
  [_ k argm]
  (cl/call-action k argm))


(defn- println!
  [_ text]
  (println text))


(def ^:dynamic *clock* nil)


(defn- swap-clock
  [_ & args]
  (apply t/swap! *clock* args))


(defn- set-clock
  [_ clock]
  (t/reset! *clock* (cond
                      (t/clock? clock) clock
                      (t/instant? clock) (t/clock clock)
                      (string? clock) (t/clock (t/instant clock)))))


(defn- reset-clock
  [_]
  (t/reset! *clock* (t/clock)))


(defn flow
  [blueprints]
  (try
    (sys/startup!)
    (doseq [[k bp] blueprints]
      (testing (str k)
        (t/with-clock *clock*
                      (apply-blueprint bp))))
    (catch Exception ex
      (handle-error ex)
      (throw ex))
    (finally
      (dum/clear-all)
      (sys/shutdown!))))


(defonce flows (atom {}))


(defn get-flow
  [key]
  (if-let [flow (key @flows)]
    flow
    (throw (ex-info (format "Flow with key `%s` not found!" key)
                    {:event :flow-not-found-error
                     :key key
                     :available-keys (keys @flows)}))))


(defmacro defflow
  [key blueprint]
  {:style/indent [1]}
  (let [key (if (qualified-keyword? key) key
                (keyword (as-> *ns* $
                               (ns-name $)
                               (name $)
                               (str/split $ #"\.")
                               (drop-while #(not= "test-flows" %) $)
                               (rest $)
                               (str/join "." $))
                         (name key)))]
    `(swap! flows assoc ~key ~blueprint)))


(defmacro flows-out
  {:style/indent [1]
   :clj-kondo/lint-as 'clojure.core/def}
  [name & args]
  (let [[h arg] (if (= 2 (count args)) [(first args) (second args)] [nil (first  args)])]
    `(do
       (clojure.test/deftest ~name
         (let [~'a-clock (t/atom)
               ~'blueprints (mapv #(cond
                                     (keyword? %) [% (get-flow %)]
                                     (vector?  %) [:inline %])
                                  ~arg)]
           (with-redefs [himmelsstuermer.impl.system.app/handler-main
                         (if (some? ~h) (fn [] ~h) himmelsstuermer.impl.system.app/handler-main)]
             (binding [*clock* ~'a-clock]
               (flow ~'blueprints))))))))
