(ns clojars.db
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojars.event :as ev]
            [clojars.maven :as maven]
            [clojars.config :refer [config]]
            [korma.db :refer [defdb transaction rollback]]
            [korma.core :refer [defentity select group fields order join
                                modifier exec-raw where limit values with
                                has-many raw insert update set-fields offset]]
            [cemerick.friend.credentials :as creds])
  (:import java.security.MessageDigest
           java.util.Date
           java.io.File))

(def ^{:private true} ssh-options
  "no-agent-forwarding,no-port-forwarding,no-pty,no-X11-forwarding")

(def reserved-names
  #{"clojure" "clojars" "clojar" "register" "login"
    "pages" "logout" "password" "username" "user"
    "repo" "repos" "jar" "jars" "about" "help" "doc"
    "docs" "images" "js" "css" "maven" "api"
    "download" "create" "new" "upload" "contact" "terms"
    "group" "groups" "browse" "status" "blog" "search"
    "email" "welcome" "devel" "development" "test" "testing"
    "prod" "production" "admin" "administrator" "root"
    "webmaster" "profile" "dashboard" "settings" "options"
    "index" "files" "releases" "snapshots"})

(def ^{:private true} constituent-chars
  (->> [[\a \z] [\A \Z] [\0 \9]]
       (mapcat (fn [[x y]] (range (int x) (inc (int y)))))
       (map char)
       vec))

(defn rand-string
  "Generates a random string of [A-z0-9] of length n."
  [n]
  (str/join (repeatedly n #(rand-nth constituent-chars))))

(defn get-time []
  (Date.))

(defn bcrypt [s]
  (creds/hash-bcrypt s :work-factor (:bcrypt-work-factor config)))

(defdb mydb (:db config))
(defentity users)
(defentity groups)
(defentity jars)

;; über-hack to work around hard-coded sqlite busy-timeout:
;; https://github.com/ato/clojars-web/issues/105
;; http://sqlite.org/c3ref/busy_timeout.html
;; https://bitbucket.org/xerial/sqlite-jdbc/issue/27
(defonce _
  (alter-var-root #'clojure.java.jdbc.internal/prepare-statement*
                  (fn [prepare]
                    (fn timeout-prepare [& args]
                      (let [stmt (apply prepare args)]
                        (doto stmt
                          ;; Note that while .getQueryTimeout returns
                          ;; milliseconds, .setQueryTimeout takes seconds!
                          (.setQueryTimeout 30)))))))

(defn split-keys [s]
  (map str/trim (str/split s #"\s*\n\s*")))

(defn write-key-file [path]
  (locking (:key-file config)
    (let [new-file (File. (str path ".new"))]
      (with-open [f (io/writer new-file)]
        (doseq [{:keys [user ssh_key]} (select users (fields :user :ssh_key))
                key (remove str/blank? (split-keys ssh_key))]
          (.write f (str "command=\"ng --nailgun-port 8700 clojars.scp " user
                         "\"," ssh-options " " key "\n"))))
      (.renameTo new-file (File. path)))))

(defn find-user [username]
  (if-let [u (get @ev/users username)]
    (-> u
        (clojure.set/rename-keys {:username :user
                                  :ssh-key :ssh_key
                                  :pgp-key :pgp_key})
        (dissoc :groups))))

(defn find-user-by-user-or-email [username-or-email]
  (if-let [u (get @ev/users username-or-email
                  (get @ev/users username-or-email))]
    (-> u
        (clojure.set/rename-keys {:username :user
                                  :ssh-key :ssh_key
                                  :pgp-key :pgp_key})
        (dissoc :groups))))

(defn find-groupnames [username]
  (get-in @ev/users [username :groups]))

(defn group-membernames [groupname]
  (get-in @ev/memberships [groupname]))

(defn group-keys [groupname]
  (filter identity
          (map (comp :pgp_key find-user)
               (group-membernames groupname))))

(defn projects-by-groupname [group-id]
  (let [project {:group_name group-id}]
    (->> (apply io/file (config :repo) (str/split group-id #"\."))
         (.listFiles)
         (filter (memfn isDirectory))
         (map #(assoc project :jar_name (.getName %))))))

(defn jars-by-username [username]
  (concat (for [group (find-groupnames username)]
            (projects-by-groupname group))))

(defn recent-versions [group-id artifact-id]
  (reverse (maven/versions group-id artifact-id)))

;; rename to find-latest-jar? should the special snapshot handling be
;; in another defn?
(defn find-jar
  ([group-id artifact-id]
     (try
       (find-jar group-id artifact-id (maven/recommended-version group-id artifact-id))
       (catch java.io.IOException e
         ;;catch and hide exception so that callee doesn't die
         )))
  ([group-id artifact-id version]
     (try
       (if-let [artifact (maven/jar-to-pom-map {:group_name group-id
                                                :jar_name artifact-id
                                                :version version})]
         (let [deploy (-> @ev/deploys
                          (get-in [group-id artifact-id version])
                          last)]
           (-> artifact
               (assoc :created (:at deploy))
               (assoc :user (:deployed-by deploy)))))
       (catch java.io.IOException e
         ;;catch and hide exception so that callee doesn't die
         ))))

(defn all-projects []
  (for [f (file-seq (io/file (config :repo)))
        :when (and (= (.getName f) "maven-metadata.xml")
                   (not (re-find #"-SNAPSHOT" (.getParent f))))
        :let [m (maven/read-metadata f)]]
    {:group_name (.getGroupId m)
     :jar_name (.getArtifactId m)
     :created (.lastModified f)}))

(defn recent-jars []
  (take 5 (sort-by (comp - :created) (all-projects))))

(defn count-all-projects []
  (count (all-projects)))

(defn count-projects-before [s]
  (count (take-while #(< 0 (compare s %))
                     (sort
                      (map #(str (:group_name %) "/" (:jar_name %))
                           (all-projects))))))

(defn browse-projects [current-page per-page]
  (vec
    (map
      #(find-jar (:group_name %) (:jar_name %))
      (take per-page
          (drop (* (- current-page 1) per-page)
                (sort-by #(str (:group_name %) "/" (:jar_name %))
                         (all-projects)))))))

(defn add-user [email username password ssh-key pgp-key]
  (let [record {:email email, :user username, :password (bcrypt password)
                :ssh_key ssh-key, :pgp_key pgp-key}
        group (str "org.clojars." username)]
    (insert users (values (assoc record
                            :created (get-time)
                            ;;TODO: remove salt field
                            :salt "")))
    (insert groups (values {:name group :user username}))
    (ev/record :user (clojure.set/rename-keys record {:user :username
                                                      :ssh_key :ssh-key
                                                      :pgp_key :pgp-key}))
    (ev/record :membership {:group-id group :username username :added-by nil})
    (write-key-file (:key-file config))))

(defn update-user [account email password ssh-key pgp-key]
  (let [fields {:email email
                :user account
                :ssh_key ssh-key
                :pgp_key pgp-key}
        fields (if (empty? password)
                 fields
                 (assoc fields :password (bcrypt password)))]
    (update users
            (set-fields (assoc fields :salt ""))
            (where {:user account}))
    (ev/record :user (clojure.set/rename-keys fields {:user :username
                                                      :ssh_key :ssh-key
                                                      :pgp_key :pgp-key})))
  (write-key-file (:key-file config)))

(defn add-member [group-id username added-by]
  (insert groups
          (values {:name group-id
                   :user username
                   :added_by added-by}))
  (ev/record :membership {:group-id group-id :username username
                          :added-by added-by}))

(defn check-and-add-group [account groupname]
  (when-not (re-matches #"^[a-z0-9-_.]+$" groupname)
    (throw (Exception. (str "Group names must consist of lowercase "
                            "letters, numbers, hyphens, underscores "
                            "and full-stops."))))
  (let [members (group-membernames groupname)]
    (if (empty? members)
      (if (reserved-names groupname)
        (throw (Exception. (str "The group name "
                                groupname
                                " is already taken.")))
        (add-member groupname account "clojars"))
      (when-not (some #{account} members)
        (throw (Exception. (str "You don't have access to the "
                                groupname " group.")))))))

(defn add-jar [account {:keys [group_name jar_name version
                               description url authors]}]
  (insert jars
          (values {:group_name group_name
                   :jar_name   jar_name
                   :version    version
                   :user       account
                   :created    (get-time)
                   :description description
                   :homepage   url
                   :authors    (str/join ", " (map #(.replace % "," "")
                                                   authors))})))
