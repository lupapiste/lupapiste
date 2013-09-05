(ns lupapalvelu.migration.migrations
  (:require [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.tools :as tools]))

(defmigration add-default-permit-type
  {:apply-when (pos? (mongo/count :applications {:permitType {$exists false}}))}
  (mongo/update :applications {:permitType {$exists false}} {$set {:permitType "R"}} :multi true))

(defmigration add-scope-to-organizations
  {:apply-when (pos? (mongo/count :organizations {:scope {$exists false}}))}
  (let [without-scope (mongo/select :organizations {:scope {$exists false}})]
    (doseq [{:keys [id municipalities]} without-scope]
      (let [scopes (map (fn [municipality] {:municipality municipality :permitType "R"}) municipalities)]
        (mongo/update-by-id :organizations id {$set {:scope scopes}})))
    {:fixed-organizations   (count without-scope)
     :organizations-total   (mongo/count :organizations)}))


(def muutostapa-not-exits-query {:documents {$elemMatch {"schema.info.name"
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
  :apply-when (pos? (mongo/count :applications muutostapa-not-exits-query))
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
