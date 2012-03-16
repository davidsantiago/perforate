(defproject perforate "0.1.0"
  :description "Painless benchmarking with Leiningen."
  :dependencies [[org.palletops/clojure "1.3.1-SNAPSHOT"]
                 [criterium "0.2.1-SNAPSHOT"]]
  :eval-in :leiningen
  :perforate {:environments [{:namespaces [perforate.benchmarks.core]}]})