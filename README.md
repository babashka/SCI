# Small Clojure Interpreter

[![CircleCI](https://circleci.com/gh/borkdude/sci/tree/master.svg?style=shield)](https://circleci.com/gh/borkdude/sci/tree/master)
[![Clojars Project](https://img.shields.io/clojars/v/borkdude/sci.svg)](https://clojars.org/borkdude/sci)
[![NPM Project](https://img.shields.io/npm/v/@borkdude/sci)](https://www.npmjs.com/package/@borkdude/sci)
[![cljdoc badge](https://cljdoc.org/badge/borkdude/sci)](https://cljdoc.org/d/borkdude/sci/CURRENT)

A tiny implementation of Clojure in Clojure.

## Quickstart

### Use from Clojure(Script)

``` clojure
(require '[sci.core :as sci])
(sci/eval-string "(inc 1)") => ;; 2
(sci/eval-string "(inc x)" {:bindings {'x 2}}) ;;=> 3
```

### Use from JavaScript

``` javascript
> const { evalString } = require('@borkdude/sci');
> const opts = {bindings: {f: function() { console.log('hello'); }}};
> evalString("(dotimes [i 2] (f))", opts);
hello
hello
```

## Rationale

You want to evaluate code from user input, but `eval` isn't safe or simply
doesn't work.

This library works with:

- Clojure on the JVM
- Clojure compiled with GraalVM native
- ClojureScript, even when compiled with `:advanced`, and (as a consequence) JavaScript

It is used as the interpreter for
[babashka](https://github.com/borkdude/babashka).

## Status

Experimental. Breaking changes are expected to happen at this phase.

## Installation

Use as a dependency:

[![Clojars Project](https://img.shields.io/clojars/v/borkdude/sci.svg)](https://clojars.org/borkdude/sci)
[![NPM Project](https://img.shields.io/npm/v/@borkdude/sci)](https://www.npmjs.com/package/@borkdude/sci)

## Usage

Currently the only API function is `sci.core/eval-string` which takes a string
to evaluate and an optional options map.

In `sci`, `defn` does not mutate the outside world, only the evaluation
context inside a call to `sci/eval-string`.

By default `sci` only enables access to the pure non-side-effecting functions in
Clojure.  More functions can be enabled, at your own risk, using `:bindings`:

``` clojure
user=> (require '[sci.core :as sci])
user=> (sci/eval-string "(println \"hello\")" {:bindings {'println println}})
hello
nil
```

It is also possible to provide namespaces which can be required:

``` clojure
user=> (def opts {:namespaces {'lib {'println println}}})
user=> (sci/eval-string "(require '[lib]) (lib/println \"hello\")" opts)
hello
nil
```

## Feature parity

Currently the following special forms/macros are supported: `def`, `fn`,
function literals (`#(inc %)`), `defn`, `quote`, `do`,`if`, `if-let`, `if-not`,
`when`, `when-let`, `when-not`, `cond`, `let`, `and`, `or`, `->`, `->>`, `as->`,
`comment`, `loop`, `lazy-seq`, `for`, `doseq`, `case`, `try/catch/finally`,
`declare`, `cond->`, `cond->>`. It also supports user defined macros.

More examples of what is currently possible can be found at
[babashka](https://github.com/borkdude/babashka).

If you miss something, feel free to post an issue.

## Caveats

To make the `rand-*` functions behave well when compiling to a GraalVM native binary, use this setting:

``` clojure
--initialize-at-run-time=java.lang.Math\$RandomNumberGeneratorHolder
```

## Test

Required: `lein`, the `clojure` CLI and GraalVM.

To succesfully run the GraalVM tests, you will have to compile the binary first
with `script/compile`.

To run all tests:

    script/test/all

For running individual tests, see the scripts in `script/test`.

## License

Copyright © 2019 Michiel Borkent

Distributed under the Eclipse Public License 1.0. This project contains code
from Clojure and ClojureScript which are also licensed under the EPL 1.0. See
LICENSE.
