(ns sci.impl.utils
  {:no-doc true})

(def constant? (some-fn fn? number? string? keyword?))

(defn mark-resolve-sym
  [sym]
  (vary-meta
   sym
   (fn [m]
     (assoc m
            :sci.impl/eval true
            :sci.impl/unresolved true))))

(defn gensym*
  ([] (mark-resolve-sym (gensym)))
  ([prefix] (mark-resolve-sym (gensym prefix))))

(defn mark-eval-call
  [expr]
  (vary-meta
   expr
   (fn [m]
     (assoc m
            :sci.impl/eval-call true
            :sci.impl/eval true))))

(defn mark-eval
  [expr]
  (vary-meta
   expr
   (fn [m]
     (assoc m :sci.impl/eval true))))
