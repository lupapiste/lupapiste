(ns sade.env
  (:use [sade.util :only [deep-merge-with]]
        [sade.strings :only [numeric?]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.walk :as walk])
  (:import [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
           [org.jasypt.properties EncryptableProperties]))

(def buildinfo (read-string (slurp (io/resource "buildinfo.clj"))))

(defn hgnotes [] (read-string (slurp (io/resource "hgnotes.clj"))))

(defn- parse-target-env [build-tag]
  (or (re-find #"[PRODEVTS]+" (or build-tag "")) "local"))

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
      (with-open [resource (clojure.lang.RT/resourceAsStream nil file-name)]
        (.load decryptor resource)
        (clojure.walk/keywordize-keys
          (apply deep-merge-with into
            (for [[k _] decryptor
                  ; _ contains "ENC(...)" value, decryption using getProperty
                  :let [v (.getProperty decryptor k)]]
              (assoc-in {} (clojure.string/split k #"\.") (read-value v)))))))))

(def ^:private config (atom {:last (java.lang.System/currentTimeMillis)
                             :data (read-config prop-file)}))

(defn get-config
  "If value autoreload=true, rereads the configuration file,
   otherwise returns cached configuration. Cache time 10s."
  []
  (let [modified   (-> config deref :last)
        now        (java.lang.System/currentTimeMillis)
        autoreload (-> config deref :data :autoreload str read-value true?)]
    (:data
      (if (and autoreload (> now (+ 10000 modified)))
        (reset! config {:last now
                        :data (read-config prop-file)})
        @config))))

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
