(ns lupapalvelu.migration.migrations
  (:require [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(defmigration add-default-permit-type
  {:apply-when (pos? (mongo/count :applications {:permitType {$exists false}}))}
  (mongo/update :applications {:permitType {$exists false}} {$set {:permitType "R"}} :multi true))


(def ^:private enabled-organizations #{"186-R"       ;; Jarvenpaa
                                       "529-R"       ;; Naantali
                                       "753-R"       ;; Sipoo
                                       "491-R"})     ;; Mikkeli

(defn- get-value-to-update [db-key id municipalities]
  (condp = db-key
    :scope (map (fn [municipality] {:municipality municipality :permitType "R"}) municipalities)
    :inforequest-enabled (if-not (nil? (enabled-organizations id)) true false)
    :new-application-enabled (if-not (nil? (enabled-organizations id)) true false)))

(defn- add-key-to-database [db-key]
  (let [without-key (mongo/select :organizations {db-key {$exists false}})]
    (doseq [{:keys [id municipalities]} without-key]
      (let [value-to-update (get-value-to-update db-key id municipalities)]
        (mongo/update-by-id :organizations id {$set {db-key value-to-update}})))
    {:fixed-organizations   (count without-key)
     :organizations-total   (mongo/count :organizations)}))

(defmigration add-scope-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:scope {$exists false}}))}
  (add-key-to-database :scope))

(defmigration add-inforequest-enabled-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:inforequest-enabled {$exists false}}))}
  (add-key-to-database :inforequest-enabled))

(defmigration add-new-application-enabled-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:new-application-enabled {$exists false}}))}
  (add-key-to-database :new-application-enabled))


(def muutostapa-not-exits-query
  {:documents {$elemMatch {"schema.info.name"
                           {$in  ["uusiRakennus" "rakennuksen-muuttaminen" "rakennuksen-laajentaminen" "purku"]}
                           "schema.body" {$not {$elemMatch {"name" "huoneistot"
                                                            "body.name" "muutostapa"}}}}}})

(defn update-rakennuslupa-documents-schemas [application]
  (let [updated (map (fn [document] (let [name (get-in document [:schema :info :name])
                                          new-schema (schemas/get-schema name)]
                                      (assoc document :schema new-schema)))
                     (:documents application))
        updated-application (assoc application :documents updated)]
    (mongo/update :applications {:_id (:id updated-application)} updated-application)))

(defmigration add-muutostapa-to-huoneistot
  {:apply-when (pos? (mongo/count :applications muutostapa-not-exits-query))}
  (doseq [application (mongo/select :applications muutostapa-not-exits-query)]
    (update-rakennuslupa-documents-schemas application)))

(defn- update-document-turvakieltoKytkin [{data :data :as document}]
  (let [to-update (tools/deep-find data [:henkilo :turvakieltoKytkin])
        updated-document (when (not-empty to-update)
                           (assoc document :data (reduce
                                                   (fn [d [old-key v]]
                                                     (let [new-key (conj (subvec old-key 0 (.size old-key)) :henkilo :henkilotiedot :turvakieltoKytkin)
                                                           cleaned-up (sade.util/dissoc-in d (conj old-key :henkilo :turvakieltoKytkin))]
                                                       (assoc-in cleaned-up new-key v)))
                                                   data to-update)))]
    (if updated-document
      updated-document
      document)))

(defn- update-application-for-turvakielto [application]
  (let [documents (:documents application)
        updated-application (assoc application :documents (map update-document-turvakieltoKytkin documents))]
    ; This updates application on mongo too
  (update-rakennuslupa-documents-schemas updated-application)))

(defmigration move-turvakielto-to-correct-place
   (let [applications (mongo/select :applications)]
     (map update-application-for-turvakielto applications)))
