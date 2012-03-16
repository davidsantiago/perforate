# perforate

Painless benchmarking with Leiningen.

## Usage

Perforate is a plugin for Leiningen 2 that makes it easy to write and
run benchmarks, much like the `test` task built into Leiningen.

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

## License

Copyright © 2012 David Santiago

Distributed under the Eclipse Public License, the same as Clojure.
