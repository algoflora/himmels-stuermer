(ns himmelsstuermer.e2e.scenes
  {:clj-kondo/config '{:ignore [:unused-private-var]}}
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.test :refer [is testing]]
    [himmelsstuermer.core.logging :refer [throwable->map]]
    [himmelsstuermer.e2e.client :as cl]
    [himmelsstuermer.e2e.dummy :as dum]
    [himmelsstuermer.misc :as misc]
    [himmelsstuermer.spec.blueprint :as spec.bp]
    [himmelsstuermer.spec.core :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [missionary.core :as m]
    [taoensso.telemere :as tt]
    [tick.core :as t]))


(malli/=> str?->re [:-> [:or :string spec/Regexp] spec/Regexp])


(defn- str?->re
  [re?]
  (if (string? re?) (re-pattern (java.util.regex.Pattern/quote re?)) re?))


(defn- dump
  [dummy]
  (let [key   (-> dummy :username keyword)
        dummy (dum/get-by-key key)]
    (println (format "Dumping dummy %s\n%s" key (with-out-str (pprint dummy))))
    (tt/event! ::dumping-dummy {:data {:dummy dummy}})))


(malli/=> get-message [:=> [:cat spec.tg/User [:maybe [:int {:min 1}]]] spec.tg/Message])


(defn- get-message
  [dummy ?num]
  (if (pos-int? ?num)
    (as-> ?num $
          (dum/get-last-messages dummy $ false)
          (reverse $) (vec $) (get $ (dec ?num)))
    (dum/get-first-message dummy)))


(malli/=> send-text [:=> [:cat spec.tg/User spec.bp/SendTextBlueprintEntryArgs] :any])


(defn- send-text
  ([dummy text] (send-text dummy text []))
  ([dummy text entities]
   (tt/event! ::dummy-send-text
              {:data {:dummy dummy
                      :text text}})
   (cl/send-text dummy (str text) entities)))


(malli/=> click-btn [:=> [:cat spec.tg/User spec.bp/ClickBtnBlueprintEntryArgs] :any])


(defn- click-btn
  {:clj-kondo/ignore [:unused-private-var]}
  ([dummy btn-re] (click-btn dummy nil btn-re))
  ([dummy num? btn-re]
   (let [msg (get-message dummy num?)]
     (tt/event! ::dummy-click-btn
                {:data {:dummy dummy
                        :button btn-re
                        :message msg}})
     (cl/click-btn dummy msg (str?->re btn-re)))))


(defmulti -check-message (fn [_ arg] (type arg)))


(defmethod -check-message java.lang.String
  [{:keys [text caption]} exp]
  (cond
    (some? text)    (testing ">>> Checking text...\n\n"    (is (= exp text)))
    (some? caption) (testing ">>> Checking caption...\n\n" (is (= exp caption)))))


(defmethod -check-message java.util.regex.Pattern
  [{:keys [text caption]} exp]
  (cond (some? text)    (testing ">>> Checking text...\n\n"    (is (some? (re-find exp text))))
        (some? caption) (testing ">>> Checking caption...\n\n" (is (some? (re-find exp caption))))))


(defmethod -check-message clojure.lang.PersistentVector
  [msg exp]
  (let [kbd (-> msg :reply_markup :inline_keyboard)]
    (testing ">>> Checking buttons...\n"
      (is (= (count exp) (count kbd)) "Different rows count!")
      (doseq [[row-idx row] (map-indexed vector exp)]
        (is (= (count (get exp row-idx)) (count row))
            (format "Different columns count in row %d" row-idx))
        (doseq [[col-idx data] (map-indexed vector row)]
          (let [re       (str?->re data)
                btn      (get-in kbd [row-idx col-idx])
                re-found (re-find re (:text btn))]
            (testing (format ">>> %s : %s\n\n" re (:text btn))
              (is (some? re-found)))))))))


(defmethod -check-message clojure.lang.PersistentHashSet
  [{:keys [entities caption_entities]} exp]
  (cond (some? entities)         (testing ">>> Checking text entities...\n\n"
                                   (is (= exp (set entities))))
        (some? caption_entities) (testing ">>> Checking caption entities...\n\n"
                                   (is (= exp (set caption_entities))))
        (not-empty exp) (is false "No entities or caption_entities!")))


(malli/=> check-msg [:=> [:cat spec.tg/User spec.bp/CheckMessageBlueprintEntryArgs] :nil])


(defn- check-msg
  [dummy & args]
  (let [[num args] (if (-> args first pos-int?) [(first args) (rest args)] [nil args])
        msg (get-message dummy num)]
    (tt/event! ::check-msg {:data {:message msg
                                   :arguments args}})
    (doseq [arg args]
      (-check-message msg arg))))


(malli/=> check-invoice [:=> [:cat spec.tg/User [:? :int] spec.bp/CheckInvoiceBlueprintEntryArgs] :nil])


(defn- check-invoice
  ([dummy title description currency prices]
   (check-invoice dummy title description currency prices []))
  ([dummy title description currency prices buttons]
   (check-invoice dummy 1 title description currency prices buttons))
  ([dummy num title description currency prices buttons]
   (let [{:keys [invoice] :as msg} (get-message dummy num)]
     (tt/event! ::check-invoice {:data {:message msg
                                        :arguments [title description currency prices buttons]}})
     (testing ">>> Checking invoice exists...\n\n"      (is (some? invoice)))
     (testing ">>> Checking invoice title...\n\n"       (is (= title (:title invoice))))
     (testing ">>> Checking invoice description...\n\n" (is (= description (:description invoice))))
     (testing ">>> Checking invoice currency...\n\n"    (is (= currency (:currency invoice))))
     (testing ">>> Checking invoice prices...\n\n"      (is (= prices (:prices invoice))))
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
    (testing (format ">>> Checking variables: %s of %s (%s) and %s (%s)...\n\n" f k1 v1 k2 v2)
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

                   (str/starts-with? key (:name (misc/project-info))) nil

                   :else (-> key dum/new :dummy))
           symb  (-> blueprint first name)
           func  (resolve (symbol "himmelsstuermer.e2e.scenes" symb))
           args  (->> blueprint rest (take-while #(not (qualified-keyword? %))))]
       (testing (format "> Line:\t\t%4d\n> Dummy:\t%s\n> Action:\t%s\n> Arguments:\t%s\n" line key symb (str/join " " args))
         (tt/event! ::apply-blueprint-function {:data {:dummy dummy
                                                       :function symb
                                                       :argument args}})
         (apply func dummy args))
       (apply-blueprint (drop (inc (count args)) blueprint) (inc line))))))


(defmulti ^:private sub-scene (fn [_ x & _]
                                (cond (fn? x) :function
                                      (vector? x) :vector)))


(defmethod sub-scene :vector
  [_ blueprint]
  ;; TODO: Find out what the hell!
  #_(when-let [error (m/explain spec.bp/Blueprint blueprint)]
    (throw (ex-info "Wrong Blueprint in sub-flow method!"
                    {:event ::wrong-sub-flow-blueprint-error
                     :error-explain error})))
  (apply-blueprint blueprint))


(defmethod sub-scene :function
  [_ f & args]
  (let [blueprint (apply f args)]
    (sub-scene nil blueprint)))


(defn- check-and-close-only-temp
  [dummy & args]
  (let [ns (:username dummy)]
    (sub-scene dummy
               (vec (concat [(keyword ns "check-msg") 1] args
                            [(keyword ns "click-btn") 1 "✖️" (keyword ns "check-no-temp-messages")])))))


(defn- check-and-close-last-temp
  [dummy & args]
  (let [ns (:username dummy)]
    (sub-scene dummy
               (vec (concat [(keyword ns "check-msg") 1] args
                            [(keyword ns "click-btn") 1 "✖️"])))))


(defn- call!
  [_ f & args]
  ;; TODO: Find out the way for database connection in such call
  (apply (requiring-resolve f) args))


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


(defn situation
  [scenes]
  (try
    (doseq [[k bp] scenes]
      (testing (str "Scene:\t" k "\n")
        (t/with-clock *clock*
                      (apply-blueprint bp))))
    (catch Exception ex
      (throw (tt/error! {:id ::situation-error
                         :data (throwable->map ex)} ex)))
    (finally
      (dum/clear-all))))


(defonce scenes (atom {}))


(defn get-scene
  [k]
  (if-let [scene (k @scenes)]
    scene
    (throw (ex-info (format "Scene with key `%s` not found!" k)
                    {:event :scene-not-found-error
                     :key k
                     :available-keys (keys @scenes)}))))


(defmacro defscene
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
    `(swap! scenes assoc ~key ~blueprint)))


(defmacro situation-run
  {:style/indent [1]
   :clj-kondo/lint-as 'clojure.core/def}
  [name & args]
  (let [[h arg] (if (= 2 (count args)) [(first args) (second args)] [nil (first  args)])]
    `(do
       (clojure.test/deftest ~name
         (let [~'a-clock (t/atom)
               ~'scenes (mapv #(cond
                                 (qualified-keyword? %) [% (get-scene %)]
                                 (vector? %)            [:inline %])
                              ~arg)]
           (System/setProperty "himmelsstuermer.test.connection-suffix" (str (random-uuid)))
           (with-redefs [himmelsstuermer.core.init/handler-main
                         ~(if (some? h) `(fn [] (m/sp ~h)) `himmelsstuermer.core.init/handler-main)]
             (binding [*clock* ~'a-clock]
               (situation ~'scenes)))
           (System/clearProperty "himmelsstuermer.test.connection-suffix"))))))
