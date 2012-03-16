(ns perforate.benchmarks.core
  (:use perforate.core))

(def fake-benchmark
  (benchmark "A fake benchmark so we can know the benchmarks run."
             :cases [(benchmark-case :only
                                     (bench-fn [] "Hi."))]))