(ns lupapalvelu.document.transformations
  (:require [sade.util :as util]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as persistence]))

;;
;; Application state change updates
;;

(defn rakennusjateselvitys-updates [suunnitelma-doc]
  (let [field-mappings {:suunniteltuMaara :toteutunutMaara}]
    (->> (:data suunnitelma-doc)
         (util/postwalk-map #(clojure.set/rename-keys % field-mappings))
         (persistence/data-model->model-updates [])
         (filter (comp not-empty second)))))

(defn create-rakennusjateselvitys-from-rakennusjatesuunnitelma [{application :application created :created :as command}]
  (when-let [suunnitelma-doc (domain/get-document-by-name application "rakennusjatesuunnitelma")]  
    (when-not (domain/get-document-by-name application "rakennusjateselvitys")
      (let [updates (rakennusjateselvitys-updates suunnitelma-doc)
            selvitys-doc (persistence/create-empty-doc command "rakennusjateselvitys")] 
        (persistence/validated-model-updates application :documents selvitys-doc updates created)))))

(defn get-state-transition-updates [command next-state]
  (case next-state
    :verdictGiven (create-rakennusjateselvitys-from-rakennusjatesuunnitelma command)
    {}))
