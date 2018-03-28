(ns lupapalvelu.building-site
  (:require [clj-time.format :as tf]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.wfs :as wfs]))

;;
;; KTJ-info updation
;;

(def ktj-format (tf/formatter "yyyyMMdd"))
(def output-format (tf/formatter "dd.MM.yyyy"))

(defn fetch-and-persist-ktj-tiedot [application document property-id time]
  (when-let [ktj-tiedot (wfs/rekisteritiedot-xml property-id)]
    (let [doc-updates [[[:kiinteisto :tilanNimi] (or (:nimi ktj-tiedot) "")]
                       [[:kiinteisto :maapintaala] (or (:maapintaala ktj-tiedot) "")]
                       [[:kiinteisto :vesipintaala] (or (:vesipintaala ktj-tiedot) "")]
                       [[:kiinteisto :rekisterointipvm] (or
                                                          (try
                                                            (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                            (catch Exception e (:rekisterointipvm ktj-tiedot)))
                                                          "")]]
          schema (schemas/get-schema (:schema-info document))
          updates (filter (partial doc-persistence/update-key-in-schema? (:body schema)) doc-updates)]
      (doc-persistence/persist-model-updates application "documents" document updates time))))