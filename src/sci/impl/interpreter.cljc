(ns sci.impl.interpreter
  {:no-doc true}
  (:refer-clojure :exclude [destructure macroexpand macroexpand-1])
  (:require
   [clojure.tools.reader.reader-types :as r]
   [sci.impl.analyzer :as ana]
   [sci.impl.fns :as fns]
   [sci.impl.interop :as interop]
   [sci.impl.max-or-throw :refer [max-or-throw]]
   [sci.impl.opts :as opts]
   [sci.impl.parser :as p]
   [sci.impl.utils :as utils :refer [throw-error-with-location
                                     rethrow-with-location-of-node
                                     set-namespace!
                                     kw-identical?]]
   [sci.impl.vars :as vars]
   [sci.impl.types :as t]))

(declare interpret)
#?(:clj (set! *warn-on-reflection* true))

(def macros
  '#{do if and or quote let fn def defn
     lazy-seq require try syntax-quote case . in-ns set!
     macroexpand-1 macroexpand})

;;;; Evaluation

(defn eval-and
  "The and macro from clojure.core."
  [ctx args]
  (if (empty? args) true
      (let [[x & xs] args
            v (interpret ctx x)]
        (if v
          (if (empty? xs) v
              (eval-and ctx xs))
          v))))

(defn eval-or
  "The or macro from clojure.core."
  [ctx args]
  (if (empty? args) nil
      (let [[x & xs] args
            v (interpret ctx x)]
        (if v v
            (if (empty? xs) v
                (eval-or ctx xs))))))

(defn eval-let
  "The let macro from clojure.core"
  [ctx let-bindings & exprs]
  (let [ctx (loop [ctx ctx
                   [let-name let-val & rest-let-bindings] let-bindings]
              (let [val-tag (when-let [m (meta let-val)]
                              (:tag m))
                    let-name (if val-tag
                               (vary-meta let-name update :tag (fn [t]
                                                                 (if t t val-tag)))
                               let-name)
                    v (interpret ctx let-val)
                    ctx (assoc-in ctx [:bindings let-name] v)]
                (if (empty? rest-let-bindings)
                  ctx
                  (recur ctx
                         rest-let-bindings))))]
    (last (map #(interpret ctx %) exprs))))

(defn eval-if
  [ctx expr]
  (let [[cond then else] expr]
    (if (interpret ctx cond)
      (interpret ctx then)
      (interpret ctx else))))

(defn eval-def
  [ctx [_def var-name ?docstring ?init]]
  (let [docstring (when ?init ?docstring)
        init (if docstring ?init ?docstring)
        init (interpret ctx init)
        m (meta var-name)
        m (when m
            (interpret ctx m))
        assoc-in-env
        (fn [env]
          (let [current-ns (:current-ns env)
                the-current-ns (get-in env [:namespaces current-ns])
                prev (get the-current-ns var-name)
                init (utils/merge-meta init m)
                v (if (kw-identical? :sci.impl/var.unbound init)
                    prev
                    (do (vars/bindRoot prev init)
                        (vary-meta prev merge m)
                        prev))
                the-current-ns (assoc the-current-ns var-name v)]
            (assoc-in env [:namespaces current-ns] the-current-ns)))
        env (swap! (:env ctx) assoc-in-env)]
    ;; return var instead of init-val
    (get-in env [:namespaces (:current-ns env) var-name])))

(defn lookup [{:keys [:bindings] :as ctx} sym]
  (or (find bindings sym)
      (when-let [c (interop/resolve-class ctx sym)]
        [sym c]) ;; TODO, don't we resolve classes in the analyzer??
      (when (get macros sym)
        [sym sym])))

(defn resolve-symbol [ctx expr]
  (second
   (or
    (lookup ctx expr)
    ;; TODO: check if symbol is in macros and then emit an error: cannot take
    ;; the value of a macro
    (throw-error-with-location
     (str "Could not resolve symbol: " (str expr) "\nks:" (keys (:bindings ctx)))
     expr))))

(defn parse-libspec [libspec]
  (if (symbol? libspec)
    {:lib-name libspec}
    (let [[lib-name & opts] libspec]
      (loop [ret {:lib-name lib-name}
             [opt-name fst-opt & rst-opts] opts]
        (if-not opt-name ret
                (case opt-name
                  :as (recur (assoc ret :as fst-opt)
                             rst-opts)
                  (:reload :reload-all :verbose) (recur ret (cons fst-opt rst-opts))
                  :refer (recur (assoc ret :refer fst-opt)
                                rst-opts)))))))

(declare eval-string*)

(defn handle-require-libspec-env
  [env current-ns the-loaded-ns lib-name {:keys [:as :refer] :as _parsed-libspec}]
  (let [the-current-ns (get-in env [:namespaces current-ns]) ;; = ns-data?
        the-current-ns (if as (assoc-in the-current-ns [:aliases as] lib-name)
                           the-current-ns)
        the-current-ns
        (if refer
          (do
            (when-not (sequential? refer)
              (throw (new #?(:clj Exception :cljs js/Error)
                          (str ":refer value must be a sequential collection of symbols"))))
            (reduce (fn [ns sym]
                      (assoc ns sym
                             (if-let [[_k v] (find the-loaded-ns sym)]
                               v
                               (throw (new #?(:clj Exception :cljs js/Error)
                                           (str sym " does not exist"))))))
                    the-current-ns
                    refer))
          the-current-ns)
        env (assoc-in env [:namespaces current-ns] the-current-ns)]
    env))

(defn handle-require-libspec
  [ctx libspec]
  (let [{:keys [:lib-name] :as parsed-libspec} (parse-libspec libspec)
        env* (:env ctx)
        env @env* ;; NOTE: loading namespaces is not (yet) thread-safe
        current-ns (:current-ns env)
        namespaces (get env :namespaces)]
    (if-let [the-loaded-ns (get namespaces lib-name)]
      (reset! env* (handle-require-libspec-env env current-ns the-loaded-ns lib-name parsed-libspec))
      (if-let [load-fn (:load-fn ctx)]
        (if-let [{:keys [:file :source]} (load-fn {:namespace lib-name})]
          (do
            (try (vars/with-bindings {vars/file-var file}
                   (eval-string* ctx source))
                 (catch #?(:clj Exception :cljs js/Error) e
                   (swap! env* update :namespaces dissoc lib-name)
                   (throw e)))
            (set-namespace! ctx current-ns)
            (swap! env* (fn [env]
                          (let [namespaces (get env :namespaces)
                                the-loaded-ns (get namespaces lib-name)]
                            (handle-require-libspec-env env current-ns
                                                        the-loaded-ns
                                                        lib-name parsed-libspec)))))
          (throw (new #?(:clj Exception :cljs js/Error)
                      (str "Could not require " lib-name "."))))
        (throw (new #?(:clj Exception :cljs js/Error)
                    (str "Could not require " lib-name ".")))))))

(defn eval-require
  [ctx expr]
  (let [args (map #(interpret ctx %) (rest expr))]
    (run! #(handle-require-libspec ctx %) args)))

(defn eval-case
  [ctx [_case {:keys [:case-map :case-val :case-default]}]]
  (let [v (interpret ctx case-val)]
    (if-let [[_ found] (find case-map v)]
      (interpret ctx found)
      (if (vector? case-default)
        (interpret ctx (second case-default))
        (throw (new #?(:clj Exception :cljs js/Error)
                    (str "No matching clause: " v)))))))

(defn eval-try
  [ctx expr]
  (let [{:keys [:body :catches :finally]} (:sci.impl/try expr)]
    (try
      (interpret (assoc ctx :sci.impl/in-try true) body)
      (catch #?(:clj Throwable :cljs js/Error) e
        (if-let
            [[_ r]
             (reduce (fn [_ c]
                       (let [clazz (:class c)]
                         (when (instance? clazz e)
                           (reduced
                            [::try-result
                             (interpret (assoc-in ctx [:bindings (:binding c)]
                                                  e)
                                        (:body c))]))))
                     nil
                     catches)]
          r
          (rethrow-with-location-of-node ctx e body)))
      (finally
        (interpret ctx finally)))))

(defn eval-throw [ctx [_throw ex]]
  (let [ex (interpret ctx ex)]
    (throw ex)))

;;;; Interop

(defn eval-static-method-invocation [ctx expr]
  (interop/invoke-static-method ctx
                                (cons (first expr)
                                      ;; eval args!
                                      (map #(interpret ctx %) (rest expr)))))

(defn eval-constructor-invocation [ctx [_new #?(:clj class :cljs constructor) args]]
  (let [args (map #(interpret ctx %) args)] ;; eval args!
    (interop/invoke-constructor ctx #?(:clj class :cljs constructor) args)))

#?(:clj
   (defn super-symbols [clazz]
     ;; (prn clazz '-> (map #(symbol (.getName ^Class %)) (supers clazz)))
     (map #(symbol (.getName ^Class %)) (supers clazz))))

(defn eval-instance-method-invocation [{:keys [:class->opts] :as ctx} [_dot instance-expr method-str args]]
  (let [instance-meta (meta instance-expr)
        t (:tag instance-meta)
        instance-expr* (interpret ctx instance-expr)
        t-class (when t
                  (or (interop/resolve-class ctx t)
                      (throw-error-with-location (str "Unable to resolve classname: " t) instance-expr)))
        ^Class target-class (or t-class
                                (when-let [f (:public-class ctx)]
                                  (f instance-expr*)))
        resolved-class (or target-class (#?(:clj class :cljs type) instance-expr*))
        class-name (#?(:clj .getName :cljs str) resolved-class)
        class-symbol (symbol class-name)
        opts (get class->opts class-symbol)]
    ;; we have to check options at run time, since we don't know what the class
    ;; of instance-expr is at analysis time
    (when-not opts
      (throw-error-with-location (str "Method " method-str " on " resolved-class " not allowed!") instance-expr))
    (let [args (map #(interpret ctx %) args)] ;; eval args!
      (if target-class
        (interop/invoke-instance-method ctx instance-expr* target-class method-str args)
        (interop/invoke-instance-method ctx instance-expr* method-str args)))))

;;;; End interop

;;;; Namespaces

(defn eval-in-ns [ctx [_in-ns ns-expr]]
  (let [ns-sym (interpret ctx ns-expr)]
    (set-namespace! ctx ns-sym)
    nil))

(defn eval-refer [ctx [_ ns-sym & exprs]]
  (let [ns-sym (interpret ctx ns-sym)]
    (loop [exprs exprs]
      (when exprs
        (let [[k v] exprs]
          (case k
            :exclude
            (swap! (:env ctx)
                   (fn [env]
                     (let [current-ns (:current-ns env)]
                       (update-in env [:namespaces current-ns :refer ns-sym :exclude]
                                  (fnil into #{}) v)))))
          (recur (nnext exprs)))))))

(declare eval-form)

(defn eval-resolve [ctx [_ sym]]
  (let [sym (interpret ctx sym)]
    (second (ana/lookup ctx sym))))

;;;; End namespaces

;;;; Macros

(defn macroexpand-1 [ctx expr]
  (let [original-expr expr]
    (if (seq? expr)
      (let [op (first expr)]
        (if (symbol? op)
          (cond (get ana/special-syms op) expr
                (contains? #{'for} op) (ana/analyze (assoc ctx :sci.impl/macroexpanding true)
                                                    expr)
                :else
                (let [f (ana/resolve-symbol ctx op)
                      f (if (and (vars/var? f)
                                 (vars/isMacro f))
                          @f f)]
                  (if (ana/macro? f)
                    (apply f original-expr (:bindings ctx) (rest expr))
                    expr)))
          expr))
      expr)))

(defn macroexpand
  [ctx form]
  (let [ex (macroexpand-1 ctx form)]
    (if (identical? ex form)
      form
      (macroexpand ctx ex))))

;;;; End macros

(defn eval-set! [ctx [_ obj v]]
  (let [obj (interpret ctx obj)
        v (interpret ctx v)]
    (if (vars/var? obj)
      (t/setVal obj v)
      (throw (ex-info (str "Cannot set " obj " to " v) {:obj obj :v v})))))

(declare eval-string)

(defn eval-do*
  [ctx exprs]
  (loop [[expr & exprs] exprs]
    (let [ret (try (interpret ctx expr)
                   (catch #?(:clj Throwable :cljs js/Error) e
                     (rethrow-with-location-of-node ctx e expr)))]
      (if-let [exprs (seq exprs)]
        (recur exprs)
        ret))))

(defn eval-do
  [ctx expr]
  (when-let [exprs (next expr)]
    (eval-do* ctx exprs)))

(defn eval-special-call [ctx f-sym expr]
  (case (utils/strip-core-ns f-sym)
    do (eval-do ctx expr)
    if (eval-if ctx (rest expr))
    and (eval-and ctx (rest expr))
    or (eval-or ctx (rest expr))
    let (apply eval-let ctx (rest expr))
    def (eval-def ctx expr)
    ;; defonce (eval-defonce ctx expr)
    lazy-seq (new #?(:clj clojure.lang.LazySeq
                     :cljs cljs.core/LazySeq)
                  #?@(:clj []
                      :cljs [nil])
                  (interpret ctx (second expr))
                  #?@(:clj []
                      :cljs [nil nil]))
    recur (fns/->Recur (map #(interpret ctx %) (rest expr)))
    require (eval-require ctx expr)
    case (eval-case ctx expr)
    try (eval-try ctx expr)
    ;; syntax-quote (eval-syntax-quote ctx expr)
    ;; interop
    new (eval-constructor-invocation ctx expr)
    . (eval-instance-method-invocation ctx expr)
    throw (eval-throw ctx expr)
    in-ns (eval-in-ns ctx expr)
    set! (eval-set! ctx expr)
    refer (eval-refer ctx expr)
    resolve (eval-resolve ctx expr)
    macroexpand-1 (macroexpand-1 ctx (interpret ctx (second expr)))
    macroexpand (macroexpand ctx (interpret ctx (second expr)))))

(defn eval-call [ctx expr]
  (try (let [f (first expr)
             m (meta f)
             op (when m (:sci.impl/op m))]
         ;; (prn "call first op" (type f) op)
         (cond
           (and (symbol? f) (not op))
           (eval-special-call ctx f expr)
           (kw-identical? op :static-access)
           (eval-static-method-invocation ctx expr)
           :else
           (let [f (if op (interpret ctx f)
                       f)] ;; why interpret if this needs op?
             (cond
               (ifn? f) (apply f (map #(interpret ctx %) (rest expr)))
               (:dry-run ctx) nil
               :else
               (throw (new #?(:clj Exception :cljs js/Error)
                           (str "Cannot call " (pr-str f) " as a function.")))))))
       (catch #?(:clj Throwable :cljs js/Error) e
         (rethrow-with-location-of-node ctx e expr))))

(defn remove-eval-mark [v]
  ;; TODO: find out why the special case for vars is needed. When I remove it,
  ;; spartan.spec does not work.
  (if (and (meta v) (not (vars/var? v)))
    (vary-meta v dissoc :sci.impl/op)
    v))

(defn interpret
  [ctx expr]
  (let [m (meta expr)
        ;; _ (prn "m" (type expr) m)
        ctx (if (and m (:top-level? ctx))
              (assoc ctx :top-level? false)
              ctx)
        op (and m (:sci.impl/op m))
        ;; _ (prn expr "op>" op)
        ret
        (if
            (not op) expr
            ;; TODO: moving this up increased performance for #246. We can
            ;; probably optimize it further by not using separate keywords for
            ;; one :sci.impl/op keyword on which we can use a case expression
            (case op
              :call (eval-call ctx expr)
              :try (eval-try ctx expr)
              :fn (fns/eval-fn ctx interpret eval-do* expr)
              :static-access (interop/get-static-field ctx expr)
              :var-value (nth expr 0)
              :deref! (let [v (first expr)
                            v (if (vars/var? v) @v v)]
                        (force v))
              :resolve-sym (resolve-symbol ctx expr)
              :needs-ctx (partial expr ctx)
              (cond (vars/var? expr) (if-not (vars/isMacro expr)
                                       (deref expr)
                                       (throw (new #?(:clj IllegalStateException :cljs js/Error)
                                                   (str "Can't take value of a macro: " expr ""))))
                    (map? expr) (zipmap (map #(interpret ctx %) (keys expr))
                                        (map #(interpret ctx %) (vals expr)))
                    (or (vector? expr) (set? expr)) (into (empty expr)
                                                          (map #(interpret ctx %)
                                                               expr))
                    :else (throw (new #?(:clj Exception :cljs js/Error)
                                      (str "unexpected: " expr ", type: " (type expr), ", meta:" (meta expr)))))))
        ret (remove-eval-mark ret)]
    ;; for debugging:
    ;; (prn expr (meta expr) '-> ret)
    (if-let [n (:realize-max ctx)]
      (max-or-throw ret (assoc ctx
                               :expression expr)
                    n)
      ret)))

(defn do? [expr]
  (and (list? expr)
       (= 'do (first expr))))

(defn eval-form [ctx form]
  (if (do? form) (loop [exprs (rest form)
                        ret nil]
                   (if (seq exprs)
                     (recur
                      (rest exprs)
                      (eval-form ctx (first exprs)))
                     ret))
      (let [analyzed (ana/analyze ctx form)
            ret (interpret ctx analyzed)]
        ret)))

(defn eval-string* [ctx s]
  (let [reader (r/indexing-push-back-reader (r/string-push-back-reader s))]
    (loop [queue []
           ret nil]
      (let [expr (or (first queue)
                     (p/parse-next ctx reader))]
        (if (utils/kw-identical? :edamame.impl.parser/eof expr) ret
            (let [ret (eval-form ctx expr)]
              (if (seq queue) (recur (rest queue) ret)
                  (recur [] ret))))))))

;;;; Called from public API

(defn eval-string
  ([s] (eval-string s nil))
  ([s opts]
   (let [init-ctx (opts/init opts)
         ret (vars/with-bindings
               (when-not @vars/current-ns
                 {vars/current-ns (vars/->SciNamespace (get @(:env init-ctx) :current-ns))})
               (eval-string* init-ctx s))]
     ret)))

;;;; Scratch

(comment
  (eval-string "((fn f [x] (if (< x 3) (recur (inc x)) x)) 0)")
  )
