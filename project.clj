(defproject perforate "0.2.4"
  :description "Painless benchmarking with Leiningen."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [criterium "0.3.1"]]
  :eval-in :leiningen
  :perforate {:environments [{:namespaces [perforate.benchmarks.core]}]})
