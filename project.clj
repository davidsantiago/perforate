(defproject perforate "0.4.0-SNAPSHOT"
  :description "Painless benchmarking with Leiningen."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [criterium "0.4.1"]
                 [clojure-csv "1.3.2"]]
  :eval-in :leiningen
  :perforate {:environments [{:namespaces [perforate.benchmarks.core]}]})
