(ns sade.env
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clj-time.format :as timeformat]
            [mount.core :as mount :refer [defstate]]
            [sade.core :refer [def-]]
            [sade.util :as util]
            [sade.strings :as ss])
  (:import [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
           [org.jasypt.properties EncryptableProperties]
           [org.joda.time DateTime]
           [java.io FileNotFoundException InputStream]))

(defn- ^InputStream try-to-open [in]
  (when in
    (try (io/input-stream in)
         (catch FileNotFoundException _ nil))))

(def buildinfo (read-string (slurp (io/resource "buildid.edn"))))

(defn- parse-target-env [buildinfo]
  (or (second (re-find #"-\s*([PRODEVTSQAprodevtsqa]{2,10})" (str buildinfo)))
      (when (re-find #"instant-smokes" (str buildinfo)) "prod")
      "local"))

(def target-env (parse-target-env (:build-tag buildinfo)))

(def build-number (str (->> (:time buildinfo)
                            (DateTime.)
                            (timeformat/unparse (timeformat/formatter "YYYYMMdd")))
                       "-"
                       (:build-number buildinfo)))

(def file-separator (System/getProperty "file.separator"))

(def- master-password
  (or (let [password-file (io/file (str (System/getProperty "user.home") file-separator
                                        "application_master_password.txt"))]
        (when (.exists password-file)
          (ss/trim-newline (slurp password-file))))
      (System/getProperty "application.masterpassword")
      (System/getenv "APPLICATION_MASTER_PASSWORD")))

(defn- ^EncryptableProperties make-decryptor [password]
  (EncryptableProperties.
    (doto (StandardPBEStringEncryptor.)
      (.setAlgorithm "PBEWITHSHA1ANDDESEDE")                ; SHA-1 & Triple DES is supported by most JVMs out of the box.
      (.setPassword password))))

(defn read-value [s]
  (cond
    (.equalsIgnoreCase "true" s) true
    (.equalsIgnoreCase "false" s) false
    (ss/numeric? s) (Long/parseLong s)
    (ss/decimal-number? s) (Double/parseDouble s)
    :else s))

(defn- read-config [password in]
  (when-let [in (try-to-open in)]
    (try
      (let [decryptor (make-decryptor password)]
        (.load decryptor in)
        (doall
          (reduce
            (fn [m k]
              (assoc-in m (map keyword (ss/split k #"\.")) (read-value (.getProperty decryptor k))))
            {}
            (keys decryptor))))
      (finally
        (try (.close in) (catch Exception _))))))

(defn- read-all-configs [& _]
  (reduce util/deep-merge (map (partial read-config master-password)
                               [(io/resource "lupapiste.properties")
                                (io/resource (str (ss/lower-case target-env) ".properties"))
                                (io/file "lupapiste.properties")
                                (io/file "user.properties")
                                (io/file (System/getProperty "lupapiste.properties"))
                                (io/file (System/getenv "LUPAPISTE_PROPERTIES"))])))

(defstate config
  :start (atom (read-all-configs)))

(defn reload! []
  (mount/stop #'config)
  (mount/start #'config))

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
    (get-in (cons :feature (vec keys)))
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

(defmacro with-feature-value
  "Binds given feature to value, executes body and finally returns feature to it's old value."
  [feature value & body]
  `(let [old-val# (feature? ~feature)]
     (set-feature! ~value [~feature])
     ~@body
     (set-feature! old-val# [~feature])))

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
    (get-in (get-config) (map keyword (ss/split prop-name #"\.")))
    (System/getProperty prop-name)
    (System/getenv (-> prop-name (ss/replace \. \_) (ss/upper-case)))
    default))

(def mode (keyword (get-prop "lupapiste.mode" "dev")))
(def port (read-value (get-prop "lupapiste.port" "8000")))
(def log-level (keyword (get-prop "lupapiste.loglevel" "debug")))
(def log-dir (get-prop "lupapiste.logdir" (if (= mode :dev) "target" ".")))
(defonce proxy-off (atom (read-value (str (get-prop "lupapiste.proxy-off" "false")))))

(defn in-env? [env]
  (= (ss/upper-case target-env) (ss/upper-case env)))

(defn dev-mode? []
  (= :dev mode))

(defn test-build? []
  (= target-env "TEST"))

(def dev-env? (in-env? "DEV"))

(def ^:dynamic *in-dev-macro* false)

(defmacro in-dev [& body]
  `(if (dev-mode?)
     (binding [*in-dev-macro* true]
       (do ~@body))))

(defn server-address [] (System/getProperty "target_server" (or (value :host) "http://localhost:8000")))
