(ns himmelsstuermer.blambda.api
  (:require
    [babashka.deps :refer [clojure]]
    [babashka.fs :as fs]
    [babashka.http-client :as http]
    [babashka.pods :as pods]
    [babashka.process :refer [shell]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [himmelsstuermer.blambda.internal :as lib]))


(defn fetch-pods
  [{:keys [bb-arch source-dir work-dir] :as opts} pods]
  (let [home-dir (System/getProperty "user.home")
        os-name (System/getProperty "os.name")
        os-arch (System/getProperty "os.arch")]
    (try
      (System/setProperty "user.home" work-dir)
      (System/setProperty "os.name" "Linux")
      (System/setProperty "os.arch" (if (= bb-arch "arm64") "aarch64" "amd64"))
      (doseq [[pod {:keys [path version]}] pods]
        (if path
          (let [src-path (fs/file source-dir path)
                tgt-path (fs/file work-dir path)]
            (fs/copy src-path tgt-path))
          (pods/load-pod pod version)))
      (finally
        (System/setProperty "user.home" home-dir)
        (System/setProperty "os.name" os-name)
        (System/setProperty "os.arch" os-arch)))))


(defn build-deps-layer
  "Builds layer for dependencies"
  [{:keys [deps-path target-dir work-dir] :as opts}]
  (let [deps-zipfile (lib/deps-zipfile opts)
        rebuild-deps? (and (coll? *command-line-args*)
                           (some #{"--rebuild-deps"} *command-line-args*))]
    (if (and (empty? (fs/modified-since deps-zipfile deps-path))
             (not rebuild-deps?))
      (println (format "\nNot rebuilding dependencies layer: no changes to %s since %s was last built"
                       (str deps-path) (str deps-zipfile)))
      (do
        (println "\nBuilding dependencies layer:" (str deps-zipfile))
        (doseq [dir [target-dir work-dir]]
          (fs/create-dirs dir))
        (let [gitlibs-dir "gitlibs"
              m2-dir "m2-repo"
              pods-dir ".babashka"
              {:keys [deps pods]} (-> deps-path slurp edn/read-string)
              local-repo (str (System/getProperty "user.home") "/.m2/repository")]
          (if (and (empty? deps) (empty? pods))
            (println (format "\nNot building dependencies layer: no deps or pods listed in %s"
                             (str deps-path)))
            (do
              (when rebuild-deps?
                (fs/delete-tree (fs/file work-dir m2-dir)))
              (spit (fs/file work-dir "deps.edn")
                    {:deps (or deps {})
                     :pods (or pods {})
                     :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                 "local" {:url (str "file://" local-repo)}}
                     :mvn/local-repo (str m2-dir)})

              (let [classpath-file (fs/file work-dir "deps-classpath")
                    local-classpath-file (fs/file work-dir "deps-local-classpath")
                    deps-base-dir (str (fs/path (fs/cwd) work-dir))
                    classpath
                    (str "resources:"
                         (with-out-str
                           (clojure ["-Spath"]
                                    {:dir work-dir
                                     :env (assoc (into {} (System/getenv))
                                                 "GITLIBS" (str gitlibs-dir))})))
                    deps-classpath (str/replace classpath deps-base-dir "/opt")]
                (println "Classpath before transforming:" classpath)
                (println "Classpath after transforming:" deps-classpath)
                (spit classpath-file deps-classpath)
                (spit local-classpath-file classpath)

                (when pods
                  (fetch-pods opts pods))

                (println "Compressing dependencies layer:" (str deps-zipfile))
                (let [paths (concat [(fs/file-name gitlibs-dir)
                                     (fs/file-name m2-dir)
                                     (fs/file-name pods-dir)
                                     (fs/file-name classpath-file)]
                                    (->> pods
                                         (map (fn [[_ {:keys [path]}]] path))
                                         (remove nil?)))]
                  (apply shell {:dir work-dir}
                         "zip -r" deps-zipfile
                         paths))))))
        (println "Finished building deps layer!")))))


(defn build-runtime-layer
  "Builds custom runtime layer"
  [{:keys [bb-arch bb-version target-dir work-dir]
    :as opts}]
  (let [runtime-zipfile (lib/runtime-zipfile opts)
        bb-filename (lib/bb-filename bb-version bb-arch)
        bb-url (lib/bb-url bb-version bb-filename)
        bb-tarball (format "%s/%s" work-dir bb-filename)]
    (if (and (fs/exists? bb-tarball)
             (empty? (fs/modified-since runtime-zipfile bb-tarball)))
      (println "\nNot rebuilding custom runtime layer; no changes to bb version or arch since last built")
      (do
        (println "\nBuilding custom runtime layer:" (str runtime-zipfile))
        (doseq [dir [target-dir work-dir]]
          (fs/create-dirs dir))

        (when-not (fs/exists? bb-tarball)
          (println "Downloading" bb-url)
          (io/copy
            (:body (http/get bb-url {:as :stream}))
            (io/file bb-tarball)))

        (println "Decompressing" bb-tarball "to" work-dir)
        (shell "tar -C" work-dir "-xzf" bb-tarball)

        (lib/copy-files! (assoc opts :resource? true)
                         ["bootstrap" "bootstrap.clj"])

        (println "Compressing custom runtime layer:" (str runtime-zipfile))
        (shell {:dir work-dir}
               "zip" runtime-zipfile
               "bb" "bootstrap" "bootstrap.clj")))))


(defn build-lambda
  [{:keys [lambda-name source-dir source-files
           target-dir work-dir] :as opts}]
  (when (empty? source-files)
    (throw (ex-info "Missing source-files"
                    {:type :blambda/error})))
  (let [lambda-zipfile (lib/zipfile opts lambda-name)]
    (if (empty? (fs/modified-since lambda-zipfile
                                   (->> source-files
                                        (map (partial fs/file source-dir))
                                        (cons "bb.edn"))))
      (println "\nNot rebuilding lambda artifact; no changes to source files since last built:"
               source-files)
      (do
        (println "\nBuilding lambda artifact:" (str lambda-zipfile))
        (doseq [dir [target-dir work-dir]]
          (fs/create-dirs dir))
        (lib/copy-files! opts source-files)
        (println "Compressing lambda:" (str lambda-zipfile))
        (apply shell {:dir work-dir}
               "zip" lambda-zipfile source-files)))))


(defn build-all
  [opts]
  (build-runtime-layer opts)
  (build-deps-layer opts)
  (build-lambda opts))


(defn clean
  "Deletes target and work directories"
  [{:keys [target-dir work-dir]}]
  (doseq [dir [target-dir work-dir]]
    (println "Removing directory:" dir)
    (fs/delete-tree dir)))
