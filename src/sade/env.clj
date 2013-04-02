(ns sade.env
  (:use [sade.util :only [deep-merge-with]]
        [sade.strings :only [numeric?]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
           [org.jasypt.properties EncryptableProperties]))

(def buildinfo (read-string (slurp (io/resource "buildinfo.clj"))))

; TODO rewrite? Perhaps determine from mode or env parameter?
(defn prop-file []
  (let [target-env (or (re-find #"[PRODEVTS]+" (or (buildinfo :build-tag) "")) "local")]
    (-> target-env (s/lower-case) (str ".properties"))))

(defn read-value [s]
  (cond
    (.equalsIgnoreCase "true" s) true
    (.equalsIgnoreCase "false" s) false
    (numeric? s) (Long/parseLong s)
    :default s))

(defn read-config [file-name password]
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
                 (assoc-in {} (clojure.string/split k #"\.") (read-value v))))))))

(def config
  (let [password (or (System/getProperty "lupapiste.masterpassword") (System/getenv "LUPAPISTE_MASTERPASSWORD") "lupapiste")]
    (read-config (prop-file) password)))

(defn value
  "returns a value from config directly."
  [& keys]
  (get-in config (flatten [keys])))

(defn- get-prop [prop-name default]
  (or
    (get-in config (map keyword (s/split prop-name #"\.")))
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
