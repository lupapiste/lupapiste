(ns lupapalvelu.idf.idf-core
  (:require [digest]
            [sade.env :as env]
            [lupapalvelu.user :as user]))


(def utf8 (java.nio.charset.Charset/forName "UTF-8"))

(def ^:private config (reduce (fn [m [k v]] (assoc m (:name v) (assoc v :id (name k)))) {} (env/value :idf)))

(defn known-partner? [partner-name]
  (contains? config partner-name))

(defn id-for-partner [partner-name]
  {:pre [(known-partner? partner-name)], :post [%]}
  (:id (config partner-name)))

(defn url-for-partner [partner-name]
  {:pre [(known-partner? partner-name)], :post [%]}
  (:url (config partner-name)))

(defn- key-for-partner [partner-name] (:key (config partner-name)))

(defn calculate-mac
  ([{:keys [etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen id] :as query-params} app ts]
    {:pre [(known-partner? app)]}
    (calculate-mac etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen app id ts))
  ([etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen app id ts]
    {:pre [(known-partner? app)]}
    (let [text (str etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen app id ts (key-for-partner app))]
      (digest/sha-256 (java.io.ByteArrayInputStream. (.getBytes text utf8))))))

(defn link-account! [email app id timestamp origin?]
  (let [path (str "partnerApplications." (id-for-partner app))]
    (user/update-user-by-email email {(str path ".id") id
                                      (str path ".created") timestamp
                                      (str path ".origin") origin?})))
