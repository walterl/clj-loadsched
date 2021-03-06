(ns loadsched.core
  (:require [clojure.java.io :refer [file]]
            [clojure.pprint :as pprint]
            [clojure.set :refer [intersection]]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.trace :refer [deftrace]]
            [loadsched.load :as sched-loading])
  (:gen-class))

(def cli-options
  [["-d" "--day DAY_OF_MONTH" "Day of month to show schedule for."
    :id :day-of-month
    :default :today
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 % 31) "Must be between 1 and 31, inclusive"]]
   ["-f" "--filename FILENAME" "File to read schedule from."
    :default "schedule.txt"
    :validate [#(.isFile (file %)) "Invalid file name"]
    ]
   ["-g" "--group GROUP" "Filter on specified groups. Default: include all groups."
    :default []
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 % 16) "Must be between 1 and 16"]
    :assoc-fn (fn [opts _ value] (assoc opts :group (conj (:group opts) value)))
    ]
   ["-s" "--stage STAGE" "Schedule for this stage only. If not given, it is queried from Eskom."
    :default :auto
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 % 8) "Must be between 1 and 8"]
    ]
   [nil "--print-schedule" "Print the entire schedule."]
   ["-h" "--help" "Display help text and exit."]
   ])

(defn print-usage [summary]
  (->> ["Load, filter and print load shedding schedule."
        ""
        "Usage: loadsched [[-d DAY_OF_MONTH] [-f FILENAME] [-g GROUP...] [-s STAGE] | --print-schedule]"
        ""
        "Default behavior is to print the entire schedule (all groups) for current load shedding stage, for today."
        ""
        "Options:"
        summary]
       (string/join \newline)
       println
       ))

(defn fetch-stage []
  (let [state-id (Integer/parseUnsignedInt (slurp "http://loadshedding.eskom.co.za/LoadShedding/getstatus"))]
    (case state-id
      99
      (throw (ex-info "Failed to lookup current load shedding stage."
                      {:error? true :state-id state-id}))

      1
      (throw (ex-info "No load shedding in progress! \\o/"
                      {:error? false :state-id state-id}))

      (dec state-id)))
  )

(defn fetch-today []
  (.get (java.util.Calendar/getInstance) java.util.Calendar/DAY_OF_MONTH))

(defn filter-by-stage
  [schedule stage]
  (cond
    (= stage :auto)
    (filter-by-stage schedule (fetch-stage))

    (some? stage)
    (map (fn [[k v]] [k {stage (get v stage)}]) schedule)

    :else
    schedule
    ))

(defn filter-by-day
  [schedule day-of-month]
  (cond
    (= day-of-month :today)
    (filter-by-day schedule (fetch-today))

    (some? day-of-month)
    (filter #(= (first (first %)) day-of-month) schedule)

    :else
    schedule
    ))

(defn remove-other-groups
  "Removes groups from `state-groups`'s value sets, that are *not* in `keep-groups`, removes empty items."
  [stage-groups keep-groups]
  (let [keep-groups-set (set keep-groups)]
    (->> stage-groups
         (map (fn [[stage groups]]
                (let [overlap (intersection keep-groups-set (set groups))]
                  (when-not (empty? overlap)
                    [stage overlap]
                    ))
                ))
         (remove (comp nil? last))
         (into {})
         )))

(defn filter-by-groups
  [schedule filter-groups]
  (if (empty? filter-groups)
    schedule
    (->> schedule
         (map (fn [[k v]] [k (remove-other-groups v filter-groups)]))
         (remove (comp empty? last))
         (into {})
         )))

(defn fmt-timeslot-data
  [[[day-of-month start-time end-time] stage-groups]]
  (string/join
    \newline
    (for [stage (sort (keys stage-groups))
          :let [groups (->> stage (get stage-groups) sort (string/join ", "))]]
      (format "%2s %s %5s - %5s: %s" day-of-month stage start-time end-time groups))))

(defn print-schedule
  "Prints the given schedule to *out*."
  [schedule]
  (->> schedule
       (map fmt-timeslot-data)
       (string/join \newline)
       println
       ))

(defn load-and-print-schedule
  [options]
  (let [schedule (sched-loading/load-schedule (:filename options))]
    (print-schedule
      (if (:print-schedule options)
        schedule
        (-> schedule
            (filter-by-stage (:stage options))
            (filter-by-day (:day-of-month options))
            (filter-by-groups (:group options))
            )))))

(defn -main
  "Load shedding schedule tool."
  [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (do
        (print-usage summary)
        (System/exit 0))

      errors
      (let [error-msg (string/join \newline (map #(str "* " %) errors))]
        (println error-msg)
        (System/exit 1))

      :else
      (try
        (load-and-print-schedule options)
        (catch clojure.lang.ExceptionInfo e
          (let [is-error? (-> e ex-data :error?)]
            (println (if is-error? "!" "#") (.getMessage e))
            (System/exit (if is-error? 1 0)))))
      )))
