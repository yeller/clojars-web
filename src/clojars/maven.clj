(ns clojars.maven
  (:require [clojure.java.io :as io]
            [clojars.config :refer [config]]
            [clojure.string :refer [split]]
            [clj-stacktrace.repl :refer [pst]])
  (:import org.apache.maven.model.io.xpp3.MavenXpp3Reader
           org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
           java.io.IOException))

(defn model-to-map [model]
  {:jar_name (or (.getArtifactId model)
                 (-> model .getParent .getArtifactId))
   :group_name (or (.getGroupId model)
                   (-> model .getParent .getGroupId))
   :version (.getVersion model)
   :description (.getDescription model)
   :url (.getUrl model)
   :scm (.getScm model)
   :licenses (.getLicenses model)
   :authors (vec (map #(.getName %) (.getContributors model)))
   :dependencies (vec (map
                       (fn [d] {:group_name (.getGroupId d)
                               :jar_name (.getArtifactId d)
                               :version (.getVersion d)
                               :scope (or (.getScope d) "compile")})
                       (.getDependencies model))) })

(defn read-pom
  "Reads a pom file returning a maven Model object."
  [file]
  (with-open [reader (io/reader file)]
    (.read (MavenXpp3Reader.) reader)))

(def pom-to-map (comp model-to-map read-pom))

(defn read-metadata
  "Reads a maven-metadata file returning a maven Metadata object."
  [file]
  (with-open [reader (io/reader file)]
    (.read (MetadataXpp3Reader.) reader)))

(defn snapshot-version
  "Get snapshot version from maven-metadata.xml used in pom filename"
  [file]
  (let [snapshot (-> (read-metadata file) .getVersioning .getSnapshot)]
    (str (.getTimestamp snapshot) "-" (.getBuildNumber snapshot))))

(defn directory-for
  "Directory for a jar under repo"
  [{:keys [group_name jar_name version]}]
  (apply io/file
         (if version
           (concat [(config :repo)] (split group_name #"\.") [jar_name version])
           (concat [(config :repo)] (split group_name #"\.") [jar_name]))))

(defn recommended-version
  [group-id artifact-id]
  (let [file (io/file (directory-for {:group_name group-id :jar_name artifact-id})
                      "maven-metadata.xml")
        versioning (-> (read-metadata file) .getVersioning)]
    (or (.getRelease versioning)
        (last (.getVersions versioning)))))

(defn versions
  [group-id artifact-id]
  (let [file (io/file (directory-for {:group_name group-id :jar_name artifact-id})
                      "maven-metadata.xml")]
    (-> (read-metadata file) .getVersioning .getVersions)))

(defn snapshot-pom-file [{:keys [jar_name version] :as jar}]
  (let [metadata-file (io/file (directory-for jar) "maven-metadata.xml")
        snapshot (snapshot-version metadata-file)
        filename (format "%s-%s-%s.pom" jar_name (re-find #"\S+(?=-SNAPSHOT$)" version) snapshot)]
    (io/file (directory-for jar) filename)))

(defn jar-to-pom-map [{:keys [jar_name version] :as jar}]
  (try
    (let [pom-file (if (re-find #"SNAPSHOT$" version)
                     (snapshot-pom-file jar)
                     (io/file (directory-for jar) (format "%s-%s.%s" jar_name version "pom")))]
      (pom-to-map (str pom-file)))
    (catch IOException e (pst e) nil)))

(defn commit-url [{:keys [scm]}]
  (let [url (and scm (.getUrl scm))
        base-url (re-find #"https?://github.com/[^/]+/[^/]+" (str url))]
    (if (and base-url (.getTag scm)) (str base-url "/commit/" (.getTag scm)))))
