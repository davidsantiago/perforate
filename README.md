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

In perforate, a benchmark is an abstract notion of a task, some goal
of interest that you are interested in learning about. Each benchmark
can have any number of cases, which are concrete implementations of
the task of interest. By implementing more than one case per
benchmark, you can compare the performance of different approaches to
implementing a given task. When you run perforate, the report that it
generates will group all of the cases in a given benchmark together to
make it easy to compare them.

Benchmarks and cases are simple maps that perforate examines to figure
out what tests to perform. The `perforate.core` namespace defines
functions and macros that make it easy to generate those maps. For
example, suppose there is a file in
benchmarks/myproject/simple_bench.clj:

```
(ns myproject.simple-bench
  (:use perforate.core))
  
(def simple-benchmark
  (benchmark "A simple benchmark."
             :cases [(benchmark-case :really-simple
                                     (bench-fn [] (+ 1 1)))
                     (benchmark-case "Slightly less simple"
                                     (bench-fn [] (+ 1 1 1)))]))
```

In this example, a benchmark is created and assigned to the
`simple-benchmark` variable. It has two cases, `:really-simple` and
`"Slightly less simple"`, which shows that you can identify the cases
with pretty much any object you'd like. The `bench-fn` generates a
function that is called by the benchmark library, that runs its body.

In order to know which benchmarks to run, you should add a
`:perforate` key to your project map. The value of this key should be
a map. The key `:environments` will hold a sequence of test
environments the plugin should run when it is called. For now, and
environment consists of a map of two key/value pairs. The first value,
`:profiles`, should be a sequence of profiles from the project.clj
that should be merged into the project map during this run of the
benchmark. The second value, `:namespaces`, should contain a sequence
of symbols naming the namespaces to run. For example, suppose the
project.clj file contains the following:

```
:perforate {:environments [{:profiles [:a1 :b1]
                            :namespaces [myproject.simple-bench myproject.complex-bench]}
                           {:profiles [:a2 :b2]
                            :namespaces [myproject.simple-bench myproject.trivial-bench]}]}
```

As you can see in this example, we have two environments, each using
two different profiles. The first will run two sets of benchmarks, and
the second will run the simple-bench set, but will run a different
set, perhaps because in this environment inadequate or older versions
of libraries are being tested.

Using the environments in combination with Leiningen 2's profiles, you
can create sets of tests that run on multiple versions of Clojure, or
use older versions of libraries, or use other sets of options from the
project map.

One thing to note: by default, the source directories are not included
on the classpath. This allows you to easily work off of JARs
containing old versions of your project just by including them in the
dependencies of the profiles you specify. If you want to test the
current version, just make a profile containing a `:source-paths` key
which contains the "src/" directory (or wherever your source is).

### Setup and Cleanup

In the above examples, we used the `bench-fn` macro to create a simple
test function. Why not use a regular function? You could do that, but
benchmark-case is not expecting a simple function to run directly, it
is expecting a function that returns the function to run. The
`bench-fn` macro creates a function to return a function that performs
the body you give it. It is a simple way to express the degenerate
case of the setup/cleanup feature.

Many benchmarks require some work to set up the environment in which
they run, and some also require cleanup work to remove any byproducts
of the tasks being tested or the setup phase itself. You can specify a
function to run as a setup phase by passing it as an argument to
`benchmark`:

```
(benchmark "A benchmark with a setup phase."
   :cases [(benchmark-case :after-setup
                           (fn [a b c] (...add'l setup stuff with a, b, and c...)
                                       [(fn [] (...do stuff with a, b, and c...))]))]
   :setup (fn [] ...do stuff... [1 2 3]))
```

Here we manually create the benchmark-case function and this time it
has arguments. Those arguments are given the values of the return
value of the setup function, so that they are available to the
function itself when it runs. The benchmark-case function is always
called with the results of the setup-phase applied to it, so the
arguments must be compatible.

Of course, if you do not need to do additional setup with the
arguments, the `bench-fn` macro is there to make it easy to accept the
arguments from the setup function and just run the code you are
interested in. Note that the function that returns the benchmarkable
function actually returns a vector with the function in it. You can
return a second function in that vector, and that function will be
called after the benchmark is completed to do cleanup.

## An Example

Suppose the project map contains the following keys:

```
:dependencies [[org.clojure/clojure "1.3.0"]
               [perforate "0.1.0-SNAPSHOT"]]
  :plugins [[perforate "0.1.0"]]
  :profiles {:current {:source-paths ["src/"]}
             :clj1.4 {:dependencies [[org.clojure/clojure "1.4.0-beta5"]]}
             :clj1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :version1 {:dependencies [[myproject "1.0.0"]]}
             :version2 {:dependencies [[myproject "2.0.0"]]}}
  :perforate {:environments [{:profiles [:clj1.3 :version1]
                              :namespaces [myproject.benchmarks.core]}
                             {:profiles [:clj1.3 :version2]
                              :namespaces [myproject.benchmarks.core]}
                             {:profiles [:clj1.4 :current]
                              :namespaces [myproject.benchmarks.core]}]}
```

A run could look like this:

```
David$ lein2 perforate
Benchmarking profiles:  [:clj1.3 :version1]
======================
Benchmark:  Test Speed
----------
Benchmark Case: :default
Evaluation count             : 120
             Execution time mean : 793.924842 ms  95.0% CI: (793.842767 ms, 793.975717 ms)
    Execution time std-deviation : 11.865390 ms  95.0% CI: (11.814311 ms, 11.917502 ms)
         Execution time lower ci : 780.498500 ms  95.0% CI: (780.498500 ms, 780.498500 ms)
         Execution time upper ci : 809.372500 ms  95.0% CI: (809.368000 ms, 809.372500 ms)

Benchmarking profiles:  [:clj1.3 :version2]
======================
Benchmark:  Test Speed
----------
Benchmark Case: :default
Evaluation count             : 120
             Execution time mean : 637.975817 ms  95.0% CI: (637.931333 ms, 638.011125 ms)
    Execution time std-deviation : 8.807448 ms  95.0% CI: (8.771608 ms, 8.859762 ms)
         Execution time lower ci : 627.351225 ms  95.0% CI: (627.351225 ms, 627.353000 ms)
         Execution time upper ci : 649.712000 ms  95.0% CI: (649.712000 ms, 649.713975 ms)

Benchmarking profiles:  [:clj1.4 :current]
======================
Benchmark:  Test Speed
----------
Benchmark Case: :default
Evaluation count             : 120
             Execution time mean : 633.556467 ms  95.0% CI: (633.515842 ms, 633.607392 ms)
    Execution time std-deviation : 9.972554 ms  95.0% CI: (9.914351 ms, 10.055073 ms)
         Execution time lower ci : 622.291500 ms  95.0% CI: (622.291500 ms, 622.291500 ms)
         Execution time upper ci : 647.861000 ms  95.0% CI: (647.861000 ms, 647.887350 ms)

Found 2 outliers in 60 samples (3.3333 %)
	low-severe	 2 (3.3333 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
```

## News

* Released version 0.1.1 with some bug fixes.

## License

Copyright © 2012 David Santiago

Distributed under the Eclipse Public License, the same as Clojure.
