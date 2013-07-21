(ns clojars.web.legacy
  (:require [compojure.core :refer [GET POST defroutes]]
            [clojars.auth :as auth]
            [clojars.db :as db]
            [ring.util.response :as resp]))

(defroutes routes
  (GET "/:username" [username]
       (if (db/find-user username)
         (resp/redirect (str "/user/" username)))))
