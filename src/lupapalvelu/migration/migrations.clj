(ns lupapalvelu.migration.migrations
  (:require [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

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
  (let [value-to-add lupapalvelu.document.schemas/muutostapa
        rakennuslupa-schemas (lupapalvelu.document.schemas/get-schemas)
        updated (map (fn [document] (let [name (get-in document [:info :name] rakennuslupa-schemas)
                                          new-schema (name rakennuslupa-schemas)]
                                      (assoc document :schema new-schema)
                                      )) (:documents application))
        ]
    (assoc application :documents updated)))

(defmigration add-muutostapa-to-huoneistot
  :apply-when (pos? (mongo/count :applications muutostapa-not-exits-query))
  (let [applications-to-update mongo/select :applications muutostapa-not-exits-query]
    for ([application applications-to-update]
          (add-muutostapa-to-application application))))




