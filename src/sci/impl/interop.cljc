(ns sci.impl.interop
  {:no-doc true}
  #?(:clj (:import
           [java.lang.reflect Field Modifier]
           [sci.impl Reflector]))
  (:require #?(:cljs [goog.object :as gobject])
            #?(:cljs [clojure.string :as str])
            [sci.impl.utils :as utils]))

;; see https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Reflector.java
;; see invokeStaticMethod, getStaticField, etc.

#?(:clj (set! *warn-on-reflection* true))

(defn invoke-instance-field
  #?@(:cljs [[obj _target-class field-name]
             ;; gobject/get didn't work here
             (aget obj field-name)]
      :clj
      [[obj ^Class target-class method]
       (let [^Field field (.getField target-class method)
             mod (.getModifiers field)]
         (if (and (not (Modifier/isStatic mod))
                  (Modifier/isPublic mod))
           (.get field obj)
           (throw (ex-info (str "Not found or accessible instance field: " method) {}))))]))

(defn invoke-instance-method
  #?@(:cljs [[obj _target-class method-name args]
             ;; gobject/get didn't work here
             (if-let [method (aget obj method-name)]
               ;; use Reflect rather than (.apply method ...), see https://github.com/babashka/nbb/issues/118
               (js/Reflect.apply method obj (into-array args) #_(js-object-array args))
               (throw (js/Error. (str "Could not find instance method: " method-name))))]
      :clj
      [[obj ^Class target-class method args]
       (if-not target-class
         (Reflector/invokeInstanceMethod obj method (object-array args))
         (let [arg-count (count args)
               methods (Reflector/getMethods target-class arg-count method false)]
           (if (and (zero? arg-count) (.isEmpty ^java.util.List methods))
             (invoke-instance-field obj target-class method)
             (Reflector/invokeMatchingMethod method methods obj (object-array args)))))]))

(defn get-static-field #?(:clj [[^Class class field-name-sym]]
                          :cljs [[class field-name-sym]])
  #?(:clj (Reflector/getStaticField class (str field-name-sym))
     :cljs (if (str/includes? (str field-name-sym) ".")
             (apply gobject/getValueByKeys class (str/split (str field-name-sym) #"\."))
             (gobject/get class field-name-sym))))

#?(:cljs (defn get-static-fields [class path-array idx max-idx]
           (if (nil? idx)
             (recur class path-array 0 (dec (alength path-array)))
             (let [class (gobject/get class (aget path-array idx))]
               (if (== idx max-idx)
                 class
                 (recur class path-array (inc idx) max-idx))))))

#?(:cljs
   (defn invoke-js-constructor* [constructor args-array]
     (js/Reflect.construct constructor args-array)))

#?(:cljs
   ;; TODO: get rid of this one in favor of the above
   (defn invoke-js-constructor [constructor args]
     (invoke-js-constructor* constructor (into-array args))))

(defn invoke-constructor #?(:clj [^Class class args]
                            :cljs [constructor args])
  #?(:clj (Reflector/invokeConstructor class (object-array args))
     :cljs (invoke-js-constructor constructor args)))

(defn invoke-static-method #?(:clj [[^Class class method-name] args]
                              :cljs [class method args])
  #?(:clj
     (Reflector/invokeStaticMethod class (str method-name) (object-array args))
     :cljs (js/Reflect.apply method class args)))

(defn fully-qualify-class [ctx sym]
  (let [env @(:env ctx)
        class->opts (:class->opts env)]
    (or #?(:clj (when (contains? class->opts sym) sym)
           :cljs (if-let [ns* (namespace sym)]
                   (when (identical? "js" ns*)
                     (when (contains? class->opts (symbol (name sym)))
                       sym))
                   (when (contains? class->opts sym)
                     sym)))
        (or (get (:imports env) sym)
            (let [cnn (utils/current-ns-name)]
              (get-in env [:namespaces cnn :imports sym]))))))

(defn resolve-class-opts [ctx sym]
  (let [env @(:env ctx)
        class->opts (:class->opts env)
        class-opts (or #?(:clj (get class->opts sym)
                          :cljs (if-let [ns* (namespace sym)]
                                  (when (identical? "js" ns*)
                                    (get class->opts (symbol (name sym))))
                                  (get class->opts sym)))
                       (let [cnn (utils/current-ns-name)
                             imports (get-in env [:namespaces cnn :imports])]
                         (if-let [[_ v] (find imports sym)]
                           ;; finding a nil v means the object was unmapped
                           (get class->opts v)
                           (when-let [v (get-in env [:imports sym])]
                             (get class->opts v)))))]
    class-opts))

(defn resolve-class [ctx sym]
  (:class (resolve-class-opts ctx sym)))
