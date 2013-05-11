(ns clojars.web.common
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]
            [hiccup.element :refer [link-to unordered-list image]]
            [clojars.web.safe-hiccup :refer [html5 raw form-to]]
            [clojars.db :as db]
            [clavatar.core :as clavatar]))

(defn when-ie [& contents]
  (str
   "<!--[if lt IE 9]>"
   (html contents)
   "<![endif]-->"))

(defn html-doc [account title & body]
  (html5
   [:head
    [:link {:type "application/opensearchdescription+xml"
            :href "/opensearch.xml"
            :rel "search"}]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0"}]
    [:title
     (when title
       (str title " - "))
     "Clojars"]
    (include-css "/bootstrap/css/bootstrap.min.css")
    (include-css "/stylesheets/screen.css")
    (raw (when-ie (include-js "/js/html5.js")))]
   [:body
    [:header
     [:div.container
      [:div.navbar
       [:div.navbar-inner
        [:h1.pull-left (link-to "/" "Clojars")
         [:small "clojure community jar repository"]]
        (if account
          [:ul.nav.pull-right
           [:li (link-to "/" "dashboard")]
           [:li (link-to "/profile" "profile")]
           [:li (link-to "/logout" "logout")]]
          [:ul.nav.pull-right
           [:li (link-to "/login" "login")]
           [:li (link-to "/register" "register")]])
        [:form.navbar-search.pull-right.form-search
         {:method "GET"
          :action "/search"}
         [:div.input-append
          [:input.search-query
           {:name "q" :id "search"
            :placeholder "Search jars..."
            :type "text"}]
          [:button {:type "submit"
                    :class "btn"} [:i.icon-search]]]]]]]]
    [:article
     [:div.container body]]
    [:footer
     [:div.container
      [:div.navbar
       [:div.navbar-inner
        [:ul.nav
         (for [x [(link-to "https://github.com/ato/clojars-web/wiki/About" "about")
                  (link-to "/projects" "projects")
                  (link-to "https://github.com/ato/clojars-web/blob/master/NEWS.md" "news")
                  (link-to "https://github.com/ato/clojars-web/wiki/Contact" "contact")
                  (link-to "https://github.com/ato/clojars-web" "code")
                  (link-to "/security" "security")
                  (link-to "https://github.com/ato/clojars-web/wiki/" "help")]]
           [:li x])]]]]]
    (include-js "/jquery/jquery.min.js")
    (include-js "/bootstrap/js/bootstrap.min.js")
    (include-js "/js/site.js")]))

(defn flash [msg]
  (if msg
    [:div.alert.alert-success msg]))

(defn error-list [errors]
  (when errors
    [:div {:class :alert}
     [:strong "Blistering barnacles!"]
     "  Something's not shipshape:"
     (unordered-list errors)]))

(defn tag [s]
  (raw (html [:span {:class "tag"} s])))

(defn jar-url [jar]
  (if (= (:group_name jar) (:jar_name jar))
    (str "/" (:jar_name jar))
    (str "/" (:group_name jar) "/" (:jar_name jar))))

(defn jar-name [jar]
  (if (= (:group_name jar) (:jar_name jar))
    (:jar_name jar)
    (str (:group_name jar) "/" (:jar_name jar))))

(defn jar-fork? [jar]
  (re-find #"^org.clojars." (or
                             (:group_name jar)
                             (:group-id jar)
                             "")))

(def single-fork-notice
  [:em.muted
   "Note: this artifact is a non-canonical fork. See "
   (link-to "https://github.com/ato/clojars-web/wiki/Groups" "the wiki")
   " for more details."])

(def collection-fork-notice
  [:em.muted
   "Note: artifacts in italics are non-canonical forks. See "
   (link-to "https://github.com/ato/clojars-web/wiki/Groups" "the wiki")
   " for more details."])

(defn jar-link [jar]
 [(if (jar-fork? jar) :em :span)
   (link-to (jar-url jar) (jar-name jar))])

(defn user-link [username]
  (let [user (db/find-user username)]
    (link-to (str "/users/" username)
             [:span (image (clavatar/gravatar (:email user)
                                              :default :mm))
              [:span username]])))

(defn group-link [groupname]
  (link-to (str "/groups/" groupname) groupname))

(defn format-date [s]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") s))

(defn simple-date [s]
  (.format (java.text.SimpleDateFormat. "MMM d, yyyy") s))

(defn page-nav [current-page total-pages]
  (let [previous-text (raw "&laquo;")
        next-text (raw "&raquo;")
        page-range 3
        page-url "/projects?page="
        current-page (-> current-page (max 1) (min total-pages))
        main-div [:div {:class "pagination"}]
        previous-page [[(if (= current-page 1)
                          :li.disabled
                          :li)
                        (link-to (str page-url (- current-page 1))
                                 previous-text)]]
        before-current (->> (drop-while
                             #(< % 1)
                             (range (- current-page page-range) current-page))
                            (map #(vector :li
                                          (link-to (str page-url %) %))))
        current [[:li.active (link-to (str page-url current-page)
                                      (str current-page))]]
        after-current (->> (take-while
                            #(<= % total-pages)
                            (range (+ current-page 1) (+ current-page 1 page-range)))
                           (map #(vector :li
                                         (link-to (str page-url %) %))))
        next-page [[(if (= current-page total-pages)
                          :li.disabled
                          :li)
                        (link-to (str page-url (+ current-page 1))
                                 next-text)]]]
    [:div.pagination
     [:ul
      (concat previous-page
              before-current
              current
              after-current
              next-page)]]))

(defn page-description [current-page per-page total]
  (let [total-pages (-> (/ total per-page) Math/ceil .intValue)
        current-page (-> current-page (max 1) (min total-pages))
        upper (* per-page current-page)]
   [:div {:class "page-description"}
     "Displaying projects "
     [:b (str (-> upper (- per-page) inc) " - " (min upper total))]
     " of "
     [:b total]]))
