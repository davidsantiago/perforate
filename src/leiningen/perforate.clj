(ns leiningen.perforate
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]))

(def perforate-default-source-path-profile {:source-paths ["benchmarks/"]})

(defn perforate
  "Run the performance tests in the benchmarks/ dir."
  [project & args]
  (let [perforate-options (:perforate project)
        perforate-profile (merge perforate-default-source-path-profile
                                 (get-in project
                                         [:profiles :perforate]))
        ;; We remove the source-paths key from the profile, so that we can use
        ;; older versions from a JAR and not have the current source override
        ;; them on the classpath. Environment profiles can add a source-path
        ;; back in, since they get merged in afterwards.
        project (dissoc project :source-paths)
        ;; Project should have the perforate profile added for all that follows.
        project (project/merge-profile project perforate-profile)
        environments (:environments perforate-options)]
    (doseq [{:keys [profiles namespaces]} environments]
      (println "Benchmarking profiles: " profiles)
      (println "======================")
      (let [project (project/merge-profiles project profiles)
            action `(do
                      (when (seq '~namespaces)
                        (apply require :reload '~namespaces))
                      (apply perf/run-benchmarks '~namespaces))]
        (eval/eval-in-project project
                              action
                              '(require ['perforate.core :as 'perf]))))))
