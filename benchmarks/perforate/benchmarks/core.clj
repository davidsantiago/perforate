(ns perforate.benchmarks.core
  (:use perforate.core))

#_(def fake-benchmark
  (benchmark "A fake benchmark so we can know the benchmarks run."
             :cases [(benchmark-case :only
                                     (bench-fn [] "Hi."))]))

(defgoal fake "A fake goal so we can know the benchmarks run.")

(defcase fake :default
  []
  "Hi.")