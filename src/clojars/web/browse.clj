(ns clojars.web.browse
  (:require [clojars.web.common :refer [html-doc jar-link user-link
                                        page-nav page-description jar-name
                                        collection-fork-notice]]
            [clojars.db :refer [browse-projects count-all-projects
                                count-projects-before]]
            [hiccup.form :refer [label submit-button]]
            [ring.util.response :refer [redirect]]))

(defn browse-page [account page per-page]
  (let [project-count (count-all-projects)
        total-pages (-> (/ project-count per-page) Math/ceil .intValue)
        projects (browse-projects page per-page)]
    (html-doc
     account
     "All projects"
     [:h1 "All projects"]
     [:form.form-search {:method :get :action "/projects"}
      (label :from "starting from")
      [:div.input-append
       [:input.search-query.span2
        {:type :text :name :from :id :from
         :placeholder "Enter a few letters"}]
       [:button.btn {:type :submit :id :jump} "Jump"]]]
     collection-fork-notice
     (page-description page per-page project-count)
     [:ul
      (for [[i jar] (map-indexed vector projects)]
        [:li
         [:h4 (jar-link jar)]
         (when (seq (:description jar))
           [:p (:description jar)])])]
     (page-nav page total-pages))))

(defn browse [account params]
  (let [per-page 20]
    (if-let [from (:from params)]
      (let [i (count-projects-before from)
            page (inc (int (/ i per-page)))]
        (redirect (str "/projects?page=" page "#" (mod i per-page))))
      (browse-page account (Integer. (or (:page params) 1)) per-page))))
