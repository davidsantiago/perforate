(ns perforate.test.core
  (:use clojure.test
        perforate.core))

;; Set up a goal we can test things on.
(defn goal-setup-fixture
  [f]
  (defgoal a)
  (f))

(use-fixtures :once goal-setup-fixture)

(deftest goals
  (is (= {} @(defgoal b "Hi")))
  (is (map? @(defgoal b "Hi" :setup #(+ 1 2))))
  (is (fn? (:setup @(defgoal b "Hi" :setup #(+ 1 2)))))
  (is (= "Hi" (:doc (meta (defgoal b "Hi"))))))

;; Due to the way clojure.test works, defcase won't work unless we
;; fully qualify the goalname symbols. But it seems to work when not
;; in tests.
#_(deftest cases
  (is (fn? @(defcase a :default [] (+ 1 2))))
  (is (fn? @(defcase a :default "Hi" [] (+ 1 2))))
  (is (= 3 (@(defcase a :default [] (+ 1 2))))))

(deftest goal-resolving
  (let [_ (defgoal goal-resolving-goal)]
    (is (map? goal-resolving-goal))
    (is (var? (#'perforate.core/resolve-goal-from-symbol
               ;; full ns seems necessary for tests. clojure.test macro magic?
               'perforate.test.core/goal-resolving-goal)))
    (is (nil? (#'perforate.core/resolve-goal-from-symbol 'asdfgh))) ;; not a var
    (is (nil? (#'perforate.core/resolve-goal-from-symbol 'fn))))) ;; not a goal