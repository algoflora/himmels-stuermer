(ns himmelsstuermer.misc
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [missionary.core :as m]))


(defn dbg
  [x]
  (println "DBG\t" x) x)


(defn- project-name
  []
  (-> (System/getProperty "user.dir")  ; Get the current directory path
      io/file                          ; Convert it to a File object
      .getName))


(defmacro project-info

  "This macro expands in map with keys `group`, `name` and `version` of current project by information from project.clj"

  []
  {:name (project-name)})


(defn caller-map-fn
  [this-ns]
  (fn [^java.lang.StackTraceElement element]
    (let [namespace (-> element .getClassName (str/split #"\$") first)
          file-name (.getFileName element)
          line      (.getLineNumber element)]
      (when (and (str/starts-with? namespace "himmelsstuermer")
                 (not (str/starts-with? namespace this-ns)))
        {:namespace namespace
         :file-name file-name
         :line line}))))


(defmacro get-caller
  []
  `(->> (Thread/currentThread)
        .getStackTrace
        (map (caller-map-fn ~(str *ns*)))
        (filter some?)
        first))


(defmulti remove-nils (fn [x]
                        (cond
                          (record? x) :default
                          (map? x)    :map
                          (vector? x) :vec
                          :else       :defalut)))


(defmethod remove-nils :map
  [m]
  (into {} (map (fn [[k v]]
                  (if (nil? v) nil [k (remove-nils v)])) m)))


(defmethod remove-nils :vec
  [v]
  (filterv some? (map #(if (some? %) (remove-nils %) nil) v)))


(defmethod remove-nils :default
  [x]
  (identity x))


(defmacro do-nanos
  [& body]
  `(let [~'t0 (System/nanoTime)]
     ~@body
     (- (System/nanoTime) ~'t0)))


(defmacro do-nanos*
  [& body]
  `(let [~'t0 (System/nanoTime)
         ~'r (do ~@body)]
     {:result ~'r
      :nanos (- (System/nanoTime) ~'t0)}))


(defn- char-range
  [lo hi]
  (range (int lo) (inc (int hi))))


(def ^:private hex
  (map char (concat (char-range \a \f)
                    (char-range \0 \9))))


(def ^:private alpha-numeric
  (map char (concat (char-range \a \z)
                    (char-range \A \Z)
                    (char-range \0 \9))))


(defn- create-generator
  [chars]
  (fn [num]
    (apply str (take num (repeatedly #(rand-nth chars))))))


(defn generate-hex
  [num]
  (m/sp ((create-generator hex) num)))


(defn generate-alpha-numeric
  [num]
  (m/sp ((create-generator alpha-numeric) num)))


(defn user->str
  [user]
  (let [username (:user/username user)]
    (if username
      (str "@" username)
      (str "id" (:user/id user)))))
