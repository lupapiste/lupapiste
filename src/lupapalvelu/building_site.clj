(ns lupapalvelu.building-site
  (:require [clj-time.format :as tf]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.wfs :as wfs]
            [taoensso.timbre :refer [warnf]]))

;;
;; KTJ-info updation
;;

(def ktj-format (tf/formatter "yyyyMMdd"))
(def output-format (tf/formatter "dd.MM.yyyy"))

(defn fetch-and-persist-ktj-tiedot [application document property-id time]
  (let [ktj-tiedot  (try (wfs/rekisteritiedot-xml property-id)
                         (catch Exception err
                           (warnf "No KTJ information for property id %s: %s"
                                  property-id err)))
        doc-updates [[[:kiinteisto :tilanNimi] (or (:nimi ktj-tiedot) "")]
                     [[:kiinteisto :maapintaala] (or (:maapintaala ktj-tiedot) "")]
                     [[:kiinteisto :vesipintaala] (or (:vesipintaala ktj-tiedot) "")]
                     [[:kiinteisto :rekisterointipvm] (or
                                                       (try
                                                         (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                         (catch Exception _ (:rekisterointipvm ktj-tiedot)))
                                                       "")]]
        schema      (schemas/get-schema (:schema-info document))
        updates     (filter (partial doc-persistence/update-key-in-schema? (:body schema)) doc-updates)]
    (doc-persistence/persist-model-updates application "documents" document updates time)))
