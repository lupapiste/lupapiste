(ns lupapalvelu.building-site
  (:require [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mml.property.api :as property-api]
            [sade.date :as date]
            [sade.env :as env]))

;;
;; KTJ-info updating
;;

(defn fetch-ktj-tiedot [property-id]
  (if (env/feature? :disable-ktj-on-create)
    (log/info "ktj-client is disabled - not getting property details for" property-id)
    (try (property-api/property-details-by-id! property-id)
         (catch Exception err
           (log/warnf "No KTJ information for property id %s: %s"
                      property-id err)))))

(defmulti post-process-ktj-field
  "Perform additional post processing on raw KTJ fields"
  {:private true}
  (fn [[k _]] k))

(defmethod post-process-ktj-field :default
  [[k v]]
  [k v])

(defmethod post-process-ktj-field :rekisterointipvm
  [[k rekisterointipvm]]
  (if-let [pvm (try
                 (some-> rekisterointipvm
                         (date/zoned-parse :basic-date)
                         (date/zoned-format :fi-date :zero-pad))
                 (catch Exception _
                   nil))]
    [k pvm]
    [k rekisterointipvm]))

(defn post-process-ktj-tiedot
  "Perform some cleanup on the raw KTJ fields"
  [ktj-tiedot]
  (->> ktj-tiedot
       (keep post-process-ktj-field)
       (into {})))

(defn fetch-and-persist-ktj-tiedot [application document property-id time]
  (let [ktj-tiedot  (-> property-id fetch-ktj-tiedot post-process-ktj-tiedot)
        doc-updates [[[:kiinteisto :tilanNimi] (or (:nimi ktj-tiedot) "")]
                     [[:kiinteisto :maapintaala] (or (:maapintaala ktj-tiedot) "")]
                     [[:kiinteisto :vesipintaala] (or (:vesipintaala ktj-tiedot) "")]
                     [[:kiinteisto :rekisterointipvm] (or (:rekisterointipvm ktj-tiedot) "")]]
        schema      (schemas/get-schema (:schema-info document))
        updates     (filter (partial doc-persistence/update-key-in-schema? (:body schema)) doc-updates)]
    (doc-persistence/persist-model-updates application "documents" document updates time)))
