(ns sade.dns
  (:require [sade.strings :as ss]
            [sade.core :refer :all])
  (:import javax.naming.directory.InitialDirContext))

(def- dns-lookup-env (doto (java.util.Hashtable.) (.put InitialDirContext/INITIAL_CONTEXT_FACTORY "com.sun.jndi.dns.DnsContextFactory")))

(def- mx-query (into-array String ["MX"]))

(defn valid-mx-domain?
  "Validates MX record for given email address or domain name"
  [^String email]
  (boolean
    (when-not (ss/blank? email)
      (try
        (some->
          (InitialDirContext. dns-lookup-env)
          (.getAttributes (ss/suffix email "@") mx-query)
          (.get "MX")
          .size
          pos?)
        (catch javax.naming.NamingException _ false)))))
