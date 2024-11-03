(ns himmelsstuermer.aws.common)


(defn stream-to-out
  [input-stream]
  (let [reader (java.io.BufferedReader. (java.io.InputStreamReader. input-stream))]
    (future
      (loop []
        (when-let [line (.readLine reader)]
          (println line)
          (recur))))))
