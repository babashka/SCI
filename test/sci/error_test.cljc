(ns sci.error-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [sci.core :as sci :refer [eval-string]]))

(deftest stacktrace-test
  (let [stacktrace
        (try (eval-string "
(defn bar [] (subs nil 0))
(defn foo [] (bar))
(foo)")
             (catch #?(:clj Exception
                       :cljs js/Error) e
               (map #(-> %
                         (select-keys [:ns :name :line :column]))
                    (sci/stacktrace e))))
        expected '({:ns clojure.core, :name subs}
                   {:ns user, :name bar, :line 2, :column 14}
                   {:ns user, :name bar, :line 2, :column 1}
                   {:ns user, :name foo, :line 3, :column 14}
                   {:ns user, :name foo, :line 3, :column 1}
                   {:ns user, :name nil, :line 4, :column 1})]
    (println "-- ^ expected --- , actual")
    (doseq [st stacktrace]
      (prn st))
    (is (= expected
           stacktrace)))
  (let [stacktrace (try (eval-string "(1 2 3)")
                        (catch #?(:clj Exception
                                  :cljs js/Error) e
                          (map #(-> %
                                    (select-keys [:ns :name :line :column]))
                               (sci/stacktrace e))))]
    (is (= '({:ns user, :name nil, :line 1, :column 1}) stacktrace )))
  (testing "unresolved class in import"
    (let [stacktrace (try (eval-string "(ns foo (:import [java.io FooBar]))")
                          (catch #?(:clj Exception
                                    :cljs js/Error) e
                            (map #(-> %
                                      (select-keys [:ns :name :line :column]))
                                 (sci/stacktrace e))))]
      (is (= '({:ns foo, :name nil, :line 1, :column 9}) stacktrace ))))
  (testing "foobar"
    (let [stacktrace (try (eval-string "(defmacro foo [x] `(subs nil ~x)) (foo 1)")
                          (catch #?(:clj Exception
                                    :cljs js/Error) e
                            (map #(-> %
                                      #_(select-keys [:ns :name :line :column]))
                                 (sci/stacktrace e))))]
      (prn stacktrace))))

(deftest locals-test
  (testing "defn does not introduce fn-named local binding"
    (let [locals
          (try (eval-string "(defn foo [x] (subs nil 0)) (foo :x)")
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) e
                 (:locals (ex-data e))))
          ks (keys locals)]
      (is (= '[x] ks)))))

(deftest arity-error-test
  (testing "The name of the correct function is reported"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
         #"Wrong number of args \(0\) passed to: foo/echo-msg"
         (eval-string "
(ns foo)

(defn echo-msg [msg]
  msg)

(ns bar (:require foo))

(defn main []
  (foo/echo-msg))  ;; called with the wrong arity

(main)")))))

(deftest inherited-ex-data-is-encapsulated
  (testing "The original ex-data is encapsulated."
    (is (= [{:column 22
             :file nil
             :line 2
             :locals {}
             :message "ex-message"
             :type :sci/error}
            {:column 3}]
           (try
             (eval-string "
(defn throwing-fn [] (throw (ex-info \"ex-message\" {:column 3})))

(throwing-fn)")
             (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) e
               [(dissoc (ex-data e) :sci.impl/callstack)
                (ex-data #?(:clj (.getCause e)
                            :cljs (ex-cause e)))]))))))
