(ns sade.dns
  (:require [sade.strings :as ss]
            [sade.core :refer :all]
            [taoensso.timbre :refer [errorf]]
            [sade.env :as env]
            [sade.validators :as v])
  (:import [java.util Hashtable]
           [javax.naming.directory InitialDirContext]
           [javax.naming NamingException]))

(def- dns-lookup-env
  (doto (Hashtable.)
    (.put InitialDirContext/INITIAL_CONTEXT_FACTORY "com.sun.jndi.dns.DnsContextFactory")))

(def- ^"[Ljava.lang.String;" mx-query (into-array String ["MX"]))

(defn- has-mx? [email]
  (some-> (InitialDirContext. dns-lookup-env)
          (.getAttributes ^String (ss/suffix email "@") mx-query)
          (.get "MX")
          .size
          pos?))

(defn valid-mx-domain?
  "Validates MX record for given email address or domain name"
  [^String email]
  (boolean
    (when-not (ss/blank? email)
      (try
        (or (has-mx? email)
            (errorf "Bad email %s: No MX record." email))
        (catch NamingException e
          (errorf "Bad email %s: %s - %s" email (.getClass e) (.getMessage e)))))))

(defn email-and-domain-valid? [email]
  (or (ss/blank? email)
      (and (v/valid-email? email)
           (or (env/value :email :skip-mx-validation) (valid-mx-domain? email)))))

