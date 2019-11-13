(ns sci.impl.analyzer
  {:no-doc true}
  (:refer-clojure :exclude [destructure macroexpand macroexpand-all macroexpand-1])
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [sci.impl.destructure :refer [destructure]]
   [sci.impl.doseq-macro :refer [expand-doseq]]
   [sci.impl.for-macro :refer [expand-for]]
   [sci.impl.utils :refer
    [eval? gensym* mark-resolve-sym mark-eval mark-eval-call constant?
     rethrow-with-location-of-node throw-error-with-location
     merge-meta kw-identical? strip-core-ns]]))

;; derived from (keys (. clojure.lang.Compiler specials))
(def special-syms '#{try finally do if new recur quote catch throw def})
;; built-in macros
(def macros '#{do if when and or -> ->> as-> quote quote* syntax-quote let fn
               fn* def defn comment loop lazy-seq for doseq require cond case
               try defmacro declare})

(defn check-permission! [{:keys [:allow :deny]} check-sym sym]
  (when-not (kw-identical? :allow (-> sym meta :row))
    (when-not (if allow (contains? allow check-sym)
                  true)
      (throw-error-with-location (str sym " is not allowed!") sym))
    (when (if deny (contains? deny check-sym)
              false)
      (throw-error-with-location (str sym " is not allowed!") sym))))

(defn lookup-env [env sym]
  (let [sym-ns (some-> (namespace sym) symbol)
        sym-name (symbol (name sym))
        env @env]
    (or (find env sym) ;; env can contains foo/bar symbols from bindings
        (if sym-ns
          (or (some-> env :namespaces sym-ns (find sym-name))
              (when-let [aliased (some-> env :aliases sym-ns)]
                (when-let [v (some-> env :namespaces aliased (get sym-name))]
                  [(symbol (str aliased) (str sym-name)) v])))
          ;; no sym-ns, this could be a symbol from clojure.core
          (some-> env :namespaces (get 'clojure.core) (find sym-name))))))

(defn lookup [{:keys [:env :bindings :classes] :as ctx} sym]
  (let [[k v :as kv]
        (or
         ;; bindings are not checked for permissions
         (when-let [[k _v]
                    (find bindings sym)]
           ;; never inline a binding at macro time!
           [k (mark-resolve-sym k)])
         (when-let
             [[k _ :as kv]
              (or
               (lookup-env env sym)
               (find classes sym)
               (when (get macros sym)
                 [sym sym])
               (when (= 'recur sym)
                 [sym sym #_(mark-resolve-sym sym)]))]
           (check-permission! ctx k sym)
           kv))]
    ;; (prn 'lookup sym '-> res)
    (if-let [m (meta k)]
      (if (:sci/deref! m)
        ;; the evaluation of this expression has been delayed by
        ;; the caller and now is the time to deref it
        [k @v] kv)
      kv)))

(defn resolve-symbol [ctx sym]
  (let [sym (strip-core-ns sym)
        res (second
             (or
              (lookup ctx sym)
              ;; TODO: check if symbol is in macros and then emit an error: cannot take
              ;; the value of a macro
              (let [n (name sym)]
                (if (str/starts-with? n "'")
                  (let [v (symbol (subs n 1))]
                    [v v])
                  (throw-error-with-location
                   (str "Could not resolve symbol: " (str sym))
                   sym)))))]
    ;; (prn 'resolve expr '-> res)
    res))

(declare analyze)

(defn expand-fn-args+body [ctx fn-name [binding-vector & body-exprs] macro?]
  (let [binding-vector (if macro? (into ['&form '&env] binding-vector)
                           binding-vector)
        fixed-args (take-while #(not= '& %) binding-vector)
        var-arg (second (drop-while #(not= '& %) binding-vector))
        fixed-arity (count fixed-args)
        fixed-names (vec (repeatedly fixed-arity gensym*))
        destructure-vec (vec (interleave binding-vector fixed-names))
        var-arg-name (when var-arg (gensym*))
        destructure-vec (if var-arg
                          (conj destructure-vec var-arg var-arg-name)
                          destructure-vec)
        destructured-vec (destructure destructure-vec)
        ;; all user-provided bindings are extracted by the destructure macro and
        ;; now we add them to bindings and continue the macroexpansion of the
        ;; body
        ctx (update ctx :bindings merge (zipmap (take-nth 2 destructured-vec)
                                                (repeat nil)))
        body-form (mark-eval-call
                   `(~'let ~destructured-vec
                     ~@(doall (map #(analyze ctx %) body-exprs))))
        arg-list (if var-arg
                   (conj fixed-names '& var-arg-name)
                   fixed-names)]
    #:sci.impl{:arg-list arg-list
               :body [body-form]
               :fixed-arity fixed-arity
               :destructure-vec destructure-vec
               :fixed-names fixed-names
               :fixed-args fixed-args
               :var-arg-name var-arg-name
               :fn-name fn-name}))

(defn expand-fn [ctx [_fn name? & body] macro?]
  (let [fn-name (if (symbol? name?)
                  name?
                  nil)
        body (if fn-name
               body
               (cons name? body))
        ;; fn-name (or fn-name (gensym* "fn"))
        bodies (if (seq? (first body))
                 body
                 [body])
        ctx (if fn-name (assoc-in ctx [:bindings fn-name] nil)
                ctx)
        arities (doall (map #(expand-fn-args+body ctx fn-name % macro?) bodies))]
    (mark-eval
     #:sci.impl{:fn-bodies arities
                :fn-name fn-name
                :fn true})))

(defn expand-fn-literal-body [ctx expr]
  (let [fn-body (get-in expr [:sci.impl/fn-bodies 0])
        fixed-names (:sci.impl/fixed-names fn-body)
        var-arg-name (:sci.impl/var-arg-name fn-body)
        bindings (if var-arg-name
                   (conj fixed-names var-arg-name)
                   fixed-names)
        bindings (zipmap bindings (repeat nil))
        ctx (update ctx :bindings merge bindings)]
    ;; expr
    (-> (update-in expr [:sci.impl/fn-bodies 0 :sci.impl/body 0]
                   (fn [expr]
                     (analyze ctx expr)))
        mark-eval)))

(defn expand-let*
  [ctx destructured-let-bindings exprs]
  (let [[ctx new-let-bindings]
        (reduce
         (fn [[ctx new-let-bindings] [binding-name binding-value]]
           (let [v (analyze ctx binding-value)]
             [(update ctx :bindings assoc binding-name v)
              (conj new-let-bindings binding-name v)]))
         [ctx []]
         (partition 2 destructured-let-bindings))]
    (mark-eval-call `(~'let ~new-let-bindings ~@(doall (map #(analyze ctx %) exprs))))))

(defn expand-let
  "The let macro from clojure.core"
  [ctx [_let let-bindings  & exprs]]
  (let [let-bindings (destructure let-bindings)]
    (expand-let* ctx let-bindings exprs)))

(defn expand->
  "The -> macro from clojure.core."
  [ctx [x & forms]]
  (let [expanded
        (loop [x x, forms forms]
          (if forms
            (let [form (first forms)
                  threaded (if (seq? form)
                             (with-meta (concat (list (first form) x)
                                                (next form))
                               (meta form))
                             (list form x))]
              (recur threaded (next forms))) x))]
    (analyze ctx expanded)))

(defn expand->>
  "The ->> macro from clojure.core."
  [ctx [x & forms]]
  (let [expanded
        (loop [x x, forms forms]
          (if forms
            (let [form (first forms)
                  threaded (if (seq? form)
                             (with-meta
                               (concat (cons (first form) (next form))
                                       (list x))
                               (meta form))
                             (list form x))]
              (recur threaded (next forms))) x))]
    (analyze ctx expanded)))

(defn expand-as->
  "The ->> macro from clojure.core."
  [ctx [_as expr name & forms]]
  (let [[let-bindings & body] `([~name ~expr
                                 ~@(interleave (repeat name) (butlast forms))]
                                ~(if (empty? forms)
                                   name
                                   (last forms)))]
    (expand-let* ctx let-bindings body)))

(defn expand-def
  [ctx [_def var-name ?docstring ?init]]
  (let [docstring (when ?init ?docstring)
        init (if docstring ?init ?docstring)
        init (analyze ctx init)
        m (if docstring {:sci/doc docstring} {})
        var-name (with-meta var-name m)]
    (swap! (:env ctx) assoc var-name :sci/var.unbound)
    (mark-eval-call (list 'def var-name init))))

(declare expand-declare)

(defn expand-defn [ctx [op fn-name docstring? & body]]
  (expand-declare ctx [nil fn-name])
  (let [macro? (= 'defmacro op)
        docstring (when (string? docstring?) docstring?)
        body (if docstring body (cons docstring? body))
        fn-body (list* 'fn #_fn-name body)
        f (expand-fn ctx fn-body macro?)
        f (assoc f :sci/macro macro?
                 :sci.impl/fn-name fn-name)]
    (mark-eval-call (list 'def fn-name f))))

(defn expand-comment
  "The comment macro from clojure.core."
  [_ctx & _body])

(defn expand-loop
  [ctx expr]
  (let [bv (second expr)
        arg-names (take-nth 2 bv)
        init-vals (take-nth 2 (rest bv))
        body (nnext expr)]
    (analyze ctx (apply list (list 'fn (vec arg-names)
                                       (cons 'do body))
                            init-vals))))

(defn expand-lazy-seq
  [ctx expr]
  (let [body (rest expr)]
    (mark-eval-call
     (list 'lazy-seq
           (analyze ctx (list 'fn [] (cons 'do body)))))))

(defn expand-cond*
  "The cond macro from clojure.core"
  [& clauses]
  (when clauses
    (list 'if (first clauses)
          (if (next clauses)
            (second clauses)
            (throw (new #?(:clj IllegalArgumentException
                           :cljs js/Error)
                        "cond requires an even number of forms")))
          (apply expand-cond* (next (next clauses))))))

(defn expand-cond
  [ctx expr]
  (let [clauses (rest expr)]
    (analyze ctx (apply expand-cond* clauses))))

(defn expand-case
  [ctx expr]
  (let [v (analyze ctx (second expr))
        clauses (nnext expr)
        match-clauses (take-nth 2 clauses)
        result-clauses (map #(analyze ctx %) (take-nth 2 (rest clauses)))
        default (when (odd? (count clauses))
                  [:val (analyze ctx (last clauses))])
        case-map (zipmap match-clauses result-clauses)
        ret (mark-eval-call (list 'case
                                  {:case-map case-map
                                   :case-val v
                                   :case-default default}
                                  default))]
    (mark-eval-call ret)))

(defn expand-try
  [ctx expr]
  (let [catches (filter #(and (seq? %) (= 'catch (first %))) expr)
        catches (mapv (fn [c]
                        (let [[_ ex binding & body] c
                              clazz (resolve-symbol ctx ex)]
                          {:class clazz
                           :binding binding
                           :body (analyze (assoc-in ctx [:bindings binding] nil)
                                              (cons 'do body))}))
                      catches)
        finally (let [l (last expr)]
                  (when (= 'finally (first l))
                    (analyze ctx (cons 'do (rest l)))))]
    (mark-eval
     {:sci.impl/try
      {:body (analyze ctx (second expr))
       :catches catches
       :finally finally}})))

(defn expand-syntax-quote [ctx expr]
  (let [ret (walk/prewalk
             (fn [x]
               (if (seq? x)
                 (case (first x)
                   unquote (analyze ctx (second x))
                   unquote-splicing (vary-meta
                                     (analyze ctx (second x))
                                     (fn [m]
                                       (assoc m :sci.impl/unquote-splicing true)))
                   x)
                 x))
             (second expr))]
    (mark-eval-call (list 'syntax-quote ret))))

(defn expand-declare [ctx [_declare & names :as _expr]]
  (swap! (:env ctx)
         (fn [env]
           ;; declaring an already existing var does nothing
           ;; that's why env is the last arg to merge, not the first
           (merge (zipmap names
                          (map (fn [n]
                                 (vary-meta (mark-eval n)
                                            #(assoc % :sci.impl/var.declared true)))
                               names))
                  env)))
  nil)

(defn macro? [f]
  (when-let [m (meta f)]
    (:sci/macro m)))

(defn analyze-call [ctx expr]
  (if (empty? expr) expr
      (let [f (first expr)]
        (if (symbol? f)
          (let [f (or (get special-syms f) ;; in call position Clojure
                                           ;; prioritizes special symbols over
                                           ;; bindings
                      (resolve-symbol ctx f))]
            (if (and (not (eval? f)) ;; the symbol is not a binding
                     (contains? macros f))
              (case f
                do (mark-eval-call expr) ;; do will call macroexpand on every
                ;; subsequent expression
                let (expand-let ctx expr)
                (fn fn*) (expand-fn ctx expr false)
                def (expand-def ctx expr)
                (defn defmacro) (let [ret (expand-defn ctx expr)]
                                  ret)
                -> (expand-> ctx (rest expr))
                ->> (expand->> ctx (rest expr))
                as-> (expand-as-> ctx expr)
                quote (do nil #_(prn "quote" expr) (second expr))
                syntax-quote (expand-syntax-quote ctx expr)
                comment (expand-comment ctx expr)
                loop (expand-loop ctx expr)
                lazy-seq (expand-lazy-seq ctx expr)
                for (analyze ctx (expand-for ctx expr))
                doseq (analyze ctx (expand-doseq ctx expr))
                require (mark-eval-call
                         (cons 'require (map #(analyze ctx %)
                                             (rest expr))))
                cond (expand-cond ctx expr)
                case (expand-case ctx expr)
                try (expand-try ctx expr)
                declare (expand-declare ctx expr)
                ;; else:
                (mark-eval-call (doall (map #(analyze ctx %) expr))))
              (try (if (macro? f)
                     (let [v (apply f expr
                                    (:bindings ctx) (rest expr))
                           expanded (analyze ctx v)]
                       expanded)
                     (mark-eval-call (doall (map #(analyze ctx %) expr))))
                   (catch #?(:clj Exception :cljs js/Error) e
                     (rethrow-with-location-of-node ctx e expr)))))
          (let [ret (mark-eval-call (doall (map #(analyze ctx %) expr)))]
            ret)))))

(defn analyze
  [ctx expr]
  (let [ret (cond (constant? expr) expr ;; constants do not carry metadata
                  (symbol? expr) (let [v (resolve-symbol ctx expr)]
                                   (cond (kw-identical? :sci/var.unbound v) nil
                                         (constant? v) v
                                         (fn? v) (merge-meta v {:sci.impl/eval false})
                                         :else (merge-meta v (meta expr))))
                  :else
                  (merge-meta
                   (cond
                     ;; already expanded by reader
                     (:sci.impl/fn expr) (expand-fn-literal-body ctx expr)
                     (map? expr)
                     (-> (zipmap (map #(analyze ctx %) (keys expr))
                                 (map #(analyze ctx %) (vals expr)))
                         mark-eval)
                     (or (vector? expr) (set? expr))
                     (-> (into (empty expr) (map #(analyze ctx %) expr))
                         mark-eval)
                     (seq? expr) (analyze-call ctx expr)
                     :else expr)
                   (select-keys (meta expr)
                                [:row :col])))]
    ret))

;;;; Scratch

(comment
  )
