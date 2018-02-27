(ns lupapalvelu.rest.applications-data
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-usage-description
                                                           drawings-as-krysp]]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical-operations]]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [warnf]]
            [lupapalvelu.action :as action]
            [lupapalvelu.building :as building]
            [lupapalvelu.rest.schemas :refer [HakemusTiedot]]))

(defn- transform-operation [operation]
  (-> operation
      (select-keys [:uusi :muuMuutosTyo :laajennus :purkaminen :kaupunkikuvaToimenpide])
      (assoc :rakennuksenTiedot (get-in operation [:rakennustieto :Rakennus :rakennuksenTiedot]))
      (assoc :rakennelmanTiedot (get-in operation [:rakennelmatieto :Rakennelma]))
      (update :rakennelmanTiedot :kuvaus)
      (util/strip-nils)))

(defn- get-toimenpiteet [application]
  (->> (application-to-canonical-operations application)
       (map :Toimenpide)
       (map transform-operation)))

(defn- get-ya [application]
  (let [operation (-> application :primaryOperation :name keyword)]
    {:kayttotarkoitus (if (= :ya-jatkoaika operation)
                        "jatkoaika"
                        (ya-operation-type-to-usage-description operation))
     :sijainnit       (map :Sijainti (drawings-as-krysp (:drawings application)))}))

(defn process-application [application]
  (if (empty? (:documents application))
    (do
      (warnf "Skipping data item [ID=%s], documents array empty" (:id application))
      nil)
    (util/strip-nils
      {:asiointitunnus          (:id application)
       :kiinteistoTunnus        (:propertyId application)
       :osoite                  (:address application)
       :kuntakoodi              (:municipality application)
       :sijaintiETRS            (:location application)
       :saapumisPvm             (util/to-xml-date (:submitted application))
       :toimenpiteet            (when (= "R" (:permitType application))
                                  (get-toimenpiteet application))
       :yleisenAlueenKayttolupa (when (= "YA" (:permitType application))
                                  (get-ya application))})))

(defn schema-verify [checker data]
  (let [errors (checker data)]
    (if errors
      (do
        (warnf "Skipping data item [ID=%s], the result does not conform to HakemusTiedot schema: %s" (:asiointitunnus data) errors))
      data)))

(def required-fields-from-db
  [:id :primaryOperation :propertyId :address :municipality
   :submitted :location :documents :permitType :drawings])

(defn- query-applications [organization]
  (mongo/select :applications {:permitType   {$in [:R :YA]}
                               :organization organization
                               :state        :submitted
                               :primaryOperation.name
                                             {$not {$in [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :ya-jatkoaika]}}}
                required-fields-from-db))

(defn- process-applications [coll]
  (->> coll
       (map process-application)
       (remove nil?)
       (map (partial schema-verify (sc/checker HakemusTiedot)))
       (remove nil?)))

(defn applications-by-organization [organization]
  (->> organization
       query-applications
       process-applications))

(defn- operation-building-updates
  "Generates single mongo update clause from three parts:
  1. Document updates for national-building-id (valtakunnallinenNumero)
  2. Buildings-array updates for national-building-id (valtakunnallinenNumero)
  3. Buildings-array updates for building location (x+y)."
  [application operation-id national-building-id location-map timestamp]
  (-> (building/push-building-updates application operation-id national-building-id location-map timestamp)
      (util/assoc-when-pred not-empty
        $set (merge
              (building/document-buildingid-updates-for-operation application national-building-id operation-id)
              (building/buildings-array-buildingid-updates-for-operation application national-building-id operation-id)
              (building/buildings-array-location-updates-for-operation application location-map operation-id)))))

(defn update-building!
  "Updates building data from REST API to application.
  Updates national-building-id to document and buildings-array.
  Updates building location (x+y) to buildings array."
  [application operation-id national-building-id location-map timestamp]
  (when-let [updates (operation-building-updates application operation-id national-building-id location-map timestamp)]
    (action/update-application (action/application->command application) updates)
    true))
