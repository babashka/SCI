(ns sci.examples.repl
  (:require [clojure.string :as str]
            [sci.core :as sci]))

(js/require "process")

(defn prompt [ctx]
  (let [ns-name (sci/eval-string* ctx "(ns-name *ns*)")]
    (.write js/process.stdout (str ns-name "> "))))

(defn await-input []
  (.write js/process.stdout "> "))

(defn handle-error [_ctx stdin last-error e]
  (.write js/process.stderr (str (.-message e) "\n"))
  (sci/alter-var-root last-error (constantly e))
  ;; ignore remaining input on error
  (reset! stdin ""))

(defn -main [& _args]
  (let [stdin (atom "")
        ;; because eval is async, dynamic bindings don't work
        ;; therefore we use an atom to keep track of the latest ns
        last-ns (atom @sci/ns)
        ;; same for the latest error
        last-error (sci/new-var '*e nil {:ns (sci/create-ns 'clojure.core)})
        ctx (sci/init {:namespaces {'clojure.core {'*e last-error}}})]
    (prompt ctx)
    (.setEncoding js/process.stdin "utf8")
    (.on js/process.stdin "data"
         (fn [data]
           (swap! stdin #(str % data))
           (sci/with-bindings {sci/ns @last-ns}
             (loop []
               (let [input @stdin
                     reader (sci/reader input)
                     ;; read the next form from stdin
                     next-form (try (sci/parse-next ctx reader)
                                    (catch :default e
                                      (if (str/includes? (.-message e) "EOF while reading")
                                        ::eof
                                        (do (handle-error ctx stdin last-error e)
                                            ::err))))]
                 (if (= ::eof next-form) (await-input)
                     (do
                       (if (= ::err next-form)
                         ;; on error, ignore rest of stdin
                         (reset! stdin "")
                         ;; else, only ignore the input we already processed
                         (let [last-line (sci/get-line-number reader)
                               last-col (sci/get-column-number reader)
                               lines (str/split-lines input)
                               remaining-lines (subs (str/join (drop (dec last-line) lines))
                                                     last-col)]
                           (reset! stdin remaining-lines)))
                       ;; if we did not reach end of file (the user pressed ctrl-d)
                       (when-not (or
                                  (= ::err next-form)
                                  (= ::sci/eof next-form))
                         ;; eval the form and print the result
                         (let [res (try (sci/eval-form ctx next-form)
                                        (catch :default e
                                          (handle-error ctx stdin last-error e)
                                          ::err))
                               ns (sci/eval-string* ctx "*ns*")]
                           (reset! last-ns ns)
                           (when-not (= ::err res)
                             (prn res))))
                       (prompt ctx)
                       (when-not (str/blank? @stdin)
                         (recur)))))))))))

(set! *main-cli-fn* -main)

;; compile:
;; clojure -A:examples -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.10.597"}}}' -m cljs.main -t node -c sci.examples.repl
;; run:
;; rlwrap node out/main.js
