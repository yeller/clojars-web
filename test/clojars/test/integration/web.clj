(ns clojars.test.integration.web
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojars.test.integration.steps :refer :all]
            [clojars.web :as web]
            [clojars.db :as db]
            [clojars.scp :as scp]
            [clojars.config :refer [config]]
            [clojars.test.test-helper :as help]
            [clojars.event :as ev]
            [net.cgrand.enlive-html :as enlive]
            [cemerick.pomegranate.aether :as aether]))

(use-fixtures :each help/default-fixture)

(deftest server-errors-display-pretty-message
  (with-out-str (-> (session web/clojars-app)
                    (visit "/error")
                    (within [:div.small-section :> :h1]
                            (has (text? "Oops!"))))))

(deftest server-errors-log-caught-exceptions
  (let [output (with-out-str (-> (session web/clojars-app)
                                 (visit "/error")))]
    (is (re-find #"^A server error has occured:.*" output))))

(defn create-temp [content]
  (let [f (doto (java.io.File/createTempFile "clojars.test.integration.web" ".pom")
            (.deleteOnExit))]
    (spit f content)
    f))

(deftest browse-page-renders-multiple-pages
  (doseq [i (range 21)]
    (aether/deploy :coordinates [(keyword (str "tester" i) "test") "0.0.1"]
                   :jar-file (io/resource "test.jar")
                   :pom-file (-> (io/resource "test-0.0.1/test.pom")
                                 slurp
                                 (string/replace "fake" (str "tester" i))
                                 create-temp)
                   :repository {"local" (scp/file-repo (:repo config))})
    (ev/record-deploy {:group-id (str "tester" i)
                       :artifact-id "test"
                       :version "0.0.1"}
                      "xeqi"
                      (io/resource "test.jar")))
   (-> (session web/clojars-app)
     (visit "/projects")
     (within [:div.light-article :> :h1]
             (has (text? "All projects")))
     (within [:.page-description]
             (has (text? "Displaying projects 1 - 20 of 21")))
     (within [:.page-nav :.current]
             (has (text? "1")))
     (within [:span.desc]
             (has (text? (reduce str (repeat 20 "Huh")))))
     (follow "2")
     (within [:.page-description]
             (has (text? "Displaying projects 21 - 21 of 21")))
     (within [:span.desc]
             (has (text? "Huh")))
     (within [:.page-nav :.current]
             (has (text? "2")))))

(deftest browse-page-can-jump
  (doseq [i (range 100 125)]
    (aether/deploy :coordinates [(keyword (str "tester" i "a") "test") "0.0.1"]
                   :jar-file (io/resource "test.jar")
                   :pom-file (-> (io/resource "test-0.0.1/test.pom")
                                 slurp
                                 (string/replace "fake" (str "tester" i "a"))
                                 create-temp)
                   :repository {"local" (scp/file-repo (:repo config))})
    (ev/record-deploy {:group-id (str "tester" i "a")
                      :artifact-id "test"
                       :version "0.0.1"}
                      "xeqi"
                      (io/resource "test.jar")))
  (-> (session web/clojars-app)
      (visit "/projects")
      (fill-in "Enter a few letters..." "tester120a/tes")
      (press "Jump")
      (follow-redirect)
      (within [[:ul.row enlive/last-of-type]
               [:li (enlive/nth-of-type 4)]
               [:a (enlive/nth-of-type 2)]]
              (has (text? "tester123a/test")))))
