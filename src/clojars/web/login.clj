(ns clojars.web.login
  (:require [clojars.web.common :refer [html-doc]]
            [hiccup.element :refer [link-to]]
            [hiccup.form :refer [label text-field
                                 password-field submit-button]]
            [ring.util.response :refer [redirect]]
            [clojars.web.safe-hiccup :refer [form-to]]))

(defn login-form [login_failed username]
  (html-doc
   nil
   "Login"
   [:h1 "Login"]
   [:p "Don't have an account? "
    (link-to "/register" "Sign up!")]

   (when login_failed
     [:div.alert.alert-block
      [:h4 "Login failed"]
      [:p "Incorrect username and/or password."]])
   [:div.span5
    [:div.row
     (form-to [:post "/login"]
              [:div.input-prepend
               [:span.add-on [:i.icon-user]]
               (text-field {:class "span5" :placeholder "Username or email"} :username username)]
              [:br]
              [:div.input-prepend
               [:span.add-on [:i.icon-lock]]
               (password-field {:class "span5" :placeholder "Password"} :password)]
              [:span.help-block.small.pull-right (link-to "/forgot-password" "Forgot password?")]
              (submit-button {:class "btn btn-large"} "Login"))]]))
