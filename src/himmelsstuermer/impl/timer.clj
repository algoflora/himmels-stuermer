(ns himmelsstuermer.impl.timer)

(defonce ^:private timer (atom (System/nanoTime)))


(defn reset-timer!
  []
  (reset! timer (System/nanoTime)))


(defn millis-passed
  []
  (* 0.000001 (- (System/nanoTime) @timer)))
