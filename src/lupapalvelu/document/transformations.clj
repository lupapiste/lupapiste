(ns lupapalvelu.document.transformations
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as persistence]))

;;
;; Application state change updates
;;

(defn rename-keys-deep [mappings m]
  (->> (map (fn [[k v]] [(get mappings k k) (if (map? v) (rename-keys-deep mappings v) v)]) m)
       (into {})))

(defn rakennusjateselvitys-updates [{application :application created :created :as command} selvitys-doc]
  (when-let [suunnitelma-doc (domain/get-document-by-name application "rakennusjatesuunnitelma")]
    (let [field-mappings {:suunniteltuMaara :toteutunutMaara}
          updates (->> (:data suunnitelma-doc)
                       (rename-keys-deep field-mappings)
                       (persistence/data-model->model-updates [])
                       (filter (comp not-empty second)))]
      (persistence/validated-model-updates application :documents selvitys-doc updates created))))

(defn create-rakennusjateselvitys-from-rakennusjatesuunnitelma [{application :application :as command}]
  (->>  (or (domain/get-document-by-name application "rakennusjateselvitys")
            (persistence/create-empty-doc command "rakennusjateselvitys"))
        (rakennusjateselvitys-updates command)))

(defn get-state-transition-updates [command next-state]
  (case next-state
    :verdictGiven (create-rakennusjateselvitys-from-rakennusjatesuunnitelma command)
    {}))
