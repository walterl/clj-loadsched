(ns loadsched.util
  (:require [clojure.string :as string]))

(defn -debug
  "Prints args and returns last one."
  [& xs]
  (apply println xs)
  (last xs))

(defn time-hour-to-int
  "Convert the hour part of a time string (e.g. \"11:00\") to an int."
  [t]
  (-> t (string/split #":") first Integer/parseUnsignedInt))
