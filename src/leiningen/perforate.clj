(ns leiningen.perforate
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]
            [clojure.java.io :as io])
  (:use [bultitude.core :only [namespaces-on-classpath]]))

(defn get-benchmark-sourcepaths [project]
  (get-in project [:perforate :benchmark-paths] ["benchmarks/"]))

(defn benchmark-namespaces
  [project]
  (sort
    (namespaces-on-classpath
      :classpath
      (map io/file (get-benchmark-sourcepaths project)))))

(defn locate-perforate-in-project
  "Given a project map, find the perforate entry in the :plugins key, so we can
   figure out what version we are."
  [project]
  ;; Either this is running as a plugin, or this file is on the classpath. In
  ;; the former case, this should succeed, in the latter, it is unnecessary, and
  ;; the nil return value will get merged harmnessly away in the project map.
  (last (filter #(or (= (first %) 'perforate)
                     (= (first %) 'perforate/perforate))
                (:plugins project))))

(defn parse-args
  "Takes the command args as input and returns a vector with two elements. The
   first is a set of the specified environments (the name key in each
   environment) and the second is a map of the options given."
  [args]
  (let [[environments options] (split-with #(not (.startsWith % "-")) args)]
    [(into #{} environments)
     (into {} (map (fn [opt-str] (vector (keyword (apply str
                                                         (filter #(not= \- %)
                                                                 opt-str)))
                                         true)) options))]))

(defn run-environment?
  "Given the specified environments set and a given environment, return true
   if we should run the benchmark. If the specified-environments set is empty,
   then all environments passed as the second arg will be run."
  [specified-environments environment]
  (if (empty? specified-environments)
    true
    (specified-environments (name (:name environment))))) ;; compare as string.

(defn perforate
  "Run the performance tests in the perforate
   benchmark-paths dir (default \"benchmarks/\")."
  [project & args]
  (let [perforate-options (:perforate project)
        environments (:environments perforate-options ::no-environments)
        has-environments (not= environments ::no-environments)
        perforate-dep (locate-perforate-in-project project)
        perforate-profile (merge {:source-paths
                                  (get-benchmark-sourcepaths project)}
                                 (if perforate-dep
                                   {:dependencies [perforate-dep]})
                                 (get-in project
                                         [:profiles :perforate]))
        ;; We remove the source-paths key from the profile, so that we can use
        ;; older versions from a JAR and not have the current source override
        ;; them on the classpath. Environment profiles can add a source-path
        ;; back in, since they get merged in afterwards.
        project (if has-environments (dissoc project :source-paths) project)
        ;; Project should have the perforate profile added for all that follows.
        project (project/merge-profiles project [perforate-profile])
        [specified-environments options] (parse-args args)
        environments (if has-environments
                       environments
                       [{:namespaces (benchmark-namespaces project)}])]
    (doseq [{:keys [name profiles namespaces fixtures] :as environment}
            environments]
      (when (run-environment? specified-environments environment)
        (when (> (count environments) 1)
          (println "Benchmarking environment: " (:name environment)))
        (when (seq profiles)
          (println "Benchmarking profiles: " profiles))
        (println "======================")
        (let [project (project/merge-profiles project profiles)
              action `(do
                        (set! *warn-on-reflection* true)
                        (when (seq '~namespaces)
                          (apply require :reload '~namespaces))
                        (perf/run-benchmarks ~options '~namespaces))]
          (eval/eval-in-project project
                                action
                                `(require
                                  ['~'perforate.core :as '~'perf]
                                  ~@(map
                                     #(list 'quote (symbol (namespace %)))
                                     fixtures))))))))
