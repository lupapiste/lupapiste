(ns lupapalvelu.document.transformations
  (:require [sade.util :as util]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as persistence]))

;;
;; Application state change updates
;;

(defn rakennusjateselvitys-updates [{application :application created :created :as command} selvitys-doc]
  (when-let [suunnitelma-doc (domain/get-document-by-name application "rakennusjatesuunnitelma")]
    (let [field-mappings {:suunniteltuMaara :toteutunutMaara}
          updates (->> (:data suunnitelma-doc)
                       (util/postwalk-map #(clojure.set/rename-keys % field-mappings))
                       (persistence/data-model->model-updates [])
                       (filter (comp not-empty second)))]
      (persistence/validated-model-updates application :documents selvitys-doc updates created))))

(defn create-rakennusjateselvitys-from-rakennusjatesuunnitelma [{application :application :as command}]
  (when-not (domain/get-document-by-name application "rakennusjateselvitys")
    (rakennusjateselvitys-updates command (persistence/create-empty-doc command "rakennusjateselvitys"))))

(defn get-state-transition-updates [command next-state]
  (case next-state
    :verdictGiven (create-rakennusjateselvitys-from-rakennusjatesuunnitelma command)
    {}))
