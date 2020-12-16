(ns sci.impl.resolve
  (:require [clojure.string :as str]
            [sci.impl.interop :as interop]
            [sci.impl.records :as records]
            [sci.impl.utils :as utils :refer [mark-resolve-sym
                                              strip-core-ns
                                              ana-macros]]
            [sci.impl.vars :as vars]))

(defn throw-error-with-location [msg node]
  (utils/throw-error-with-location msg node {:phase "analysis"}))

(defn check-permission! [{:keys [:allow :deny]} check-sym sym v]
  (or (identical? utils/allowed-loop sym)
      (identical? utils/allowed-recur sym)
      (let [check-sym (strip-core-ns check-sym)]
        (when-not (if allow (or (and (vars/var? v) (not (:sci.impl/built-in (meta v))))
                                (contains? allow check-sym))
                      true)
          (throw-error-with-location (str sym " is not allowed!") sym))
        (when (if deny (contains? deny check-sym)
                  false)
          (throw-error-with-location (str sym " is not allowed!") sym)))))

(defn lookup* [{:keys [:env] :as ctx} sym call?]
  (let [sym-ns (some-> (namespace sym) symbol)
        sym-name (symbol (name sym))
        env @env
        cnn (vars/current-ns-name)
        the-current-ns (-> env :namespaces cnn)
        ;; resolve alias
        sym-ns (when sym-ns (or (get-in the-current-ns [:aliases sym-ns])
                                sym-ns))]
    (or (find the-current-ns sym) ;; env can contain foo/bar symbols from bindings
        (cond
          (and sym-ns (or (= sym-ns 'clojure.core) (= sym-ns 'cljs.core)))
          (or (some-> env :namespaces (get 'clojure.core) (find sym-name))
              (when-let [v (when call? (get ana-macros sym-name))]
                [sym v]))
          sym-ns
          (or (some-> env :namespaces sym-ns (find sym-name))
              (when-let [clazz (interop/resolve-class ctx sym-ns)]
                [sym (with-meta
                       [clazz sym-name]
                       #?(:clj
                          (if call?
                            {:sci.impl.analyzer/static-access true}
                            {:sci.impl/op :static-access
                             :file @vars/current-file
                             :ns @vars/current-ns})
                          :cljs {:sci.impl/op :static-access}))]))
          :else
          ;; no sym-ns, this could be a symbol from clojure.core
          (when-not (contains?
                     (get-in the-current-ns [:refer 'clojure.core :exclude]) sym-name)
            (or
             (some-> env :namespaces (get 'clojure.core) (find sym-name))
             (when (when call? (get ana-macros sym))
               [sym sym])
             (when-let [c (interop/resolve-class ctx sym)]
               [sym c])
             ;; resolves record or protocol referenced as class
             ;; e.g. clojure.lang.IDeref which is really a var in clojure.lang/IDeref
             (when-let [x (records/resolve-record-or-protocol-class ctx sym)]
               [sym x])))))))

(defn tag [_ctx expr]
  (when-let [m (meta expr)]
    (:tag m)))

(defn lookup [{:keys [:bindings] :as ctx} sym call?]
  (let [[k v :as kv]
        (or
         ;; bindings are not checked for permissions
         (when-let [[k v]
                    (find bindings sym)]
           ;; never inline a binding at macro time!
           (let [t (tag ctx v)
                 v (mark-resolve-sym k)
                 ;; pass along tag of expression!
                 v (if t (vary-meta v
                                    assoc :tag t)
                       v)]
             [k v]))
         (when-let
             [[k v :as kv]
              (or
               (lookup* ctx sym call?)
               #_(when (= 'recur sym)
                   [sym sym]))]
           (check-permission! ctx k sym v)
           kv))]
    ;; (prn 'lookup sym '-> res)
    (if-let [m (and (not (:sci.impl/prevent-deref ctx))
                    (meta k))]
      (if (:sci.impl/deref! m)
        ;; the evaluation of this expression has been delayed by
        ;; the caller and now is the time to deref it
        [k (with-meta [v]
             {:sci.impl/op :deref!})]
        kv)
      kv)))

(defn resolve-symbol
  ([ctx sym] (resolve-symbol ctx sym false))
  ([ctx sym call?]
   (let [sym sym ;; (strip-core-ns sym)
         res (second
              (or
               (lookup ctx sym call?)
               ;; TODO: check if symbol is in macros and then emit an error: cannot take
               ;; the value of a macro
               (let [n (name sym)]
                 (cond
                   (and call?
                        (str/starts-with? n ".")
                        (> (count n) 1))
                   [sym 'expand-dot*] ;; method invocation
                   (and call?
                        (str/ends-with? n ".")
                        (> (count n) 1))
                   [sym 'expand-constructor]
                   (str/starts-with? n "'") ;; TODO: deprecated?
                   (let [v (symbol (subs n 1))]
                     [v v])
                   :else
                   (throw-error-with-location
                    (str "Could not resolve symbol: " (str sym))
                    sym)))))]
     ;; (prn 'resolve sym '-> res (meta res))
     res)))
