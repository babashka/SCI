(ns sci.impl.interop
  {:no-doc true}
  #?(:clj (:import [sci.impl Reflector]))
  (:require #?(:cljs [goog.object :as gobj])
            [sci.impl.vars :as vars]
            #?(:cljs [clojure.string])))

;; see https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Reflector.java
;; see invokeStaticMethod, getStaticField, etc.

#?(:clj (set! *warn-on-reflection* true))

(defn invoke-instance-method
  #?@(:cljs [[obj _target-class method-name args]
             ;; gobj/get didn't work here
             (if (identical? \- (.charAt method-name 0))
               (aget obj (subs method-name 1))
               (if-let [method (aget obj method-name)]
                 (apply method obj args)
                 (throw (js/Error. (str "Could not find instance method: " method-name)))))]
      :clj
      [#_([obj method args]
        (invoke-instance-method obj nil method args))
       ([obj target-class method args]
        (if-not target-class
          (Reflector/invokeInstanceMethod obj method (object-array args))
          (let [methods (Reflector/getMethods target-class (count args) method false)]
            (Reflector/invokeMatchingMethod method methods obj (object-array args)))))]))

(defn get-static-field #?(:clj [[^Class class field-name-sym]]
                          :cljs [[class field-name-sym]])
  #?(:clj (Reflector/getStaticField class (str field-name-sym))
     :cljs (gobj/get class field-name-sym)))

(defn invoke-constructor #?(:clj [^Class class args]
                            :cljs [constructor args])
  #?(:clj (Reflector/invokeConstructor class (object-array args))
     :cljs (apply constructor args)))

#?(:cljs
   (defn js-constructor-fn [class]
     (let [constructor (js/Function.prototype.bind.apply class)]
       (fn
         ([a] (new constructor a))
         ([a b] (new constructor a b c))
         ([a b c] (new constructor a b c d))
         ([a b c d] (new constructor a b c d e))
         ([a b c d e] (new constructor a b c d e))
         ([a b c d e f] (new constructor a b c d e f))
         ([a b c d e f g] (new constructor a b c d e f g))
         ([a b c d e f g & more]
          (throw (ex-info "Constructors with more than 7 arguments are not supported" {:class class})))))))

(defn invoke-static-method #?(:clj [[^Class class method-name] args]
                              :cljs [[class method-name] args])
  #?(:clj
     (Reflector/invokeStaticMethod class (str method-name) (object-array args))
     :cljs (if-let [method (gobj/get class method-name)]
             (.apply method class (to-array args))
             (let [method-name (str method-name)
                   [field sub-method-name] (clojure.string/split method-name #"\.")]
               (cond
                 sub-method-name
                 (invoke-instance-method (get-static-field [class field]) nil sub-method-name args)

                 (clojure.string/ends-with? method-name ".")
                 (apply (js-constructor-fn (get-static-field [class field])) args)

                 :else
                 (throw (js/Error. (str "Could not find static method " method-name))))))))

(defn fully-qualify-class [{:keys [:env :class->opts]} sym]
  (or #?(:clj (when (contains? class->opts sym) sym)
         :cljs (if-let [ns* (namespace sym)]
                 (when (identical? "js" ns*)
                   (when (contains? class->opts (symbol (name sym)))
                     sym))
                 (when (contains? class->opts sym)
                   sym)))
      (let [env @env]
        (or (get (:imports env) sym)
            (let [cnn (vars/current-ns-name)]
              (get-in env [:namespaces cnn :imports sym]))))))

(defn resolve-class-opts [{:keys [:env :class->opts]} sym]
  (let [class-opts (or #?(:clj (get class->opts sym)
                     :cljs (if-let [ns* (namespace sym)]
                             (when (identical? "js" ns*)
                               (get class->opts (symbol (name sym))))
                             (get class->opts sym)))
                       (let [env @env]
                         (or
                          (when-let [v (get-in env [:imports sym])]
                            (get class->opts v))
                          (let [cnn (vars/current-ns-name)]
                            (when-let [v (get-in env [:namespaces cnn :imports sym])]
                              (get class->opts v))))))]
    class-opts))

(defn resolve-class [ctx sym]
  (:class (resolve-class-opts ctx sym)))
