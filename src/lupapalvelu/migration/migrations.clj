(ns lupapalvelu.migration.migrations
  (:require [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(defmigration add-default-permit-type
  {:apply-when (pos? (mongo/count :applications {:permitType {$exists false}}))}
  (mongo/update :applications {:permitType {$exists false}} {$set {:permitType "R"}} :multi true))

(def enabled-organizations ["186-R"       ;; Jarvenpaa
                            "529-R"       ;; Naantali
                            "753-R"       ;; Sipoo
                            "491-R"])     ;; Mikkeli

(defn- add-key-to-database [key]
  {:apply-when (pos? (mongo/count :organizations {key {$exists false}}))}
  (let [without-key (mongo/select :organizations {key {$exists false}})]
    (condp = key
      :scope (doseq [{:keys [id municipalities]} without-key]
               (let [scope (map (fn [municipality] {:municipality municipality :permitType "R"}) municipalities)]
                 (mongo/update-by-id :organizations id {$set {key scope}})))
      :inforequest-enabled (doseq [{:keys [id]} without-key]
                             (let [enabled-value (if (some #(= id %) enabled-organizations) true false)]
                               (mongo/update-by-id :organizations id {$set {:inforequest-enabled enabled-value}})))
      :new-application-enabled (doseq [{:keys [id]} without-key]
                                 (let [enabled-value (if (some #(= id %) enabled-organizations) true false)]
                                   (mongo/update-by-id :organizations id {$set {:new-application-enabled enabled-value}}))))
    {:fixed-organizations   (count without-key)
     :organizations-total   (mongo/count :organizations)}))

(defmigration add-scope-to-organizations
  (add-key-to-database :scope))

(def muutostapa-not-exits-query {:documents {$elemMatch {"schema.info.name"
                                                         {$in  ["uusiRakennus" "rakennuksen-muuttaminen" "rakennuksen-laajentaminen" "purku"]}
                                                         "schema.body" {$not {$elemMatch {"name" "huoneistot"
                                                                                          "body.name" "muutostapa"}}}}}})

(defn update-rakennuslupa-documents-schemas [application]
  (let [updated (map (fn [document] (let [name (get-in document [:schema :info :name])
                                          new-schema (schemas/get-schema (:schema-version application) name)]
                                      (assoc document :schema new-schema)))
                     (:documents application))
        updated-application (assoc application :documents updated)]
    (mongo/update :applications {:_id (:id updated-application)} updated-application)))

(defmigration add-muutostapa-to-huoneistot
  :apply-when (pos? (mongo/count :applications muutostapa-not-exits-query))
  (doseq [application (mongo/select :applications muutostapa-not-exits-query)]
    (update-rakennuslupa-documents-schemas application)))

(defmigration add-enableds-for-inforequest-and-new-application-to-organizations
  (add-key-to-database :inforequest-enabled)
  (add-key-to-database :new-application-enabled))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

(defmigration schemas-be-gonez
  {:apply-when (pos? (mongo/count :applications {:schema-version {$exists false}}))}
  (doseq [application (mongo/select :applications {:schema-version {$exists false}} {:documents true})]
    (mongo/update-by-id :applications (:id application) {$set {:schema-version 1
                                                               :documents (map drop-schema-data (:documents application))}})))
