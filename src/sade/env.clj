(ns sade.env
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.io FileNotFoundException InputStream]
           [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
           [org.jasypt.properties EncryptableProperties]))

(set! *warn-on-reflection* true)

(defn- ^InputStream try-to-open [in]
  (when in
    (try (io/input-stream in)
         (catch FileNotFoundException _ nil))))

(def buildinfo (edn/read-string (slurp (io/resource "buildid.edn"))))

(defn- parse-target-env [buildinfo]
  (or (second (re-find #"-\s*([PRODEVTSQAprodevtsqa]{2,10})" (str buildinfo)))
     (when (re-find #"instant-smokes" (str buildinfo)) "prod")
     "local"))

(def target-env
  "Target environment (e.g., prod, qa, dev, local) where this instance is deployed"
  (or (-> "TARGET_ENV" System/getenv ss/trim ss/blank-as-nil)
      (parse-target-env (:build-tag buildinfo))))

(defn- ->build-number [{:keys [time build-number]}]
  (ss/trim-join "-" [(some-> time
                             (date/zoned-format (date/pattern-formatter "YYYYMMdd")))
                     (str build-number)]))

(def build-number (or (->build-number buildinfo) ""))

(def file-separator (System/getProperty "file.separator"))

(def ^:private master-password
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
    (.equalsIgnoreCase "true" s)  true
    (.equalsIgnoreCase "false" s) false
    (ss/numeric? s)               (Long/parseLong s)
    (ss/decimal-number? s)        (Double/parseDouble s)
    (ss/starts-with? s ":")       (keyword (subs s 1))
    :else                         s))

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

(defonce config (atom (read-all-configs)))

(defn get-config
  []
  {:post [(map? %)]}
  (let [v @config]
    ;; When starting up, first time dereffing returns a `NotStartedState` instance
    (if (map? v) v @config)))

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
  [value path]
  (swap! config assoc-in (concat [:feature] (map keyword path)) value)
  nil)

(defn enable-feature!
  "enables feature value in-memory."
  [& feature] (set-feature! true feature))

(defn disable-feature!
  "disables feature value in-memory."
  [& feature] (set-feature! false feature))

(defmacro with-feature-value
  "Binds given feature to value, executes body and finally rebinds feature to it's old value.
  Returns the value of body."
  [feature value & body]
  `(let [old-val# (feature? ~feature)]
     (set-feature! ~value [~feature])
     (let [result# (do ~@body)]
       (set-feature! old-val# [~feature])
       result#)))

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
    (get-in (get-config) (util/split-kw-path prop-name))
    (System/getProperty prop-name)
    (System/getenv (-> prop-name (ss/replace \. \_) (ss/upper-case)))
    default))

(defn- resolve-mode
  "Mode keyword for the current environment (e.g. `:dev`, `:test`, `:smoke`, `:qa`,
  `:prod.`). `:dev` (default) is only allowed if `dev-mode-supported` feature is enabled."
  []
  (let [mode (keyword (get-prop "lupapiste.mode" :dev))]
    (assert (or (not= mode :dev) (feature? :dev-mode-supported))
            "Dev mode not supported.")
    mode))

(def mode (resolve-mode))
(def port (read-value (get-prop "lupapiste.port" "8000")))
(def log-level (keyword (get-prop "lupapiste.loglevel" "debug")))
(def log-dir (get-prop "lupapiste.logdir" (if (= mode :dev) "target" ".")))
(defonce proxy-off (atom (read-value (str (get-prop "lupapiste.proxy-off" "false")))))

(defn in-env? [env]
  (= (ss/upper-case target-env) (ss/upper-case env)))

(defn dev-mode? []
  ;; We check the feature here as well, just in case.
  (and (= :dev mode) (feature? :dev-mode-supported)))

(defn test-build? []
  (= target-env "TEST"))

(def dev-env? (in-env? "DEV"))

(def ^:dynamic *in-dev-macro* false)

(defmacro in-dev [& body]
  `(if (dev-mode?)
     (binding [*in-dev-macro* true]
       (do ~@body))))

(defn server-address []
  (System/getProperty "target_server"
                      (or (value :host)
                          "http://localhost:8000")))
