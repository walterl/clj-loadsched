(ns loadsched.load
  (:require [clojure.set :refer [union]]
            [clojure.string :as string]
            [clojure.pprint :as pprint]
            [loadsched.util :as util]))

(defn split-line
  "Split the given line on pipe (|) characters."
  [line]
  (filter
    (comp not empty?)
    (string/split line #"\s*\|\s*")))

(defn load-lines
  "Returns vector field vectors from specified file. See `split-line`."
  [filename]
  (map split-line (string/split (slurp filename) #"\n")))

(defn stage-to-num
  "Converts string `StageN` to integer N."
  [stage-str]
  (Integer/parseUnsignedInt
    (string/replace stage-str "Stage" "")))

(defn map-day-groups
  "Maps seq of (string) group numbers, to the day of the month."
  [groups]
  (->> (map #(Integer/parseUnsignedInt %) groups)
       (interleave (range 1 (inc (count groups))))
       (apply sorted-map)
       ))

(defn parse-fields
  "Parses lines into timespan or stage/day group quasi-records."
  [fields]
  (cond
    (= (count fields) 2)
    {:start-time (first fields) :end-time (second fields)}

    (= (count fields) 32)
    {:stage (stage-to-num (first fields))
     :day-groups (map-day-groups (rest fields))}
    ))

(defn append-records
  [coll [day-of-month group]]
  (update-in
    coll [:records]
    #(conj (or % []) {:day-of-month day-of-month
                      :stage (:stage coll)
                      :start-time (:start-time coll)
                      :end-time (:end-time coll)
                      :group group})))

(defn denormalize-line
  [results line]
  (let [{:keys [records start-time end-time]} results
        line-start-time (:start-time line)
        line-end-time (:end-time line)]
    (if (and line-start-time line-end-time)
      {:records records :start-time line-start-time :end-time line-end-time}

      (let [initial-rec {:stage (:stage line)
                         :start-time start-time
                         :end-time end-time}]
        (->> (:day-groups line)
             (reduce append-records initial-rec)
             :records
             (concat records)
             (assoc {} :records)
             (into results)
             )))))

(defn collect-records
  "Turns a sequence of parsed lines (`parse-fields`) into a sequence of complete schedule records."
  [parsed-lines]
  (->> parsed-lines
       (reduce denormalize-line {:records [] :start-time nil :end-time nil})
       :records
       (sort-by (juxt :day-of-month :stage (comp util/time-hour-to-int :start-time)))
       ))

(defn collect-prev-stages-groups
  [stage-groups stage]
  (apply
    union
    (map #(get stage-groups %) (range 1 stage))))

(defn add-stages-groups
  [coll rec]
  (let [{:keys [results time-slot]} coll
        {:keys [stage group]} rec]
    {:results (assoc-in
                results [time-slot stage]
                (conj (collect-prev-stages-groups (get results time-slot) stage) group))
     :time-slot time-slot}))

(defn group-results
  [{:keys [results]} [time-slot recs]]
  (->> recs
       (reduce add-stages-groups {:results results :time-slot time-slot})
       :results
       (merge results)
       (assoc {} :results)
       ))

(defn summarize-stage-groups
  [records]
  (->> records
       (group-by (juxt :day-of-month :start-time :end-time))
       (reduce group-results {:results {}})
       :results
       (sort-by (fn [[[dom ts te] v]] [dom (util/time-hour-to-int ts)]))
       ))

(defn load-schedule
  "Loads and parses load shedding schedule from specified file name."
  [filename]
  (->> (load-lines filename)
       (map parse-fields)
       (filter (comp not nil?))
       collect-records
       summarize-stage-groups
       ))
