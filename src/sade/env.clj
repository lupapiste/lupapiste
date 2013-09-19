(ns sade.env
  (:require [sade.util :refer :all]
            [sade.strings :refer [numeric?]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [me.raynes.fs :as fs]
            [monger.collection :as mc]
            [swiss-arrows.core :refer :all])
  (:import [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
           [org.jasypt.properties EncryptableProperties]))

(defn- try-to-open [in]
  (when in
    (try
      (io/input-stream in)
      (catch java.io.FileNotFoundException _
        nil))))

(def buildinfo (read-string (slurp (io/resource "buildinfo.clj"))))
(defn hgnotes [] (read-string (slurp (io/resource "hgnotes.clj"))))

(defn- parse-target-env [buildinfo] (or (re-find #"[PRODEVTSQA]+" (or buildinfo "")) "local"))
(def target-env (parse-target-env (:build-tag buildinfo)))

(defn- make-decryptor [password]
  (EncryptableProperties.
    (doto (StandardPBEStringEncryptor.)
      (.setAlgorithm "PBEWITHSHA1ANDDESEDE") ; SHA-1 & Triple DES is supported by most JVMs out of the box.
      (.setPassword password))))

(defn read-value [s]
  (cond
    (.equalsIgnoreCase "true" s) true
    (.equalsIgnoreCase "false" s) false
    (numeric? s) (Long/parseLong s)
    :else s))

(defn- read-config [password in]
  (when-let [in (try-to-open in)]
    (try
      (let [decryptor (make-decryptor password)]
        (.load decryptor in)
        (doall
          (reduce
            (fn [m k]
              (assoc-in m (map keyword (s/split k #"\.")) (read-value (.getProperty decryptor k))))
            {}
            (keys decryptor))))
      (finally
        (try (.close in) (catch Exception _))))))

(defn- read-all-configs []
  (let [password (or (System/getProperty "lupapiste.masterpassword") (System/getenv "LUPAPISTE_MASTERPASSWORD") "lupapiste")]
    (reduce merge (map (partial read-config password)
                    [(io/resource "lupapiste.properties")
                     (io/resource (str (s/lower-case target-env) ".properties"))
                     (io/file "lupapiste.properties")
                     (io/file (System/getProperty "lupapiste.properties"))
                     (io/file (System/getenv "LUPAPISTE_PROPERTIES"))]))))

(def ^:private config (atom (read-all-configs)))

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
(def port (->int (get-prop "lupapiste.port" "8000")))
(def log-level (keyword (get-prop "lupapiste.loglevel" (if (= mode :dev) "debug" "info"))))
(def log-dir (get-prop "lupapiste.logdir" (if (= mode :dev) "target" "")))
(def perf-mon-on (Boolean/parseBoolean (str (get-prop "lupapiste.perfmon" "false"))))
(def proxy-off (atom (Boolean/parseBoolean (str (get-prop "lupapiste.proxy-off" "false")))))

(defn dev-mode? []
  (= :dev mode))

(defn test-build? []
  (= target-env "TEST"))

(def ^:dynamic *in-dev-macro* false)

(defmacro in-dev [& body]
  `(if (dev-mode?)
     (binding [*in-dev-macro* true]
       (do ~@body))))
