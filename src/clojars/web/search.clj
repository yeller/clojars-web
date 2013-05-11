(ns clojars.web.search
  (:require [clojars.web.common :refer [html-doc jar-link jar-fork?
                                        collection-fork-notice user-link
                                        format-date]]
            [clojars.search :as search]
            [cheshire.core :as json]))

(defn- jar->json [jar]
  (let [m {:jar_name (:artifact-id jar)
           :group_name (:group-id jar)
           :version (:version jar)
           :description (:description jar)}
        created (:at jar)]
    (if created
      (assoc m :created created)
      m)))

(defn json-gen [query]
  (let [results (search/search query)]
    (json/generate-string {:count (count results)
                           :results (map jar->json results)})))

(defn json-search [query]
  {:status 200,
   :headers {"Content-Type" "application/json; charset=UTF-8"}
   :body (json-gen query)})

(defn html-search [account query]
  (html-doc account (str query " - search")
            [:h1 "Search for '" query "'"]
            (try
              (let [results (search/search query)]
                (if (empty? results)
                  [:p "No results."]
                  [:div
                   (if (some jar-fork? results)
                     collection-fork-notice)
                   [:ul
                    (for [jar results]
                      [:li
                       [:h4 (jar-link {:jar_name (:artifact-id jar)
                                       :group_name (:group-id jar)})]
                       (when (seq (:description jar))
                         [:p (:description jar)])])]]))
              (catch Exception _
                (.printStackTrace _)
                [:p "Could not search; please check your query syntax."]))))

(defn search [account params]
  (let [q (params :q)]
    (if (= (params :format) "json")
      (json-search q)
      (html-search account q))))
