(ns lupapalvelu.suti
  (:require [monger.operators :refer :all]
            [cheshire.core :as json]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [debugf]]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]
            [lupapalvelu.states :as states]))

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
  '\\/Date(1495806556450)\\/' (when filled). Included timestamp is in
  UTC. Returns the timestamp as long (ms from epoch) or nil."
  [field]
  (try
    (some->> field
             (re-find #"\d+")
             Long/parseLong)
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
    (catch Exception _
      "suti.products-error")))

(defn- suti-enabled?
  "Suti is enabled/required for an application when
    1. Organisation has Suti enabled.
    2. Application's primary operation has Suti requirement
    3. The application is not a legacy application: it has not
       already reached the sent state without Suti information."
  [{:keys [suti primaryOperation state]} organization]
  (let [{:keys [enabled operations]} (:suti organization)
        {suti-id :id added :added}   suti]
    (and enabled
         (contains? (set operations)
                    (:name primaryOperation))
         (not (and (ss/blank? suti-id)
                   (not added)
                   (contains? (conj states/post-verdict-states :sent)
                              (keyword state)))))))

(defn application-data [{:keys [suti primaryOperation state] :as application}
                        organization]
  (let [{:keys [enabled www
                operations]}       (:suti organization)
        {suti-id :id added :added} suti
        suti-enabled               (suti-enabled? application organization)]
    {:enabled suti-enabled
     :www (when (every? ss/not-blank? [www suti-id])
            (ss/replace www "$$" suti-id))
     :suti suti}))

(defn application-products
  "In addition to products the suti-id is returned as well just in case."
  [{:keys [suti primaryOperation state] :as application}
                            organization]
  (let [{:keys [enabled www
                server operations]} (:suti organization)
        url                         (:url server)
        {suti-id :id added :added}  suti
        suti-enabled                (suti-enabled? application organization)]
    {:id suti-id
     :products (when (and (ss/not-blank? suti-id)
                          (not added)
                          suti-enabled
                          (ss/not-blank? url))
                 (fetch-suti-products (append-to-url url suti-id) server))}))

(defn suti-submit-validation
  "Can be used as pre-check. Validates if application can be submitted from suti point of view.
  Validates only if suti is enabled for organization and for primary operation.
  Returs nil when suti 'added' flag is true or suti-id is set. Else suti-id needs to be set."
  [{:keys [application organization]}]
  (let [{:keys [enabled suti]} (application-data application @organization)]
    (when enabled
      (when-not (or (:added suti) (ss/not-blank? (:id suti)))
        (fail :suti.id-missing)))))
