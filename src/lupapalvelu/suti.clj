(ns lupapalvelu.suti
  (:require [monger.operators :refer :all]
            [cheshire.core :as json]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [debugf]]
            [sade.http :as http]
            [sade.strings :as ss]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr])
  (:import org.joda.time.DateTimeZone))

(defn admin-org [admin]
  (-> admin
      usr/authority-admins-organization-id
      org/get-organization))

(defn toggle-enable [organization-id flag]
  (org/update-organization organization-id
                           {$set {:suti.enabled flag}}))

(defn toggle-operation [organization operation-id flag]
  (let [already (contains? (-> organization :suti :operations set) operation-id)]
    (when (not= (boolean already) (boolean flag))
      (org/update-organization (:id organization)
                               {(if flag $push $pull) {:suti.operations operation-id}}))))

(defn toggle-section-operation [organization operation-id flag]
  (let [already (contains? (-> organization :section-operations set) operation-id)]
    (when (not= (boolean already) (boolean flag))
      (org/update-organization (:id organization)
                               {(if flag $push $pull) {:section-operations operation-id}}))))

(defn organization-details [{{server :server :as suti} :suti}]
  (-> suti
      (select-keys [:enabled :www])
      (assoc :server (select-keys server [:url :username]))))

(defn set-www [organization www]
  (org/update-organization (:id organization) {$set {:suti.www (ss/trim www)}}))

(defn- append-to-url [url part]
  (if (ss/ends-with url "/")
    (str url part)
    (str url "/" part)))

(defn- clean-timestamp
  "Suti returns expirydate and downloaded fields as a strings
  '\\/Date(1495806556450)\\/'. Included Unix timestamp is in the local
  time, so it is also converted to UTC. Returns Unix timestamp (ms
  from epoc) or nil."
  [field]
  (try
    (when-let [ts (some->> field
                           (re-find #"\d+")
                           Long/parseLong)]
      (.convertLocalToUTC (DateTimeZone/getDefault) ts true))
    (catch Exception _)))

(defn- clean-suti-dates
  [{:keys [expirydate downloaded] :as data}]
  (assoc data
         :expirydate (clean-timestamp expirydate)
         :downloaded (clean-timestamp downloaded)))

(defn- fetch-suti-products
  "Returns either list of products (can be empty) or error localization key."
  [url {:keys [username password crypto-iv]}]
  (try
    (some-<> url
             (http/get (merge {}
                              (when (ss/not-blank? crypto-iv)
                                {:basic-auth [username (org/decode-credentials password
                                                                               crypto-iv)]})))
             :body
             (json/parse-string true)
             :productlist
             (map clean-suti-dates <>))
    (catch Exception e
      (debugf "Suti fetch failed (%s): %s" url (.getMessage e))
      "suti.products-error")))

(defn application-data [{:keys [suti organization primaryOperation]}]
  (let [{:keys [enabled www
                server operations]} (:suti (org/get-organization organization))
        url                         (:url server)
        suti-id                     (:id suti)
        suti-enabled                (and enabled
                                         (contains? (set operations)
                                                    (:name primaryOperation)))
        products                    (when (and (ss/not-blank? suti-id) suti-enabled (ss/not-blank? url))
                                      (fetch-suti-products (append-to-url url suti-id) server))]
    {:enabled enabled
     :www (when (every? ss/not-blank? [www suti-id])
            (ss/replace www "$$" suti-id))
     :products products
     :suti suti}))
