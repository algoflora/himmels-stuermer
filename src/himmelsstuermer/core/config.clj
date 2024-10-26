(ns himmelsstuermer.core.config
  (:require
    [aero.core :refer [read-config reader]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [himmelsstuermer.spec.app :as spec.app]
    [malli.core :as malli]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(def profile
  (delay (some-> (or (System/getProperty "himmelsstuermer.profile")
                     (System/getenv "HIMMELSSTUERMER_PROFILE"))
                 str/lower-case
                 keyword)))


(defmethod reader 'prop
  [_ _ value]
  (System/getProperty (name value)))


(def himmelsstuermer-config
  (m/sp
    (let [profile @profile
          cfg (read-config (io/resource "himmelsstuermer-resources/config.edn")
                           {:profile profile})]
      (tt/event! ::himmelsstuermer-config-file-loaded {:data {:profile profile
                                                              :config cfg}})
      cfg)))


(def project-config
  (m/sp
    (let [profile @profile
          cfg (read-config (io/resource "config.edn")
                           {:profile profile})]
      (tt/event! ::project-config-file-loaded {:data {:profile profile
                                                      :config cfg}})
      cfg)))


(malli/=> merge-configs [:=>
                         [:cat spec.app/HimmelsstuermerConfig spec.app/ProjectConfig]
                         spec.app/Config])


(defn- merge-configs
  [hh-cfg cfg]
  (merge hh-cfg cfg))


(def config
  (m/memo
    (m/via m/blk
           (let [cfg (m/? (m/join merge-configs himmelsstuermer-config project-config))]
             (tt/event! ::config-loaded {:data {:config cfg}})
             cfg))))
