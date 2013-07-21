(ns clojars.test.unit.db
  (:require [clojars.db :as db]
            [clojure.java.jdbc :as jdbc]
            [clojars.test.test-helper :as help]
            [clojure.test :refer :all]))

(use-fixtures :each help/default-fixture help/index-fixture)

(defn submap [s m]
  (every? (fn [[k v]] (= (m k) v)) s))

(deftest added-users-can-be-found
  (let [email "test@example.com"
        name "testuser"
        password "password"
        ssh-key "asdf"
        pgp-key "aoeu"
        ms (long 0)]
      (is (db/add-user email name password ssh-key pgp-key))
      (are [x] (submap {:email email
                        :user name
                        :ssh_key ssh-key}
                       x)
           (db/find-user name)
           (db/find-user-by-user-or-email name)
           (db/find-user-by-user-or-email email))))

(deftest user-does-not-exist
  (is (not (db/find-user-by-user-or-email "test2@example.com"))))

(deftest updated-users-can-be-found
  (let [email "test@example.com"
        name "testuser"
        password "password"
        ssh-key "asdf"
        pgp-key "aoeu"
        ms (long 0)
        email2 "test2@example.com"
        name2 "testuser2"
        password2 "password2"
        ssh-key2 "asdf2"
        pgp-key2 "aoeu2"]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. ms))]
      ;;TODO: What should be done about the key-file?
      (is (db/add-user email name password ssh-key pgp-key))
      (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 1)))]
        ;;TODO: What should be done about the key-file?
        (is (db/update-user name email2 name2 password2 ssh-key2 pgp-key2))
        (are [x] (submap {:email email2
                          :user name2
                          :ssh_key ssh-key2
                          :pgp_key pgp-key2
                          :created ms}
                         x)
             (db/find-user name2)
             (db/find-user-by-user-or-email name2)
             (db/find-user-by-user-or-email email2)))
      (is (not (db/find-user name))))))

(deftest added-users-are-added-only-to-their-org-clojars-group
  (let [email "test@example.com"
        name "testuser"
        password "password"
        ssh-key "asdf"
        pgp-key "aoeu"]
    ;;TODO: What should be done about the key-file?
    (is (db/add-user email name password ssh-key pgp-key))
    (is (= ["testuser"]
           (db/group-membernames (str "org.clojars." name))))
    (is (= ["org.clojars.testuser"]
           (db/find-groupnames name)))))

(deftest users-can-be-added-to-groups
  (let [email "test@example.com"
        name "testuser"
        password "password"
        ssh-key "asdf"
        pgp-key "aoeu"]
    ;;TODO: What should be done about the key-file?
    (db/add-user email name password ssh-key pgp-key)
    (db/add-member "test-group" name "some-dude")
    (is (= ["testuser"] (db/group-membernames "test-group")))
    (is (some #{"test-group"} (db/find-groupnames name)))))

(deftest check-and-add-group-validates-group-name-format
  (is (thrown? Exception (db/check-and-add-group "test-user" nil)))
  (is (thrown? Exception (db/check-and-add-group "test-user" "HI")))
  (is (thrown? Exception (db/check-and-add-group "test-user" "lein*")))
  (is (thrown? Exception (db/check-and-add-group "test-user" "lein=")))
  (is (thrown? Exception (db/check-and-add-group "test-user" "lein>")))
  (is (thrown? Exception (db/check-and-add-group "test-user" "べ")))
  (db/check-and-add-group "test-user" "hi")
  (db/check-and-add-group "test-user" "hi-")
  (db/check-and-add-group "test-user" "hi_1...2"))

(deftest check-and-add-group-validates-group-name-is-not-reserved
  (doseq [group db/reserved-names]
    (is (thrown? Exception (db/check-and-add-group "test-user" group)))))

(deftest check-and-add-group-validates-group-permissions
  (db/add-member "group-name" "some-user" "some-dude")
  (is (thrown? Exception (db/check-and-add-group "test-user" "group-name"))))


(deftest check-and-add-group-creates-single-member-group-for-user
  (is (empty? (db/group-membernames "group-name")))
  (db/check-and-add-group "test-user" "group-name")
  (is (= ["test-user"] (db/group-membernames "group-name")))
  (is (= ["group-name"]
         (db/find-groupnames "test-user"))))
