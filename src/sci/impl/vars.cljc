(ns sci.impl.vars
  {:no-doc true}
  (:refer-clojure :exclude [var? binding
                            push-thread-bindings
                            get-thread-bindings
                            pop-thread-bindings
                            with-redefs
                            with-redefs-fn
                            with-bindings
                            thread-bound?
                            alter-var-root])
  (:require [sci.impl.macros :as macros]
            #?(:clj [borkdude.graal.locking :as locking])
            [sci.impl.types :as t])
  #?(:cljs (:require-macros [sci.impl.vars :refer [with-bindings]])))

#?(:clj (set! *warn-on-reflection* true))

(deftype Frame [bindings prev])

(def top-frame (Frame. {} nil))

#?(:clj
   (def ^ThreadLocal dvals (proxy [ThreadLocal] []
                             (initialValue [] top-frame)))
   :cljs
   (def dvals (atom top-frame)))

(defn get-thread-binding-frame ^Frame []
  #?(:clj (.get dvals)
     :cljs @dvals))

(defn clone-thread-binding-frame ^Frame []
  (let [^Frame f #?(:clj (.get dvals)
                    :cljs @dvals)]
    (Frame. (.-bindings f) nil)))

(defn reset-thread-binding-frame [frame]
  #?(:clj (.set dvals frame)
     :cljs (reset! dvals frame)))

(deftype TBox #?(:clj [thread ^:volatile-mutable val]
                 :cljs [thread ^:mutable val])
  t/IBox
  (setVal [this v]
    (set! val  v))
  (getVal [this] val))

(declare var?)

(defn dynamic-var? [v]
  (and (var? v)
       (:dynamic (meta v))))

(defn push-thread-bindings [bindings]
  (let [frame (get-thread-binding-frame)
        bmap (.-bindings frame)
        bmap (reduce (fn [acc [var* val*]]
                       (when-not (dynamic-var? var*)
                         (throw (new #?(:clj IllegalStateException
                                        :cljs js/Error)
                                     (str "Can't dynamically bind non-dynamic var " var*))))
                       (assoc acc var* (TBox. #?(:clj (Thread/currentThread)
                                                 :cljs nil) val*)))
                     bmap
                     bindings)]
    (reset-thread-binding-frame (Frame. bmap frame))))

(defn pop-thread-bindings []
  (if-let [f (.-prev (get-thread-binding-frame))]
    (if (identical? top-frame f)
      #?(:clj (.remove dvals)
         :cljs (reset! dvals top-frame))
      (reset-thread-binding-frame f))
    (throw (new #?(:clj Exception :cljs js/Error) "No frame to pop."))))

(defn get-thread-bindings []
  (let [f (get-thread-binding-frame)]
    (loop [ret {}
           kvs (seq (.-bindings f))]
      (if kvs
        (let [[var* ^TBox tbox] (first kvs)
              tbox-val (t/getVal tbox)]
          (recur (assoc ret var* tbox-val)
                 (next kvs)))
        ret))))

(defn get-thread-binding ^TBox [sci-var]
  (when-let [^Frame f #?(:clj (.get dvals)
                         :cljs @dvals)]
    (when-let [bindings (.-bindings f)]
      (get bindings sci-var))))

(defn thread-bound? [sci-var]
  (when-let [^Frame f #?(:clj (.get dvals)
                         :cljs @dvals)]
    (when-let [bindings (.-bindings f)]
      (contains? bindings sci-var))))

(defn binding-conveyor-fn
  [f]
  (let [frame (clone-thread-binding-frame)]
    (fn
      ([]
       (reset-thread-binding-frame frame)
       (f))
      ([x]
       (reset-thread-binding-frame frame)
       (f x))
      ([x y]
       (reset-thread-binding-frame frame)
       (f x y))
      ([x y z]
       (reset-thread-binding-frame frame)
       (f x y z))
      ([x y z & args]
       (reset-thread-binding-frame frame)
       (apply f x y z args)))))

(defprotocol IVar
  (bindRoot [this v])
  (getRawRoot [this])
  (toSymbol [this])
  (isMacro [this])
  (hasRoot [this])
  (isBound [this])
  (unbind [this]))

(defn throw-unbound-call-exception [the-var]
  (throw (new #?(:clj IllegalStateException
                 :cljs js/Error) (str "Attempting to call unbound fn: " the-var))))

(deftype SciUnbound [the-var]
  Object
  (toString [_]
    (str "Unbound: " the-var))
  #?@(:clj [clojure.lang.IFn] :cljs [IFn])
  (#?(:clj invoke :cljs -invoke) [_]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r s]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r s t]
    (throw-unbound-call-exception the-var))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r s t rest]
    (throw-unbound-call-exception the-var))
  #?(:clj
     (applyTo [_ args]
              (throw-unbound-call-exception the-var))))

;; adapted from https://github.com/clojure/clojurescript/blob/df1837048d01b157a04bb3dc7fedc58ee349a24a/src/main/cljs/cljs/core.cljs#L1118
(deftype SciVar [#?(:clj ^:volatile-mutable root
                    :cljs ^:mutable root)
                 ;; TODO: namespace field
                 ;; TODO: dynamic field
                 ;; TODO: macro field
                 sym
                 #?(:clj ^:volatile-mutable meta
                    :cljs ^:mutable meta)]
  IVar
  (bindRoot [this v]
    (set! (.-root this) v))
  (getRawRoot [this]
    root)
  (toSymbol [this] sym)
  (isMacro [_]
    (:sci/macro (clojure.core/meta root)))
  (isBound [this]
    (or (not (instance? SciUnbound root))
        (thread-bound? this)))
  (unbind [this]
    (set! (.-root this) (SciUnbound. this)))
  (hasRoot [this]
    (not (instance? SciUnbound root)))
  t/IBox
  (setVal [this v]
    (let [b (get-thread-binding this)]
      (if (some? b)
        #?(:clj
           (let [t (.-thread b)]
             (if (not (identical? t (Thread/currentThread)))
               (throw (new IllegalStateException
                           (str "Can't change/establish root binding of " this " with set")))
               (t/setVal b v)))
           :cljs (t/setVal b v))
        (throw (new #?(:clj IllegalStateException :cljs js/Error)
                    (str "Can't change/establish root binding of " this " with set"))))))
  (getVal [this] root)
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  (#?(:clj deref
      :cljs -deref) [this]
    (or (when-let [tbox (get-thread-binding this)]
          (t/getVal tbox))
        root))
  Object
  (toString [_]
    (str "#'" sym))
  #?(:cljs IPrintWithWriter)
  #?(:cljs (-pr-writer [a writer opts]
                       (-write writer "#'")
                       (pr-writer sym writer opts)))
  #?(:clj clojure.lang.IMeta :cljs IMeta)
  #?(:clj (clojure.core/meta [_] meta) :cljs (-meta [_] meta))
  #?(:clj clojure.lang.IObj :cljs IWithMeta)
  #?(:clj
     (withMeta [this new-meta]
               (set! meta new-meta)
               this)
     :cljs
     (-with-meta [this new-meta]
                 (set! meta new-meta)
                 this))
  ;; #?(:clj Comparable :cljs IEquiv)
  ;; (-equiv [this other]
  ;;   (if (instance? Var other)
  ;;     (= (.-sym this) (.-sym other))
  ;;     false))
  ;; #?(:clj clojure.lang.IHashEq :cljs IHash)
  ;; (-hash [_]
  ;;   (hash-symbol sym))
  #?(:clj clojure.lang.IReference)
  #?(:clj (alterMeta [this f args]
                     (locking/locking (set! meta (apply f meta args)))))
  #?(:clj (resetMeta [this m]
                     (locking/locking (set! meta m))))
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [_]
    (root))
  (#?(:clj invoke :cljs -invoke) [_ a]
    (root a))
  (#?(:clj invoke :cljs -invoke) [_ a b]
    (root a b))
  (#?(:clj invoke :cljs -invoke) [_ a b c]
    (root a b c))
  (#?(:clj invoke :cljs -invoke) [_ a b c d]
    (root a b c d))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e]
    (root a b c d e))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f]
    (root a b c d e f))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g]
    (root a b c d e f g))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h]
    (root a b c d e f g h))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i]
    (root a b c d e f g h i))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j]
    (root a b c d e f g h i j))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k]
    (root a b c d e f g h i j k))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l]
    (root a b c d e f g h i j k l))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m]
    (root a b c d e f g h i j k l m))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n]
    (root a b c d e f g h i j k l m n))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o]
    (root a b c d e f g h i j k l m n o))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p]
    (root a b c d e f g h i j k l m n o p))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q]
    (root a b c d e f g h i j k l m n o p q))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r]
    (root a b c d e f g h i j k l m n o p q r))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r s]
    (root a b c d e f g h i j k l m n o p q r s))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r s t]
    (root a b c d e f g h i j k l m n o p q r s t))
  (#?(:clj invoke :cljs -invoke) [_ a b c d e f g h i j k l m n o p q r s t rest]
    (apply root a b c d e f g h i j k l m n o p q r s t rest))
  #?(:clj
     (applyTo [_ args]
              (apply root args))))

#?(:clj
   (do (defmethod print-method sci.impl.vars.IVar [o ^java.io.Writer w]
         (.write w (str "#'" (toSymbol o))))
       (prefer-method print-method sci.impl.vars.IVar clojure.lang.IDeref)))

(defn var? [x]
  (instance? sci.impl.vars.SciVar x))

(defn dynamic-var
  ([name]
   (dynamic-var name nil (meta name)))
  ([name init-val]
   (dynamic-var name init-val (meta name)))
  ([name init-val meta]
   (let [meta (assoc meta :dynamic true)]
     (SciVar. init-val name meta))))

#_(defn with-redefs-fn
  [binding-map func]
  (let [root-bind (fn [m]
                    (doseq [[a-var a-val] m]
                      (sci.impl.vars/bindRoot a-var a-val)))
        old-vals (zipmap (keys binding-map)
                         (map #(sci.impl.vars/getRawRoot %) (keys binding-map)))]
    (try
      (root-bind binding-map)
      (func)
      (finally
        (root-bind old-vals)))))

#_(defn with-redefs
  [_ _ bindings & body]
  `(clojure.core/with-redefs-fn ~(zipmap (map #(list `var %) (take-nth 2 bindings))
                                         (take-nth 2 (next bindings)))
     (fn [] ~@body)))

(defn binding
  [_ _ bindings & body]
  #_(assert-args
     (vector? bindings) "a vector for its binding"
     (even? (count bindings)) "an even number of forms in binding vector")
  (let [var-ize (fn [var-vals]
                  (loop [ret [] vvs (seq var-vals)]
                    (if vvs
                      (recur  (conj (conj ret `(var ~(first vvs))) (second vvs))
                              (next (next vvs)))
                      (seq ret))))]
    `(let []
       ;; important: outside try
       (clojure.core/push-thread-bindings (hash-map ~@(var-ize bindings)))
       (try
         ~@body
         (finally
           (clojure.core/pop-thread-bindings))))))

(defprotocol INamespace
  (getName [_]))

(deftype SciNamespace [name]
  Object
  (toString [_]
    (str name))
  INamespace
  #_(findInternedVar [this sym]
      (let [k (munge (str sym))]
        (when ^boolean #?(:cljs (gobject/containsKey obj k))
          (let [var-sym (symbol (str name) (str sym))
                var-meta {:ns this}]
            (Var. (ns-lookup obj k) var-sym var-meta)))))
  (getName [_] name)
  ;; IEquiv
  ;; (-equiv [_ other]
  ;;   (if (instance? SciNamespace other)
  ;;     (= name (.-name other))
  ;;     false))
  ;; IHash
  ;; (-hash [_]
  ;;   (hash name))
  )

(macros/deftime
  (defmacro with-bindings
    "Macro for binding sci vars. Must be called with map of sci dynamic
  vars to values. Used in babashka."
    [bindings & body]
    ;; important: outside try
    `(do (vars/push-thread-bindings ~bindings)
         (try
           (do ~@body)
           (finally (vars/pop-thread-bindings))))))

(def current-file (dynamic-var '*file* nil))

(def current-ns (dynamic-var '*ns* nil))

(defn current-ns-name []
  (getName @current-ns))

(defn alter-var-root [v f & args]
  #?(:clj
     (locking/locking v (bindRoot v (apply f @v args)))
     :cljs (bindRoot v (apply f @v args))))

(comment
  (def v1 (SciVar. (fn [] 0) 'foo nil))
  @v1 ;; 0
  (push-thread-bindings {v1 2})
  (get-thread-binding v1) ;; 2
  (push-thread-bindings {v1 3})
  (get-thread-binding v1) ;; 3
  (pop-thread-bindings)
  (get-thread-binding v1) ;; 2
  (pop-thread-bindings)
  (get-thread-binding v1) ;; nil
  @v1 ;; 0
  (pop-thread-bindings) ;; exception
  )
