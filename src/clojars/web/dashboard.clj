(ns clojars.web.dashboard
  (:require [clojars.web.common :refer [html-doc jar-link group-link tag]]
            [clojars.db :refer [jars-by-groupname find-groupnames recent-jars find-user]]
            [hiccup.element :refer [unordered-list link-to image]]
            [clavatar.core :as clavatar]))

(defn index-page [account]
  (html-doc
   account
   nil
   [:div.row
    [:div.span12
     [:h1 "Clojars is a " [:strong "dead easy"] " community repository for"
      " open source " (link-to "http://clojure.org/" "Clojure")
      " libraries. "]
     [:div.tabbable
      [:ul.nav.nav-tabs
       [:li.active
        [:a {:href "#lein"
             :data-toggle "tab"} "leiningen"]]
       [:li
        [:a {:href "#maven"
             :data-toggle "tab"} "maven"]]]
      [:div.tab-content
       [:div#lein.tab-pane.fade.in.active
        [:div.row
         [:div.span6
          [:h2 "Use"]
          [:pre (tag "[") "ring \"1.1.2\"" (tag "]")]]
         [:div.span6
          [:h2 "Share"]
          [:ol
           [:li
            [:p (link-to "/register" "Create an account")]]
           [:li
            [:pre (tag "$") " lein deploy clojars"]]]
          [:p "See an extended example at the " (link-to "http://wiki.github.com/ato/clojars-web/tutorial" "tutorial")]]]]
       [:div#maven.tab-pane.fade
        [:div.row
         [:div.span6
          [:h2 "Use"]
          [:pre
           (tag "<repository>\n")
           (tag "  <id>") "clojars.org" (tag "</id>\n")
           (tag "  <url>") "http://clojars.org/repo" (tag "</url>\n")
           (tag "</repository>")]
          [:pre
           (tag "<dependency>\n")
           (tag "  <groupId>") "ring" (tag "</groupId>\n")
           (tag "  <artifactId>") "ring" (tag "</artifactId>\n")
           (tag "  <version>") "1.1.2" (tag "</version>\n")
           (tag "</dependency>\n")]]
         [:div.span6
          [:h2 "Share"]
          [:ol
           [:li
            [:p (link-to "/register" "Create an account")]]
           [:li
            [:pre "...."]]]]]]]]
     [:h2 "Recently pushed jars"]
     (unordered-list (map jar-link (recent-jars)))
     (link-to {:class "btn btn-success"} "/projects" "Browse the repository >>")]] ))

(defn dashboard [account]
  (html-doc
   account
   account
   [:h1 [:span (image (clavatar/gravatar (:email (find-user account))
                                         :size 200
                                         :default :mm))]
    account " dashboard"]
   [:div.row
    [:div.span6
     [:h2 "Groups"]
     (unordered-list (map group-link (find-groupnames account)))]
    [:div.span6
     [:h2 "Artifacts"]
     (unordered-list (for [groupname (find-groupnames account)
                           artifact (jars-by-groupname groupname)]
                       (jar-link artifact)))
     (link-to "http://wiki.github.com/ato/clojars-web/pushing" "add new jar")]])
  )
