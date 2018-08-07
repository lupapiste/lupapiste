(ns lupapalvelu.idf.idf-core
  (:require [pandect.core :as pandect]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.user :as user]))

(def send-or-receive-set #{:send :receive} )

(def- config (reduce (fn [m [k v]] (assoc m (:name v) (assoc v :id (name k)))) {} (env/value :idf)))

(defn known-partner? [partner-name]
  (contains? config partner-name))

(defn id-for-partner [partner-name]
  {:pre [(known-partner? partner-name)], :post [%]}
  (:id (config partner-name)))

(defn url-for-partner [partner-name]
  {:pre [(known-partner? partner-name)], :post [%]}
  (get-in (config partner-name) [:send :url]))

(defn send-app-for-partner [partner-name]
  {:pre [(known-partner? partner-name)], :post [%]}
  (get-in (config partner-name) [:send :app]))

(defn- key-for-partner [partner-name] (:key (config partner-name)))
(defn- send-key-for-partner [partner-name] (get-in (config partner-name) [:send :key]))

(defn calculate-mac
  ([{:keys [etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen id]} partner-name ts send-or-receive]
    {:pre [(known-partner? partner-name)]}
    (calculate-mac etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen partner-name id ts send-or-receive))
  ([etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen partner-name id ts send-or-receive]
    {:pre [(known-partner? partner-name)
           (send-or-receive-set send-or-receive)]}
    (let [[app key] (if (= :receive send-or-receive)
                      [partner-name (key-for-partner partner-name)]
                      [(send-app-for-partner partner-name) (send-key-for-partner partner-name)])
          text (str etunimi sukunimi email puhelin katuosoite postinumero postitoimipaikka suoramarkkinointilupa ammattilainen app id ts key)]
      (assert key (str "key, parter/app=" partner-name " " app))
      (pandect/sha256 (java.io.ByteArrayInputStream. (ss/utf8-bytes text))))))

(defn link-account! [email app id timestamp origin?]
  (let [path (str "partnerApplications." (id-for-partner app))]
    (user/update-user-by-email email {(str path ".id") id
                                      (str path ".created") timestamp
                                      (str path ".origin") origin?})))
