(defproject perforate "0.1.1"
  :description "Painless benchmarking with Leiningen."
  :dependencies [[org.clojure/clojure "1.4.0-beta5"]
                 [criterium "0.2.0"]]
  :eval-in :leiningen
  :perforate {:environments [{:namespaces [perforate.benchmarks.core]}]})