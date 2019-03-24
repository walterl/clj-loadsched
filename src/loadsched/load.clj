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

(defn append-new-record
  [coll [day-of-month group] stage start-time end-time]
  (conj coll {:day-of-month day-of-month
              :stage stage
              :start-time start-time
              :end-time end-time
              :group group}))

(defn denormalize-line
  [coll line]
  (let [{:keys [records start-time end-time]} coll
        line-start-time (:start-time line)
        line-end-time (:end-time line)]
    (if (and line-start-time line-end-time)
      ; It's a line with only start/end times
      {:records records :start-time line-start-time :end-time line-end-time}

      ; Otherwise it's a line with stage and day/groups info
      (let [stage (:stage line)]
        (->> (:day-groups line)
             (reduce #(append-new-record %1 %2 stage start-time end-time) [])
             (concat records)
             (assoc coll :records)
             )))))

(defn lines-to-records
  "Turns a sequence of parsed lines (`parse-fields`) into a sequence of complete schedule records."
  [parsed-lines]
  (->> parsed-lines
       (reduce denormalize-line {:records [] :start-time nil :end-time nil})
       :records
       (sort-by (juxt :day-of-month :stage (comp util/time-hour-to-int :start-time)))
       ))

(defn collect-prev-stages-groups
  "Collect all groups from stage prior to `stage`, in a set."
  [stage-groups stage]
  (apply
    union
    (map #(get stage-groups %) (range 1 stage))))

(defn add-stages-groups
  "Add the groups for the specified stage and time slot."
  [coll {:keys [stage group]} time-slot]
  (assoc-in
    coll [time-slot stage]
    (conj (collect-prev-stages-groups (get coll time-slot) stage) group)))

(defn group-stage-groups
  "Add stage data for the specified time slot."
  [coll [time-slot grouped-recs]]
  (reduce #(add-stages-groups %1 %2 time-slot) coll grouped-recs))

(defn summarize-stage-groups
  "Summarize schedule data by grouping groups under time slot and stage."
  [records]
  (->> records
       (group-by (juxt :day-of-month :start-time :end-time))
       (reduce group-stage-groups {})
       (sort-by (fn [[[dom ts te] v]] [dom (util/time-hour-to-int ts)]))
       ))

(defn load-schedule
  "Loads and parses load shedding schedule from specified file name."
  [filename]
  (->> (load-lines filename)
       (map parse-fields)
       (filter (comp not nil?))
       lines-to-records
       summarize-stage-groups
       ))
