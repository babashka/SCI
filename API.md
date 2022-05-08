# Table of contents
-  sci.async
  -  eval-string*
  -  last-ns
  -  handle-libspecs
  -  eval-ns-form
  -  eval-string*
-  sci.core
  -  new-var
  -  new-dynamic-var
  -  set!
  -  new-macro-var
  -  copy-var
  -  with-bindings
  -  binding
  -  in
  -  out
  -  err
  -  ns
  -  file
  -  read-eval
  -  print-length
  -  print-level
  -  print-meta
  -  print-readably
  -  print-dup
  -  assert
  -  *1
  -  *2
  -  *3
  -  *e
  -  with-in-str
  -  with-out-str
  -  future
  -  pmap
  -  alter-var-root
  -  intern
  -  eval-string
  -  init
  -  merge-opts
  -  fork
  -  eval-string*
  -  create-ns
  -  parse-string
  -  reader
  -  get-line-number
  -  get-column-number
  -  parse-next
  -  eval-form
  -  stacktrace
  -  format-stacktrace
  -  ns-name
  -  -copy-ns
  -  process-publics
  -  exclude-when-meta
  -  meta-fn
  -  cljs-ns-publics
  -  cljs-ns-publics
  -  require-cljs-analyzer-api
  -  copy-ns
  -  add-class!
  -  add-import!
  -  find-ns
  -  all-ns
  -  new-var
  -  new-dynamic-var
  -  set!
  -  new-macro-var
  -  copy-var
  -  with-bindings
  -  binding
  -  in
  -  out
  -  err
  -  ns
  -  file
  -  read-eval
  -  print-length
  -  print-level
  -  print-meta
  -  print-readably
  -  print-dup
  -  print-fn
  -  print-err-fn
  -  print-newline
  -  assert
  -  *1
  -  *2
  -  *3
  -  *e
  -  with-in-str
  -  with-out-str
  -  future
  -  alter-var-root
  -  intern
  -  eval-string
  -  init
  -  merge-opts
  -  fork
  -  eval-string*
  -  create-ns
  -  parse-string
  -  reader
  -  get-line-number
  -  get-column-number
  -  parse-next
  -  eval-form
  -  stacktrace
  -  format-stacktrace
  -  ns-name
  -  -copy-ns
  -  process-publics
  -  exclude-when-meta
  -  meta-fn
  -  require-cljs-analyzer-api
  -  copy-ns
  -  add-class!
  -  add-import!
  -  find-ns
  -  all-ns
## sci.async 

### `eval-string*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/async.cljs#L8-L8)
<hr>
## sci.core 

### `*1`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L113-L113)
### `*2`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L114-L114)
### `*3`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L115-L115)
### `*e`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L116-L116)
### `add-class!`
``` clojure

(add-class! [ctx class-name class])
```


Adds class (JVM class or JS object) to `ctx` as `class-name` (a
  symbol). Returns mutated context.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L440-L450)
### `add-import!`
``` clojure

(add-import! [ctx ns-name class-name alias])
```


Adds import of class named by `class-name` (a symbol) to namespace named by `ns-name` (a symbol) under alias `alias` (a symbol). Returns mutated context.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L452-L457)
### `all-ns`
``` clojure

(all-ns [ctx])
```


Returns all SCI ns objects in the `ctx`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L464-L467)
### `alter-var-root`
``` clojure

(alter-var-root [v f])
(alter-var-root [v f & args])
```


Atomically alters the root binding of sci var v by applying f to its
  current value plus any args.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L177-L183)
### `assert`

SCI var that represents SCI's clojure.core/*assert*

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L111-L111)
### `binding`
``` clojure

(binding [bindings & body])
```


Macro.


Macro for binding sci vars. Must be called with a vector of sci
  dynamic vars to values.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L87-L94)
### `cljs-ns-publics`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L341-L341)
### `copy-ns`
``` clojure

(copy-ns [ns-sym sci-ns])
(copy-ns [ns-sym sci-ns opts])
```


Macro.


Returns map of names to SCI vars as a result of copying public
  Clojure vars from ns-sym (a symbol). Attaches sci-ns (result of
  sci/create-ns) to meta. Copies :name, :macro :doc, :no-doc
  and :argslists metadata.

  Options:

  - :exclude: a seqable of names to exclude from the
  namespace. Defaults to none.

  - :copy-meta: a seqable of keywords to copy from the original var
  meta.  Use :all instead of a seqable to copy all. Defaults
  to [:doc :arglists :macro].

  - :exclude-when-meta: seqable of keywords; vars with meta matching
  these keys are excluded.  Defaults to [:no-doc :skip-wiki]

  The selection of vars is done at compile time which is mostly
  important for ClojureScript to not pull in vars into the compiled
  JS. Any additional vars can be added after the fact with sci/copy-var
  manually.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L358-L438)
### `copy-var`
``` clojure

(copy-var [sym ns])
(copy-var [sym ns opts])
```


Macro.


Copies contents from var `sym` to a new sci var. The value `ns` is an
  object created with `sci.core/create-ns`. If new-name is supplied, the 
  copied var will be named new-name.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L52-L73)
### `create-ns`
``` clojure

(create-ns [sym])
(create-ns [sym meta])
```


Creates namespace object. Can be used in var metadata.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L249-L253)
### `err`

SCI var that represents SCI's `clojure.core/*err*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L99-L99)
### `eval-form`
``` clojure

(eval-form [ctx form])
```


Evaluates form (as produced by `parse-string` or `parse-next`) in the
  context of `ctx` (as produced with `init`). To allow namespace
  switches, establish root binding of `sci/ns` with `sci/binding` or
  `sci/with-bindings.`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L283-L290)
### `eval-string`
``` clojure

(eval-string [s])
(eval-string [s opts])
```


Evaluates string `s` as one or multiple Clojure expressions using the Small Clojure Interpreter.

  The map `opts` may contain the following:

  - `:namespaces`: a map of symbols to namespaces, where a namespace
  is a map with symbols to values, e.g.: `{'foo.bar {'x 1}}`. These
  namespaces can be used with `require`.

  - `:bindings`: `:bindings x` is the same as `:namespaces {'user x}`.

  - `:allow`: a seqable of allowed symbols. All symbols, even those
  brought in via `:bindings` or `:namespaces` have to be explicitly
  enumerated.

  - `:deny`: a seqable of disallowed symbols, e.g.: `[loop quote
  recur]`.

  - `:features`: when provided a non-empty set of keywords, sci will process reader conditionals using these features (e.g. #{:bb}).

  - `:env`: an atom with a map in which state from the
  evaluation (defined namespaced and vars) will be persisted for
  re-use over multiple calls.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L196-L221)
### `eval-string*`
``` clojure

(eval-string* [ctx s])
```


Evaluates string `s` in the context of `ctx` (as produced with
  `init`).

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L243-L247)
### `file`

SCI var that represents SCI's `clojure.core/*file*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L101-L101)
### `find-ns`
``` clojure

(find-ns [ctx ns-sym])
```


Returns SCI ns object as created with `sci/create-ns` from `ctx` found by `ns-sym`.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L459-L462)
### `fork`
``` clojure

(fork [ctx])
```


Forks a context (as produced with `init`) into a new context. Any new
  vars created in the new context won't be visible in the original
  context.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L236-L241)
### `format-stacktrace`
``` clojure

(format-stacktrace [stacktrace])
```


Returns a list of formatted stack trace elements as strings from stacktrace.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L297-L300)
### `future`
``` clojure

(future [& body])
```


Macro.


Like clojure.core/future but also conveys sci bindings to the thread.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L151-L156)
### `get-column-number`
``` clojure

(get-column-number [reader])
```


[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L270-L271)
### `get-line-number`
``` clojure

(get-line-number [reader])
```


[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L267-L268)
### `in`

SCI var that represents SCI's `clojure.core/*in*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L97-L97)
### `init`
``` clojure

(init [opts])
```


Creates an initial sci context from given options `opts`. The context
  can be used with `eval-string*`. See `eval-string` for available
  options. The internal organization of the context is implementation
  detail and may change in the future.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L223-L229)
### `intern`
``` clojure

(intern [ctx sci-ns name])
(intern [ctx sci-ns name val])
```


Finds or creates a sci var named by the symbol name in the namespace
  ns (which can be a symbol or a sci namespace), setting its root
  binding to val if supplied. The namespace must exist in the ctx. The
  sci var will adopt any metadata from the name symbol.  Returns the
  sci var.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L185-L194)
### `merge-opts`
``` clojure

(merge-opts [ctx opts])
```


Updates a context with opts merged in and returns it.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L231-L234)
### `new-dynamic-var`
``` clojure

(new-dynamic-var [name])
(new-dynamic-var [name init-val])
(new-dynamic-var [name init-val meta])
```


Same as new-var but adds :dynamic true to meta.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L31-L36)
### `new-macro-var`
``` clojure

(new-macro-var [name init-val])
(new-macro-var [name init-val meta])
```


Same as new-var but adds :macro true to meta as well
  as :sci/macro true to meta of the fn itself.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L43-L50)
### `new-var`
``` clojure

(new-var [name])
(new-var [name init-val])
(new-var [name init-val meta])
```


Returns a new sci var.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L24-L29)
### `ns`

SCI var that represents SCI's `clojure.core/*ns*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L100-L100)
### `ns-name`
``` clojure

(ns-name [sci-ns])
```


Returns name of SCI ns as symbol.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L302-L305)
### `out`

SCI var that represents SCI's `clojure.core/*out*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L98-L98)
### `parse-next`
``` clojure

(parse-next [ctx reader])
(parse-next [ctx reader opts])
```


Parses next form from reader

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L273-L281)
### `parse-string`
``` clojure

(parse-string [ctx s])
```


Parses string `s` in the context of `ctx` (as produced with
  `init`).

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L255-L259)
### `pmap`
``` clojure

(pmap [f coll])
(pmap [f coll & colls])
```


Like clojure.core/pmap but also conveys sci bindings to the threads.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L158-L175)
### `print-dup`

SCI var that represents SCI's `clojure.core/*print-dup*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L107-L107)
### `print-err-fn`

SCI var that represents SCI's `cljs.core/*print-err-fn*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L109-L109)
### `print-fn`

SCI var that represents SCI's `cljs.core/*print-fn*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L108-L108)
### `print-length`

SCI var that represents SCI's `clojure.core/*print-length*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L103-L103)
### `print-level`

SCI var that represents SCI's `clojure.core/*print-level*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L104-L104)
### `print-meta`

SCI var that represents SCI's `clojure.core/*print-meta*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L105-L105)
### `print-newline`

SCI var that represents SCI's `cljs.core/*print-newline*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L110-L110)
### `print-readably`

SCI var that represents SCI's `clojure.core/*print-readably*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L106-L106)
### `read-eval`

SCI var that represents SCI's `clojure.core/*read-eval*`

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L102-L102)
### `reader`
``` clojure

(reader [x])
```


Coerces x into indexing pushback-reader to be used with
  parse-next. Accepts: string or java.io.Reader.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L261-L265)
### `set!`
``` clojure

(set! [dynamic-var v])
```


Establish thread local binding of dynamic var

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L38-L41)
### `stacktrace`
``` clojure

(stacktrace [ex])
```


Returns list of stacktrace element maps from exception, if available.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L292-L295)
### `with-bindings`
``` clojure

(with-bindings [bindings-map & body])
```


Macro.


Macro for binding sci vars. Must be called with map of sci dynamic
  vars to values. Used in babashka.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L76-L85)
### `with-in-str`
``` clojure

(with-in-str [s & body])
```


Macro.


Evaluates body in a context in which sci's *in* is bound to a fresh
  StringReader initialized with the string s.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L122-L129)
### `with-out-str`
``` clojure

(with-out-str [& body])
```


Macro.


Evaluates exprs in a context in which sci's *out* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls.

[Source](https://github.com/babashka/sci/blob/master/src/sci/core.cljc#L132-L148)
<hr>
