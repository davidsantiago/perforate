(ns perforate.core
  (:require [criterium.core :as crit]))


(defn universal-reducer
  "A function that can be used to reduce any function output."
  [a b]
  (let [a (if (nil? a) 0 a)
        b (if (nil? b) 0 b)]
    (+ (.hashCode a) (.hashCode b))))

(defmacro defgoal
  "([name doc-string? & options])
   Creates a new benchmark goal with the associated (optional) setup and
   cleanup functions. Options are key/value pairs, picked from:

     :setup    - Specifies a function to call before every test. Its return
                 value will be applied to each test-case spawn function.
     :cleanup  - Specifies a function to call after every test. Its arg list
                 will have the return value of the setup function applied."
  [name & opts]
  (let [[doc-string opts] (if (string? (first opts))
                            [(first opts) (rest opts)]
                            [nil opts])
        option-map (apply hash-map opts)]
    `(def ~(with-meta name {:doc doc-string
                            :perforate/goal true})
       ~option-map)))

(defn- resolve-goal-from-symbol
  "Given a symbol naming a goal, return the var holding the goal, if it exists
   (and is a goal)."
  [goal-symbol]
  (let [maybe-goal-var (resolve goal-symbol)]
    (when (:perforate/goal (meta maybe-goal-var))
      maybe-goal-var)))

(defmacro defcase*
  "([goal-name variant-name doc-string? test-spawn-fn])
   Creates a benchmark case for the goal goal-name. The goal-name argument
   should be a symbol referencing the desired goal this case implements, and
   variant-name can be just about anything (aim for keyword, probably). defcase*
   is more flexible than defcase in that it allows the more detailed
   specification of the test spawn function, including a cleanup function.

   The test-spawn-fn is a function that returns a vector of one or two
   functions. The first element is always the zero-arg function that will be
   run repeatedly for benchmarking. The optional second element is a cleanup
   function that will be called once benchmarking is complete. Both the
   test-spawn function and the clean-up function will have the return value of
   the goal's setup function applied to them, so their arglists must match
   that."
  [goal-name variant-name & opts]
  (let [[doc-string opts] (if (string? (first opts))
                            [(first opts) (rest opts)]
                            [nil opts])
        test-spawn-fn (first opts)]
    (if-let [goal (resolve-goal-from-symbol goal-name)]
      `(def ~(with-meta (symbol (gensym (str (name goal-name) "--"
                                             (name variant-name))))
               {:doc doc-string
                :perforate/case true
                :perforate/goal-for-case goal
                :perforate/variant variant-name})
         ~test-spawn-fn)
       (throw (Exception. (str "Could not find goal " (name goal-name)))))))

(defmacro defcase
  "([goal-name variant-name doc-string? arg-list body])
   Creates a benchmark case for the goal goal-name. The goal-name argument
   should be a symbol referencing the desired goal this case implements, and
   variant-name can be just about anything (aim for keyword, probably).

   The arg-list will have the return value of the goal's setup function applied,
   and the body will be benchmarked."
  [goal-name variant-name & opts]
  (let [[doc-string opts] (if (string? (first opts))
                            [(first opts) (rest opts)]
                            [nil opts])
        [arg-list body & _] opts]
    `(defcase* ~goal-name ~variant-name
       ~@(if doc-string [doc-string]) ;; So doc-string disappears when nil.
       (fn ~arg-list ~body))))

(defmacro bench-fn
  "Convenience macro to generate a defcase* spawn function when there is
   no processing required. These two are equivalent:

     * (benchmark-case \"name\" (fn [a1 a2 a3] [(fn [] ...)]))
     * (benchmark-case \"name\" (bench-fn [a1 a2 a3] ...)

   where the actions of the function are elided."
  [arg-list body]
  `(fn ~arg-list [(fn [] ~body)]))

(defn benchmark-function
  "Benchmark the given function, quick or not."
  [func quick?]
  (if quick?
    (crit/quick-benchmark (func) :reduce-with universal-reducer)
    (crit/benchmark (func) :reduce-with universal-reducer)))

#_(defn summarize-benchmark-results
  "Given a map returned from a criterium benchmark run, print out a nice
   summary."
  [bench-results]
  
  )

(defn run-benchmark
  "Given a benchmark map, returns a map of the variants to their criterium
   benchmark results. The setup-return argument must be the return value of
   the goal's setup function.

   Options:

       :quick  - Pass true to run a faster, less accurate test."
  [test-spawn-fn setup-return & {:keys [quick]}]
  (let [[test-fn & cleanup-fn] (apply test-spawn-fn setup-return)
        bench-result (benchmark-function test-fn quick)]
    (when cleanup-fn
      (apply (first cleanup-fn) setup-return))
    bench-result))

#_(defn run-benchmarks
  "Given a list of namespaces, runs all the benchmarks they contain."
  [& namespaces]
  (doseq [ns namespaces]
    (let [cases (filter #(:perforate/benchmark (meta %))
                             (map deref (vals (ns-interns (the-ns ns)))))]
      (doseq [benchmark benchmarks]
        (println "Benchmark: " (:doc (meta benchmark)))
        (println "----------")
        (let [benchmark-results (run-benchmark benchmark)]
          (doseq [[case-name case-results] benchmark-results]
            (println "Benchmark Case:" case-name)
            (crit/report-result case-results))))))
  (println ""))

(defn run-benchmarks
  "Given a list of namespaces, runs all the benchmarks they contain and reports
   the results."
  [options-map namespaces]
  (let [cases (apply concat (for [ns namespaces]
                              (filter #(:perforate/case (meta %))
                                      (vals (ns-interns (the-ns ns))))))
        goals (into #{} (map #(:perforate/goal-for-case (meta %)) cases))
        goal-case-map (into {} (for [goal goals]
                                 [goal (filter #(= goal
                                                   (:perforate/goal-for-case
                                                    (meta %)))
                                               cases)]))
        ;; Going to loop through the goals, and then loop through each case for
        ;; each goal. For each goal, we'll run the setup, then all the cases,
        ;; and then any cleanup. Then we collect up all those results into a map
        ;; of cases to results.
        case-result-map
        (into {}
              (apply concat
                     (for [goal goals]
                       (let [setup-return (if (:setup goal)
                                            ((:setup goal)))
                             case-results (for [case (get goal-case-map goal)]
                                            (let [res (run-benchmark
                                                       case
                                                       setup-return)]
                                              [case res]))]
                         (when (:cleanup goal)
                           (apply (:cleanup goal) setup-return))
                         case-results))))]
    ;; Now we have the results, so we report them.
    (doseq [goal goals]
      (println "Goal: " (:doc (meta goal)))
      (println "-----")
      (doseq [case (get goal-case-map goal)]
        (println "Case: " (:perforate/variant (meta case)))
        (crit/report-result (get case-result-map case))
        (println "")))))
