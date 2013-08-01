(ns sade.env
  (:use [sade.util]
        [sade.strings :only [numeric?]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [me.raynes.fs :as fs])
  (:import [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
           [org.jasypt.properties EncryptableProperties]))

(def buildinfo (read-string (slurp (io/resource "buildinfo.clj"))))

(defn hgnotes [] (read-string (slurp (io/resource "hgnotes.clj"))))

(def mongo-connection-info
  (if (fs/exists? "mongo-connection.clj")
    {:mongodb (read-string (slurp (io/file "mongo-connection.clj")))}
    {}))

(defn- parse-target-env [build-tag]
  (or (re-find #"[PRODEVTSQA]+" (or build-tag "")) "local"))

(def target-env (parse-target-env (:build-tag buildinfo)))

; TODO rewrite? Perhaps determine from mode or env parameter?
(def ^:private prop-file (-> target-env (s/lower-case) (str ".properties")))

(defn read-value [s]
  (cond
    (.equalsIgnoreCase "true" s) true
    (.equalsIgnoreCase "false" s) false
    (numeric? s) (Long/parseLong s)
    :default s))

(defn read-config
  ([file-name]
    (read-config file-name (or (System/getProperty "lupapiste.masterpassword") (System/getenv "LUPAPISTE_MASTERPASSWORD") "lupapiste")))
  ([file-name password]
    (let [decryptor (EncryptableProperties. (doto (StandardPBEStringEncryptor.)
                                              (.setAlgorithm "PBEWITHSHA1ANDDESEDE") ; SHA-1 & Triple DES is supported by most JVMs out of the box.
                                              (.setPassword password)))]
      (with-open [resource (io/input-stream (io/resource file-name))]
        (.load decryptor resource)
        (merge
          (clojure.walk/keywordize-keys
            (apply deep-merge-with into
                   (for [[k _] decryptor
                         ; _ contains "ENC(...)" value, decryption using getProperty
                         :let [v (.getProperty decryptor k)]]
                     (assoc-in {} (clojure.string/split k #"\.") (read-value v)))))
          mongo-connection-info)))))

(def ^:private config (atom (read-config prop-file)))

(defn get-config [] @config)

(defn value
  "Returns a value from config."
  [& keys]
  (get-in (get-config) (flatten [keys])))

(defn feature?
  "Checks if a feature is enabled."
  [& keys]
  (->
    (get-config)
    (get-in (cons :feature (into [] keys)))
    str
    read-value
    true?))

(defn set-feature!
  "sets feature value in-memory."
  [value path] (swap! config assoc-in (concat [:feature] (map keyword path)) value))

(defn enable-feature!
  "enables feature value in-memory."
  [& feature] (set-feature! true feature))

(defn disable-feature!
  "disables feature value in-memory."
  [& feature] (set-feature! false feature))

(defn features
  "Returns a list of all enabled features."
  []
  (walk/prewalk
    (fn [x]
      (if (map? x)
        (into {}
          (for [[k v] x]
            [k (if (map? v) v (-> v str read-value true?))]))
        x))
    (:feature (get-config))))

(defn- get-prop [prop-name default]
  (or
    (get-in (get-config) (map keyword (s/split prop-name #"\.")))
    (System/getProperty prop-name)
    (System/getenv (-> prop-name (s/replace \. \_) (s/upper-case)))
    default))

(def mode (keyword (get-prop "lupapiste.mode" "dev")))
(def port (Integer/parseInt (get-prop "lupapiste.port" "8000")))
(def log-level (keyword (get-prop "lupapiste.loglevel" (if (= mode :dev) "debug" "info"))))
(def log-dir (get-prop "lupapiste.logdir" (if (= mode :dev) "target" "")))
(def perf-mon-on (Boolean/parseBoolean (str (get-prop "lupapiste.perfmon" "false"))))
(def proxy-off (atom (Boolean/parseBoolean (str (get-prop "lupapiste.proxy-off" "false")))))

(defn dev-mode? []
  (= :dev mode))

(def ^:dynamic *in-dev-macro* false)

(defmacro in-dev [& body]
  `(if (dev-mode?)
     (binding [*in-dev-macro* true]
       (do ~@body))))
