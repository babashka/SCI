(ns sci.namespaces-test
  (:require
   [clojure.set :as set]
   [clojure.test :as test :refer [deftest is testing]]
   [sci.test-utils :as tu]))

(defn eval*
  ([form] (eval* nil form))
  ([binding form]
   (tu/eval* form {:bindings {'*in* binding}})))

(deftest autoresolve-test
  (is (= :user/foo (eval* "::foo")))
  (is (= :bar/foo (eval* "(in-ns 'bar) ::foo")))
  (is (= :clojure.string/foo (eval* "::str/foo")))
  (is (= :clojure.set/foo (eval* "(require '[clojure.set :as str]) ::str/foo")))
  (is (= :clojure.string/foo (eval* "(in-ns 'foo) (require '[clojure.string :as str]) ::str/foo")))
  (is (= :clojure.string/foo (eval* "(ns foo (:require [clojure.string :as s])) ::s/foo"))))

(deftest vars-partitioned-by-namespace-test
  (is (= 10 (eval* "(in-ns 'foo) (def x 10) (in-ns 'bar) (def x 11) (in-ns 'foo) x"))))

(deftest ns-form-test
  (is (= #{1} (eval* "(ns foo (:require [clojure.set :as x])) (x/difference #{1 2 3} #{2 3 4})")))
  (is (= #{1} (eval* "(ns foo
                        \"docstring\"
                        {:metadata 1}
                        (:require [clojure.set :refer [difference]])) (difference #{1 2 3} #{2 3 4})")))
  #?(:clj
     (is (= :foo/foo
            (eval* "(ns foo (:import [clojure.lang ExceptionInfo]))
                    (try ::foo (catch ExceptionInfo e nil))")))))

(deftest ns-name-test
  (is (= 'foo (eval* "(ns foo) (ns-name *ns*)"))))

(deftest no-crash-test
  (is (= :foo/foo (eval* "(ns foo \"docstring\") ::foo"))))

(deftest ns-metadata-test
  (is (= {:line 1, :column 5, :a 1, :b 1}
         (eval* "(ns ^{:a 1} foo {:b 1}) (meta *ns*)")))
  (is (= {:line 1, :column 5, :a 1, :b 1}
         (eval* "(ns ^{:a 1} foo {:b 1}) (meta *ns*) (ns bar) (meta (the-ns 'foo))"))))

(deftest recycle-namespace-objects
  (when-not tu/native?
    (is (empty? (set/difference (eval* "(set (all-ns))") (eval* "(set (all-ns))"))))))

(deftest namespace-doc
  (is (= "foobar" (:doc (eval* "(ns foo \"foobar\") (meta (find-ns 'foo))")))))

(deftest ns-imports-test
  #?@(:clj
      [(is (eval* "(some? (get (ns-imports *ns*) 'String))"))
       (is (eval* "
(import clojure.lang.ExceptionInfo) (some? (get (ns-imports *ns*) 'ExceptionInfo))"))]))

(deftest ns-publics-test
  (testing "See issue 520"
    (is (eval* "(require '[clojure.string :refer [includes?]]) (nil? (get (ns-publics *ns*) 'refer))"))))

(deftest ns-refers-test
  (is (eval* "(some? (get (ns-refers *ns*) 'inc))"))
  (is (eval* "(def x 1) (nil? (get (ns-refers *ns*) 'x))"))
  (is (eval* "(require '[clojure.string :refer [includes?]]) (some? (get (ns-refers *ns*) 'includes?))")))

(deftest ns-map-test
  (is (eval* "(some? (get (ns-map *ns*) 'inc))"))
  #?(:clj (is (eval* "(some? (get (ns-map *ns*) 'String))")))
  (is (eval* "(defn- foo []) (some? (get (ns-map *ns*) 'foo))")))

(deftest ns-unmap-test
  (is (eval* "(def foo 1) (ns-unmap *ns* 'foo) (nil? (resolve 'foo))"))
  (is (eval* "(defn bar []) (ns-unmap *ns* 'bar) (nil? (resolve 'bar))"))
  (is (eval* "(defn- baz []) (ns-unmap *ns* 'baz) (nil? (resolve 'baz))"))
  #?(:clj (is (= [false true] (eval* "
(ns-unmap *ns* 'Object)
(def o1 (resolve 'Object))
(import '[java.lang Object])
(def o2 (resolve 'Object))
[(some? o1) (some? o2)]")))))

(deftest find-var-test
  (is (eval* "(= #'clojure.core/map (find-var 'clojure.core/map))"))
  (is (eval* "(nil? (find-var 'clojure.core/no-such-symbol))"))
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                        #"No such namespace: no.such.namespace"
                        (eval* "(find-var 'no.such.namespace/var)")))
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                        #"Not a qualified symbol: no-namespace"
                        (eval* "(find-var 'no-namespace)"))))

(deftest find-ns-test
  (is (true? (eval* "(ns foo) (some? (find-ns 'foo))")))
  (is (nil? (eval* "(find-ns 'foo)"))))

(deftest remove-ns-test
  (is (nil? (eval* "(ns foo) (ns bar) (remove-ns 'foo) (find-ns 'foo)"))))

(deftest ns-syntax-test
  (is (thrown-with-msg?
       #?(:clj Exception :cljs js/Error)
       #"symbol"
       (eval* "(ns)"))))

(deftest nested-libspecs-test
  (is (= #{1 2 3 4} (eval* "(require '[clojure [set :refer [union]]]) (union #{1 2 3} #{2 3 4})")))
  (is (thrown-with-msg?
       #?(:clj Exception :cljs js/Error)
       #"lib names inside prefix lists must not contain periods"
       (eval* "(ns clojure.core.protocols) (ns foo) (require '[clojure [core.protocols]])")))
  (is (thrown-with-msg?
       #?(:clj Exception :cljs js/Error)
       #"Unsupported option\(s\) supplied: :foo"
       (eval* "(ns foo (:require [clojure.core] [dude] :foo))")))
  (testing "error message contains location"
    (is (thrown-with-data?
         {:line 1 :column 9}
         (eval* "(ns foo (:require [clojure.core] [dude] :foo))")))))
