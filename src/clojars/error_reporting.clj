(ns clojars.error-reporting
  (:require [yeller.clojure.ring :as yeller-ring]
            [yeller.clojure.client :as yeller]
            [clojars.config :refer [config]]
            [clj-stacktrace.repl :refer [pst]]))

(def client
  (delay
    (when-let [token (:yeller-token config)]
      (println "clojars-web: enabling yeller client")
      (yeller/client {:token token}))))

(defn report-error
  ([e]
   (report-error e nil))
  ([e extra]
   (pst e)
   (when-let [c @client]
     (println "REPORTING" e)
     (yeller/report c e (assoc extra :environment (:yeller-environment config))))))

(defn report-ring-error [e request]
  (report-error e (yeller-ring/format-extra nil request)))

(defn register-global-exception-handler! []
  (when-let [c @client]
    (Thread/setDefaultUncaughtExceptionHandler c)))
