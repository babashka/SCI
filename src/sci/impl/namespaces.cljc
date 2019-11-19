(ns sci.impl.namespaces
  {:no-doc true}
  (:refer-clojure :exclude [ex-message])
  (:require
   [clojure.string :as str]
   [clojure.set :as set]))

(defn macrofy [f]
  (vary-meta f #(assoc % :sci/macro true)))

(defn dotimes*
  "dotimes from clojure.core"
  [[i n] & body]
  `(let [n# (long ~n)]
     (loop [~i 0]
       (when (< ~i n#)
         ~@body
         (recur (unchecked-inc ~i))))))

(defn if-not*
  "if-not from clojure.core"
  ([&form &env test then] (if-not* &form &env test then nil))
  ([_&form _&env test then else]
   `(if (not ~test) ~then ~else)))

(defn when-not*
  "when-not from clojure.core"
  [_&form _&env test & body]
  (list 'if test nil (cons 'do body)))

(defn doto*
  "doto from clojure.core"
  [_&form _&env x & forms]
  (let [gx (gensym)]
    `(let [~gx ~x]
       ~@(map (fn [f]
                (with-meta
                  (if (seq? f)
                    `(~(first f) ~gx ~@(next f))
                    `(~f ~gx))
                  (meta f)))
              forms)
       ~gx)))

(defn cond->*
  [_&form _&env expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        steps (map (fn [[test step]] `(if ~test (-> ~g ~step) ~g))
                   (partition 2 clauses))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defn cond->>*
  [_&form _&env expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        steps (map (fn [[test step]] `(if ~test (->> ~g ~step) ~g))
                   (partition 2 clauses))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defn if-let*
  ([&form &env bindings then]
   (if-let* &form &env bindings then nil))
  ([_&form _&env bindings then else & _oldform]
   (let [form (bindings 0) tst (bindings 1)]
     `(let [temp# ~tst]
        (if temp#
          (let [~form temp#]
            ~then)
          ~else)))))

(defn when-let*
  [_&form _&env bindings & body]
  (let [form (bindings 0) tst (bindings 1)]
    `(let [temp# ~tst]
       (when temp#
         (let [~form temp#]
           ~@body)))))

(defn when-first* [_ _ bindings & body]
  (let [[x xs] bindings]
    `(when-let [xs# (seq ~xs)]
       (let [~x (first xs#)]
         ~@body))))

(def ex-message
  (if-let [v (resolve 'clojure.core/ex-message)]
    @v
    (fn ex-message [ex]
      (when (instance? #?(:clj Throwable :cljs js/Error) ex)
        #?(:clj (.getMessage ^Throwable ex)
           :cljs (.-message ex))))))

(def clojure-core
  {'= =
   '< <
   '<= <=
   '> >
   '>= >=
   '+ +
   '- -
   '* *
   '/ /
   '== ==
   'add-watch add-watch
   'aget aget
   'aset aset
   'alength alength
   'apply apply
   'array-map array-map
   'assoc assoc
   'assoc-in assoc-in
   'associative? associative?
   'atom atom
   'bit-and-not bit-and-not
   'bit-set bit-set
   'bit-shift-left bit-shift-left
   'bit-shift-right bit-shift-right
   'bit-xor bit-xor
   'boolean boolean
   'boolean? boolean?
   'booleans booleans
   'butlast butlast
   'bytes bytes
   'bit-test bit-test
   'bit-and bit-and
   'bounded-count bounded-count
   'bit-or bit-or
   'bit-flip bit-flip
   'bit-not bit-not
   'byte byte
   'cat cat
   'char char
   'char? char?
   #?@(:cljs ['clj->js clj->js])
   'cond-> (macrofy cond->*)
   'cond->> (macrofy cond->>*)
   'conj conj
   'cons cons
   'contains? contains?
   'count count
   'cycle cycle
   'comp comp
   'concat concat
   'comparator comparator
   'coll? coll?
   'compare compare
   'complement complement
   'constantly constantly
   'chars chars
   'completing completing
   'counted? counted?
   'chunk chunk
   'chunk-append chunk-append
   'chunk-buffer chunk-buffer
   'chunk-cons chunk-cons
   'chunk-first chunk-first
   'chunk-rest chunk-rest
   'chunk-next chunk-next
   'chunked-seq? chunked-seq?
   'dec dec
   'dedupe dedupe
   'deref deref
   'dissoc dissoc
   'distinct distinct
   'distinct? distinct?
   'disj disj
   'doall doall
   'dorun dorun
   'dotimes (macrofy dotimes*)
   'doto (macrofy doto*)
   'double double
   'double? double?
   'drop drop
   'drop-last drop-last
   'drop-while drop-while
   'doubles doubles
   'eduction eduction
   'empty empty
   'empty? empty?
   'even? even?
   'every? every?
   'every-pred every-pred
   'ensure-reduced ensure-reduced
   'ex-data ex-data
   'ex-info ex-info
   'ex-message ex-message
   'first first
   'float? float?
   'floats floats
   'fnil fnil
   'fnext fnext
   'ffirst ffirst
   'flatten flatten
   'false? false?
   'filter filter
   'filterv filterv
   'find find
   'frequencies frequencies
   'float float
   'fn? fn?
   'get get
   'get-in get-in
   'group-by group-by
   'gensym gensym
   'hash hash
   'hash-map hash-map
   'hash-set hash-set
   'hash-unordered-coll hash-unordered-coll
   'ident? ident?
   'identical? identical?
   'identity identity
   'if-let (macrofy if-let*)
   'if-not (macrofy if-not*)
   'inc inc
   'int-array int-array
   'interleave interleave
   'into into
   'iterate iterate
   'int int
   'int? int?
   'interpose interpose
   'indexed? indexed?
   'integer? integer?
   'ints ints
   'into-array into-array
   #?@(:cljs ['js->clj js->clj])
   #?@(:cljs ['js-obj js-obj])
   'juxt juxt
   'keep keep
   'keep-indexed keep-indexed
   'key key
   'keys keys
   'keyword keyword
   'keyword? keyword?
   'last last
   'long long
   'list list
   'list? list?
   'longs longs
   'list* list*
   'long-array long-array
   'map map
   'map? map?
   'map-indexed map-indexed
   'map-entry? map-entry?
   'mapv mapv
   'mapcat mapcat
   'max max
   'max-key max-key
   'meta meta
   'merge merge
   'merge-with merge-with
   'min min
   'min-key min-key
   'munge munge
   'mod mod
   'make-array make-array
   'name name
   'namespace namespace
   'newline newline
   'nfirst nfirst
   'not not
   'not= not=
   'not-every? not-every?
   'neg? neg?
   'neg-int? neg-int?
   'nth nth
   'nthnext nthnext
   'nthrest nthrest
   'nil? nil?
   'nat-int? nat-int?
   'number? number?
   'not-empty not-empty
   'not-any? not-any?
   'next next
   'nnext nnext
   'odd? odd?
   'object-array object-array
   'peek peek
   'pop pop
   'pos? pos?
   'pos-int? pos-int?
   'partial partial
   'partition partition
   'partition-all partition-all
   'partition-by partition-by
   'pr-str pr-str
   'prn-str prn-str
   'print-str print-str
   'qualified-ident? qualified-ident?
   'qualified-symbol? qualified-symbol?
   'qualified-keyword? qualified-keyword?
   'quot quot
   're-seq re-seq
   're-find re-find
   're-pattern re-pattern
   're-matches re-matches
   'rem rem
   'remove remove
   'rest rest
   'repeatedly repeatedly
   'reverse reverse
   'rand-int rand-int
   'rand-nth rand-nth
   'range range
   'reduce reduce
   'reduce-kv reduce-kv
   'reduced reduced
   'reduced? reduced?
   'reset! reset!
   'reversible? reversible?
   'rsubseq rsubseq
   'reductions reductions
   'rand rand
   'replace replace
   'rseq rseq
   'random-sample random-sample
   'repeat repeat
   'run! run!
   'set? set?
   'sequential? sequential?
   'select-keys select-keys
   'simple-keyword? simple-keyword?
   'simple-symbol? simple-symbol?
   'some? some?
   'string? string?
   'str str
   'second second
   'set set
   'seq seq
   'seq? seq?
   'short short
   'shuffle shuffle
   'sort sort
   'sort-by sort-by
   'subs subs
   'symbol symbol
   'symbol? symbol?
   'special-symbol? special-symbol?
   'subvec subvec
   'some-fn some-fn
   'some some
   'split-at split-at
   'split-with split-with
   'sorted-set sorted-set
   'subseq subseq
   'sorted-set-by sorted-set-by
   'sorted-map-by sorted-map-by
   'sorted-map sorted-map
   'sorted? sorted?
   'simple-ident? simple-ident?
   'sequence sequence
   'seqable? seqable?
   'shorts shorts
   'swap! swap!
   'swap-vals! swap-vals!
   'take take
   'take-last take-last
   'take-nth take-nth
   'take-while take-while
   ;; 'throw throw*
   'trampoline trampoline
   'transduce transduce
   'tree-seq tree-seq
   'type type
   'true? true?
   'to-array to-array
   'update update
   'update-in update-in
   'uri? uri?
   'uuid? uuid?
   'unchecked-inc-int unchecked-inc-int
   'unchecked-long unchecked-long
   'unchecked-negate unchecked-negate
   'unchecked-remainder-int unchecked-remainder-int
   'unchecked-subtract-int unchecked-subtract-int
   'unsigned-bit-shift-right unsigned-bit-shift-right
   'unchecked-float unchecked-float
   'unchecked-add-int unchecked-add-int
   'unchecked-double unchecked-double
   'unchecked-multiply-int unchecked-multiply-int
   'unchecked-int unchecked-int
   'unchecked-multiply unchecked-multiply
   'unchecked-dec-int unchecked-dec-int
   'unchecked-add unchecked-add
   'unreduced unreduced
   'unchecked-divide-int unchecked-divide-int
   'unchecked-subtract unchecked-subtract
   'unchecked-negate-int unchecked-negate-int
   'unchecked-inc unchecked-inc
   'unchecked-char unchecked-char
   'unchecked-byte unchecked-byte
   'unchecked-short unchecked-short
   'val val
   'vals vals
   'vary-meta vary-meta
   'vec vec
   'vector vector
   'vector? vector?
   'when-first (macrofy when-first*)
   'when-let (macrofy when-let*)
   'when-not (macrofy when-not*)
   'with-meta with-meta
   'zipmap zipmap
   'zero? zero?
   #?@(:clj ['+' +'
             '-' -'
             '*' *'
             'boolean-array boolean-array
             'bound? bound?
             'byte-array byte-array
             'bigint bigint
             'bytes? bytes?
             'biginteger biginteger
             'bigdec bigdec
             'char-array char-array
             'char-escape-string char-escape-string
             'char-name-string char-name-string
             'class class
             'dec' dec'
             'decimal? decimal?
             'denominator denominator
             'format format
             'float-array float-array
             'inc' inc'
             'line-seq line-seq
             'num num
             'namespace-munge namespace-munge
             'numerator numerator
             'replicate replicate
             'rational? rational?
             'ratio? ratio?
             'rationalize rationalize
             'seque seque
             'xml-seq xml-seq])})

(def namespaces
  {'clojure.core clojure-core
   'clojure.string {'blank? str/blank?
                    'capitalize str/capitalize
                    'ends-with? str/ends-with?
                    'escape str/escape
                    'includes? str/includes?
                    'index-of str/index-of
                    'join str/join
                    'last-index-of str/last-index-of
                    'lower-case str/lower-case
                    'replace str/replace
                    'replace-first str/replace-first
                    'reverse str/reverse
                    'split str/split
                    'split-lines str/split-lines
                    'starts-with? str/starts-with?
                    'trim str/trim
                    'trim-newline str/trim-newline
                    'triml str/triml
                    'trimr str/trimr
                    'upper-case str/upper-case
                    #?@(:clj ['str/re-quote-replacement str/re-quote-replacement])}
   'clojure.set {'difference set/difference
                 'index set/index
                 'intersection set/intersection
                 'join set/join
                 'map-invert set/map-invert
                 'project set/project
                 'rename set/rename
                 'rename-keys set/rename-keys
                 'select set/select
                 'subset? set/subset?
                 'superset? set/superset?
                 'union set/union}})

(def aliases
  '{str clojure.string
    set clojure.set})
