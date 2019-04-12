(defproject loadsched "0.1.0-SNAPSHOT"
  :description "A simple command-line utility for querying a load shedding schedule."
  :url "https://github.com/walterl/clj-loadsched/"
  :license {:name "MIT"
            :url "https://github.com/walterl/clj-loadsched/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/tools.trace "0.7.10"]]
  :main ^:skip-aot loadsched.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
