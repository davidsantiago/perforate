(ns leiningen.test.perforate
  (:use clojure.test 
        leiningen.perforate))

(def sample-project-default-sourcepath
  '(defproject perforate "0.1.0-SNAPSHOT"
     :description "The next Instagram."
     :dependencies [[org.clojure/clojure "1.4.0"]]
     :plugins [[perforate "0.4.0-SNAPSHOT"]]))

(def sample-project-custom-sourcepath
  '(defproject perforate "0.1.0-SNAPSHOT"
     :description "The next Tumblr."
     :dependencies [[org.clojure/clojure "1.4.0"]]
     :plugins [[perforate "0.4.0-SNAPSHOT"]]
     :perforate {:source-paths ["src/bench/clojure/"]}))

(deftest test-sourcepaths-configuration 
  (testing "default sourcepath"
    (is (= ["benchmarks/"] 
           (get-benchmark-sourcepaths sample-project-default-sourcepath))))
  (testing "custom sourcepath"
    (is (= ["src/bench/clojure/"])
        (get-benchmark-sourcepaths sample-project-custom-sourcepath))))
