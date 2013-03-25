# perforate

Painless benchmarking with
[Leiningen](https://github.com/technomancy/leiningen) and
[Criterium](https://github.com/hugoduncan/criterium).

## Usage

Perforate is a plugin for Leiningen 2 that makes it easy to write and
run benchmarks, much like the `test` task built into Leiningen. The
benchmarking is done with Hugo Duncan's
[Criterium](https://github.com/hugoduncan/criterium), which is
carefully designed to overcome common JVM benchmarking pitfalls.

To use perforate, create a directory in the top level of your project
called "benchmarks". This directory will be added to the classpath
when perforate runs, and all the specified tests inside it will be
run. Again, you can think of it as being very similar to the "test/"
directory used by the test task.

When you are trying to learn about the performance of some code, you
are typically focused on specific tasks or goals that you would like
the code to perform. You are also interested in the performance
characteristics of one or more different ways of accomplishing that
goal. Perforate is organized around these two concepts: *goals* are
the abstract goal that you wish to test various implementations of,
while *cases* are tests of the specific implementations. When you run
the perforate task, the report that it generates will group all of the
cases in a given goal together to make it easy to compare them.

The `perforate.core` namespace defines functions and macros that make
it easy to define goals and cases. For example, suppose there is a file
in benchmarks/myproject/simple_bench.clj:

```
(ns myproject.simple-bench
  (:use perforate.core))
  
(defgoal simple-bench "A simple benchmark.")

(defcase simple-bench :really-simple
  [] (+ 1 1))
  
(defcase simple-bench :slightly-less-simple
  [] (+ 1 1 1))
```

Here we defined a simple benchmark called simple-bench and gave it a
doc string. We also defined two cases, :really-simple and
:slightly-less-simple. As you can see, goals are "open" in the sense
that you can add as many cases to a given goal as you'd like, or even
spread them across namespaces. There is some similarity between
defgoal/defcase and defmulti/defmethod.

By default, perforate will run all benchmarks it finds in the
"benchmarks/" directory of your project. But you can get much finer
control by adding a `:perforate` key to your project map. The value of
this key should be a map. The key `:environments` will hold a sequence
of test environments the plugin should run when it is called. An
environment consists of a map of key/value pairs. When present, the
`:profiles` key should be a sequence of profiles from the project.clj
that should be merged into the project map during this run of the
benchmark. When present, the `:namespaces` key, should contain a
sequence of symbols naming the namespaces to run. You can also add a
`:name` key to give each environment a name. For example, suppose the
project.clj file contains the following:

```
:perforate {:environments [{:name :a1b1
                            :profiles [:a1 :b1]
                            :namespaces [myproject.simple-bench myproject.complex-bench]}
                           {:name :a2b2
                            :profiles [:a2 :b2]
                            :namespaces [myproject.simple-bench myproject.trivial-bench]}]}
```

As you can see in this example, we have two environments, each using
two different profiles. The two environments will run a shared set of
benchmarks and a set of benchmarks specific to that environment,
perhaps because in the environment inadequate or older versions of
libraries are being tested.

Using the environments in combination with Leiningen 2's profiles, you
can create sets of tests that run on multiple versions of Clojure, or
use older versions of libraries, or use other sets of options from the
project map. By naming environments on the command line when you run
the perforate task, you can restrict the benchmark run to only the
environments you specified.

One thing to note: by default, the source directories are not included
on the classpath. This allows you to easily work off of JARs
containing old versions of your project just by including them in the
dependencies of the profiles you specify. If you want to test the
current version from the source directory, just make a profile
containing a `:source-paths` key which contains the "src/" directory
(or wherever your source is).

### Setup and Cleanup

Many benchmarks require some work to set up the environment in which
they run, and some also require cleanup work to remove any byproducts
of the tasks being tested or the setup phase itself. You can specify a
function to run as a setup (or cleanup) phase by passing it as an
argument to `defgoal`:

```
(defgoal simple-with-setup "A benchmark with a setup phase."
   :setup (fn [] ...do stuff... [1 2 3])
   :cleanup (fn [a b c] ...do stuff...)
```

Note how the setup function returns a vector of three values. The
cleanup function takes three arguments. When the cleanup function is
called (if there is one), it will have the return value of the setup
function applied to its arguments. So be sure they match.

The return value of the setup function is also passed to your
benchmark function. Thus, it must have a matching set of arguments in
the `defcase` arglists, as shown in the following `defcase` for
`simple-with-setup`:

```
(defcase simple-with-setup :default
  [a b c] (+ a b c))
```

Starting in version 0.3.0, you can also specify clojure.test-style fixtures in your perforate options. Fixtures are useful for setup work that is specific to an environment and not necessarily a goal. For example, your perforate options might look like

```
{:environments
  [{:name :array
    :namespaces [myproject.implementation]
    :fixtures [myproject.implementation.array/with-array]}
   {:name :volatile
    :namespaces [myproject.implementation]
    :fixtures [myproject.implementation.type/with-volatile]}
   {:name :unsynchronized
    :namespaces [myproject.implementation]
    :fixtures [myproject.implementation.type/with-unsynchronized]}]}
```

In this example, the same exact tests are run (those in the namespace myproject.implementation), but the fixtures such as `with-array` could be something that swaps out the implementation used, as in 

```
(defn with-array [f] 
  (with-redefs [myproject.core/execute-expr-core execute-expr-core-with-array] 
    (f)))
```

Or perhaps some parameter of interest can be dynamically bound to different values using fixtures, to see how the parameter causes performance to vary. However, this last example illustrates the need for caution when using fixtures: certain dynamic constructs like a dynamic variable, when used in a very tight inner loop function that you are attempting to measure, could result in misleading results. You should probably not reach for fixtures as your first option for running tests, but if they are a good fit, use a little extra caution to make sure you know what you are measuring when you do.

### Under the Hood

In reality, the function that gets benchmarked by criterium always
takes zero arguments. The `defcase` macro is doing some work behind
the scenes to give you the arglist in lexical scope while still
running as a zero-argument function. A case is actually a function
that returns the function to be benchmarked. The case function accepts
the setup function's arguments, if any, and returns the function to be
benchmarked as a closure over the setup function arguments.

Sometimes it's helpful to do some additional case-specific setup on
those arguments before returning the actual function to be benchmarked
for the given case. For this, there's the lower-level `defcase*`,
which takes a single argument: a function that returns the function to
be benchmarked. This function must have an arglist that matches the
return value of the setup function, and must return a vector
containing either one or two functions. The first function is always
the function be benchmarked; if there is a second function, it is a
case-specific cleanup function that will be called when that case is
done being measured. Here is the `simple-with-setup` example as a `defcase*`:

```
(defcase* simple-with-setup :default2
  (fn [a b c] [(fn [] (+ a b c))]))
```

As you can see, this function returns the benchmarkable function in a
vector, but is otherwise equivalent. In fact, the `defcase` version
should turn into something quite like this `defcase*` version. The
main interest in `defcase*` is if there is some case-specific setup or
cleanup. An example might be

```
(defcase* simple-with-setup :case-setup
  (fn [a b c] 
    (let [d (* a b c)]
      [(fn [] (+ a b c d))])))
```

## An Example

Suppose the project map contains the following keys:

```
:dependencies [[org.clojure/clojure "1.3.0"]
               [perforate "0.3.1"]]
  :plugins [[perforate "0.3.1"]]
  :profiles {:current {:source-paths ["src/"]}
             :clj1.4 {:dependencies [[org.clojure/clojure "1.4.0-beta5"]]}
             :clj1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :version1 {:dependencies [[myproject "1.0.0"]]}
             :version2 {:dependencies [[myproject "2.0.0"]]}}
  :perforate {:environments [{:name :version1
                              :profiles [:clj1.3 :version1]
                              :namespaces [myproject.benchmarks.core]}
                             {:name :version2
                              :profiles [:clj1.3 :version2]
                              :namespaces [myproject.benchmarks.core]}
                             {:name :current
                              :profiles [:clj1.4 :current]
                              :namespaces [myproject.benchmarks.core]}]}
```

A run could look like this:

```
David$ lein2 perforate
Benchmarking profiles:  [:clj1.3 :version1]
======================
Goal:  Test Speed
----------
Case: :default
Evaluation count             : 120
             Execution time mean : 793.924842 ms  95.0% CI: (793.842767 ms, 793.975717 ms)
    Execution time std-deviation : 11.865390 ms  95.0% CI: (11.814311 ms, 11.917502 ms)
         Execution time lower ci : 780.498500 ms  95.0% CI: (780.498500 ms, 780.498500 ms)
         Execution time upper ci : 809.372500 ms  95.0% CI: (809.368000 ms, 809.372500 ms)

Benchmarking profiles:  [:clj1.3 :version2]
======================
Goal:  Test Speed
----------
Case: :default
Evaluation count             : 120
             Execution time mean : 637.975817 ms  95.0% CI: (637.931333 ms, 638.011125 ms)
    Execution time std-deviation : 8.807448 ms  95.0% CI: (8.771608 ms, 8.859762 ms)
         Execution time lower ci : 627.351225 ms  95.0% CI: (627.351225 ms, 627.353000 ms)
         Execution time upper ci : 649.712000 ms  95.0% CI: (649.712000 ms, 649.713975 ms)

Benchmarking profiles:  [:clj1.4 :current]
======================
Goal:  Test Speed
----------
Case: :default
Evaluation count             : 120
             Execution time mean : 633.556467 ms  95.0% CI: (633.515842 ms, 633.607392 ms)
    Execution time std-deviation : 9.972554 ms  95.0% CI: (9.914351 ms, 10.055073 ms)
         Execution time lower ci : 622.291500 ms  95.0% CI: (622.291500 ms, 622.291500 ms)
         Execution time upper ci : 647.861000 ms  95.0% CI: (647.861000 ms, 647.887350 ms)

Found 2 outliers in 60 samples (3.3333 %)
	low-severe	 2 (3.3333 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
```

If you only wanted to run the `:current` profile, and you wanted Criterium to do faster, less accurate benchmarks, you could run the following command on the command-line, which would result in similar output:

```
David$ lein2 perforate current --quick
Benchmarking profiles:  [:clj1.4 :current]
======================
WARNING: Final GC required 3.7433766853457424 % of runtime
WARNING: Final GC required 2.563144804701669 % of runtime
Goal:  Test Speed
-----
Case:  :default
Evaluation count             : 6
             Execution time mean : 524.897667 ms  95.0% CI: (524.493000 ms, 525.080167 ms)
    Execution time std-deviation : 8.663503 ms  95.0% CI: (8.414881 ms, 8.730166 ms)
         Execution time lower ci : 515.172000 ms  95.0% CI: (515.172000 ms, 515.172000 ms)
         Execution time upper ci : 534.102250 ms  95.0% CI: (534.102250 ms, 534.102250 ms)
```

## News

* Released version 0.3.1, which fixes a bug that prevented `defcase` from working correctly when the argument list was not empty.

* Released version 0.3.0, which adds support for outputting benchmark results in new formats: EDN, CSV, and table. Also adds support for fixtures. Thanks to [Hugo Duncan](http://github.com/hugoduncan).

* Released version 0.2.4, which updates to the latest version of Criterium and runs all benchmarks with *warn-on-reflection* set to true.

* Released version 0.2.3, which updates for latest Leiningen 2 preview versions.

* Released version 0.2.2, which adds the defcase-fn convenience macro. Should be nicer to use instead of defcase* for cases with no cleanup functions.

* Released version 0.2.1, which fixes the (previously) completely broken defcase macro.

* Released version 0.2.0, a substantial redesign and rewrite.
  - Now works without the need to specify environments, thanks to [Hugo Duncan](https://github.com/hugoduncan).
  - Redesigned to an "open" system modeled on multimethods.
  - Added command line arguments to the perforate task, so specific environments and options can be specified on the command line.

* Released version 0.1.1 with some bug fixes.

## Contributors

* [Hugo Duncan](https://github.com/hugoduncan)

## License

Copyright © 2012 David Santiago

Distributed under the Eclipse Public License, the same as Clojure.
