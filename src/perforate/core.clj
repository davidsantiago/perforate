(ns perforate.core
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [criterium.core :as crit]
            [clojure-csv.core :as csv]))


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
       (fn ~arg-list [(fn [] ~body)]))))

(defmacro defcase-fn
  "([goal-name variant-name doc-string? init-args body])
   Works like defcase, but the arglist is for the return value of the setup
   function, and the return value must be the function to be benchmarked."
  [goal-name variant-name & opts]
  (let [[doc-string opts] (if (string? (first opts))
                            [(first opts) (rest opts)]
                            [nil opts])
        [init-arg-list body & _] opts]
    `(defcase* ~goal-name ~variant-name
       ~@(if doc-string [doc-string])  ;; So doc-string disappears when nil.
       (fn ~init-arg-list [~body]))))

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
    (crit/quick-benchmark (func)
                          (merge crit/*default-quick-bench-opts*
                                 {:samples 12
                                  :warmup-jit-period crit/*warmup-jit-period*}))
    (crit/benchmark (func) crit/*default-benchmark-opts*)))

#_(defn summarize-benchmark-results
  "Given a map returned from a criterium benchmark run, print out a nice
   summary."
  [bench-results]
  
  )

(defn run-benchmark
  "Given a benchmark map, returns a map of the variants to their criterium
   benchmark results. The setup-return argument must be the return value of
   the goal's setup function.

   Options (passed in a map):

       :quick  - Pass true to run a faster, less accurate test."
  [test-spawn-fn setup-return {:keys [quick]}]
  (let [[test-fn & cleanup-fn] (apply test-spawn-fn setup-return)
        bench-result (benchmark-function test-fn quick)]
    (when cleanup-fn
      (apply (first cleanup-fn) setup-return))
    bench-result))

(defmulti print-results
  "Print results in the specified format."
  (fn [format goal-case-map case-results-map goals] format))

(defmethod print-results :criterium
  [_ goal-case-map case-result-map goals]
  (doseq [goal goals]
      (println "Goal: " (:doc (meta goal)))
      (println "-----")
      (doseq [case (get goal-case-map goal)]
        (println "Case: " (:perforate/variant (meta case)))
        (crit/report-result (get case-result-map case))
        (println ""))))

(defn case-seq
  "Build a sequence of maps, one for each case, with :goal and :case values."
  [goal-case-map case-result-map goals]
  (for [goal goals
        case (get goal-case-map goal)
        :let [goal-name (:doc (meta goal))
              case-name (:perforate/variant (meta case))]]
    (-> (get case-result-map case)
        (assoc :goal goal-name
               :case case-name)
        (update-in [:options] dissoc :reduce-with))))

(def simple-output-keys
  [:goal :case :mean :variance :upper-q :lower-q
   :execution-count :sample-count])

(defmethod print-results :table
  [_ goal-case-map case-result-map goals]
  (let [f (ns-resolve 'clojure.pprint 'print-table)]
    (if f
      (f
       simple-output-keys
       (map
        #(-> %
             (dissoc :os-details :options :runtime-details)
             (update-in [:mean] first)
             (update-in [:variance] first)
             (update-in [:upper-q] first)
             (update-in [:lower-q] first))
        (case-seq goal-case-map case-result-map goals)))
      (println "Table format output requires clojure 1.4+"))))

(defmethod print-results :edn
  [_ goal-case-map case-result-map goals]
  (pprint/pprint (case-seq goal-case-map case-result-map goals)))

(defmethod print-results :csv
  [_ goal-case-map case-result-map goals]
  (let [results (case-seq goal-case-map case-result-map goals)]
    (print (csv/write-csv [(map name simple-output-keys)]))
    (doseq [result results
            :let [result (-> result
                             (dissoc :os-details :options :runtime-details)
                             (update-in [:mean] first)
                             (update-in [:variance] first)
                             (update-in [:upper-q] first)
                             (update-in [:lower-q] first))]]
      (print (csv/write-csv [(map (comp str result) simple-output-keys)])))))

(defn- print-goals [goals]
  (if (empty? goals)
    (println (str
      "WARNING: No goals found!\n"
      "Did you place your benchmark sources under \"benchmarks/\" "
      "or configure an alternate location as below?\n\n"
      "   {:perforate {:benchmark-paths [\"src/main/bench/\"]}}\n\n"))
    (println (apply str
      "Benchmarking the following goals: \n"
      (->> goals (map #(-> % meta :name)) (sort) (interpose "\n"))))))

(defn run-benchmarks
  "Given a list of namespaces, runs all the benchmarks they contain and reports
   the results."
  [options-map namespaces]
  (let [cases (apply concat (for [ns namespaces]
                              (filter #(:perforate/case (meta %))
                                      (vals (ns-interns (the-ns ns))))))
        goals (into #{} (map #(:perforate/goal-for-case (meta %)) cases))
        _     (print-goals goals)
        goal-case-map (into {} (for [goal goals]
                                 [goal (filter #(= goal
                                                   (:perforate/goal-for-case
                                                    (meta %)))
                                               cases)]))
        fixtures (reduce comp (fn [f] (f)) (:fixtures options-map))
        ;; Going to loop through the goals, and then loop through each case for
        ;; each goal. For each goal, we'll run the setup, then all the cases,
        ;; and then any cleanup. Then we collect up all those results into a map
        ;; of cases to results.
        case-result-map-fn
        #(into {}
               (apply concat
                      (for [goal goals]
                        (let [setup-return (if (:setup @goal)
                                             ((:setup @goal)))
                              case-results (doall (for [case (get goal-case-map
                                                                  goal)]
                                                    (let [res (run-benchmark
                                                                case
                                                                setup-return
                                                                options-map)]
                                                      [case res])))]
                          (when (:cleanup @goal)
                            (apply (:cleanup @goal) setup-return))
                          case-results))))
        case-result-map (fixtures case-result-map-fn)]
    ;; Now we have the results, so we report them.
    (doseq [fmt (or
                 (seq
                  (filter #{:edn :csv :criterium :table} (keys options-map)))
                 [:criterium])]
      (print-results fmt goal-case-map case-result-map goals))))
