(ns sci.core
  (:refer-clojure :exclude [*in* *out* *err* with-bindings with-in-str with-out-str])
  (:require
   [sci.impl.interpreter :as i]
   [sci.impl.vars :as vars]
   [sci.impl.io :as sio]
   [sci.impl.macros :as macros])
  #?(:cljs (:require-macros [sci.core :refer [with-bindings with-out-str]])))

#?(:clj (set! *warn-on-reflection* true))

(defn new-var
  "Returns a new sci var."
  ([name] (new-var name nil nil))
  ([name val] (new-var name val (meta name)))
  ([name init-val meta] (sci.impl.vars.SciVar. init-val name meta)))

(defn new-dynamic-var
  "Same as new-var but adds :dynamic true to meta."
  ([name] (new-dynamic-var name nil nil))
  ([name init-val] (new-dynamic-var name init-val nil ))
  ([name init-val meta] (sci.impl.vars.SciVar. init-val name (assoc meta :dynamic true))))

(macros/deftime
  (defmacro with-bindings
    "Macro for binding sci vars. Must be called with map of sci dynamic
  vars to values. Used in babashka."
    [bindings & body]
    `(try (vars/push-thread-bindings ~bindings)
          (do ~@body)
          (finally (vars/pop-thread-bindings)))))

;; `*in*`, `*out*`, `*err*` are set to :dynamic to suppress a compiler warning
;; they are really not dynamic to sci library users, but represent dynamic vars
;; *inside* sci
(def ^:dynamic *in* "Sci var that represents sci's clojure.core/*in*" sio/in)
#?(:clj (.setDynamic #'*in* false))
(alter-meta! #'*in* assoc :dynamic false)

(def ^:dynamic *out* "Sci var that represents sci's clojure.core/*out*" sio/out)
#?(:clj (.setDynamic #'*out* false))
(alter-meta! #'*out* assoc :dynamic false)

(def ^:dynamic *err* "Sci var that represents sci's clojure.core/*err*" sio/err)
#?(:clj (.setDynamic #'*err* false))
(alter-meta! #'*err* assoc :dynamic false)

(macros/deftime
  (defmacro with-in-str
    [s & body]
    `(let [in# (-> (java.io.StringReader. ~s)
                   (clojure.lang.LineNumberingPushbackReader.))]
       (with-bindings {*in* in#}
         (do ~@body)))))

(macros/deftime
  (defmacro with-out-str
    [& body]
    `(let [out# (macros/? :clj (java.io.StringWriter.)
                          :cljs (goog.string/StringBuffer.))]
       (with-bindings {*out* out#}
         (do ~@body)
         (str out#)))))

(defn eval-string
  "Evaluates string `s` as one or multiple Clojure expressions using the Small Clojure Interpreter.

  The map `opts` may contain the following:

  - `:bindings`: a map of symbols to values, e.g.: `{'x 1}`. The
  symbols will acts as names bound to the corresponding values in the
  expressions.

  - `:namespaces`: a map of symbols to namespaces, where a namespace
  is a map with symbols to values, e.g.: `{'foo.bar {'x 1}}`. These
  namespaces can be used with `require`.

  - `:in`, `:out` and `:err`: the root bindings of sci's
  `clojure.core/*in*`, `clojure.core/*out*` and `clojure.core/*err*`.

  - `:allow`: a seqable of allowed symbols. All symbols, even those
  brought in via `:bindings` or `:namespaces` have to be explicitly
  enumerated.

  - `:deny`: a seqable of disallowed symbols, e.g.: `[loop quote
  recur]`.

  - `:realize-max`: integer; when provided, program may realize a
  maximum number of elements from sequences, e.g. `(vec (range))` will
  throw for any number. This also applies to sequences returned from
  the expression to the caller.

  - `:preset`: a pretermined set of options. Currently only
  `:termination-safe` is supported, which will set `:realize-max` to
  `100` and disallows the symbols `loop`, `recur` and `trampoline`.

  - `:features`: when provided a non-empty set of keywords, sci will process reader conditionals using these features (e.g. #{:bb})."
  ([s] (eval-string s nil))
  ([s opts]
   (i/eval-string s opts)))

;;;; Scratch

(comment
  (eval-string "(inc x)" {:bindings {'x 2}})
  )
