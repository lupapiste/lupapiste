(ns lupapalvelu.rest.applications-data
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.bag :as bag]
            [lupapalvelu.building :as building]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-usage-description
                                                           drawings-as-krysp]]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical-operations]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rest.schemas :refer [HakemusTiedot DateString]]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.util :as util]
            [schema-tools.core :as st]
            [taoensso.timbre :refer [warnf]]))

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

(defn- datefixer  [schema]
  (when (= schema DateString)
    #(date/iso-date % :local)))

(defn process-application [application]
  (if (empty? (:documents application))
    (warnf "Skipping data item [ID=%s], documents array empty" (:id application))
    (-> {:asiointitunnus          (:id application)
         :kiinteistoTunnus        (:propertyId application)
         :osoite                  (:address application)
         :kuntakoodi              (:municipality application)
         :asiatyyppi              (when (= "muutoslupa" (:permitSubtype application))
                                    (:permitSubtype application))
         :sijaintiETRS            (:location application)
         :saapumisPvm             (date/iso-date (:submitted application) :local)
         :toimenpiteet            (when (= "R" (:permitType application))
                                    (get-toimenpiteet application))
         :yleisenAlueenKayttolupa (when (= "YA" (:permitType application))
                                    (get-ya application))}
        util/strip-nils
        (st/select-schema HakemusTiedot datefixer))))

(def required-fields-from-db
  [:id :organization :primaryOperation :propertyId :address :municipality :submitted
   :location :documents :permitType :drawings :secondaryOperations :permitSubtype])

(defn- query-applications [organization]
  (mongo/select :applications {:permitType   {$in [:R :YA]}
                               :organization organization
                               :state        :submitted
                               :primaryOperation.name
                               {$nin [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :ya-jatkoaika]}}
                required-fields-from-db))

(defn- process-applications [coll]
  (keep (fn [a]
          (try
            (process-application a)
            (catch Exception e
              (warnf "Skipping data item [ID=%s]: %s"
                     (:id a) (ex-message e)))))
        coll))

(defn- bag-organization [applications]
  (if-let [organization (some-> applications first org/get-application-organization)]
    (map #(bag/put % :organization organization) applications)
    applications))

(defn applications-by-organization [organization]
  (->> organization
       query-applications
       bag-organization
       process-applications))

(defn- operation-building-updates
  "Generates single mongo update clause from five parts:
  1. Push building updates to building-updates array
  2. Document updates for national-building-id (valtakunnallinenNumero)
  3. Using operation-id finds matching document and then finds matching apartments for updates and then creates updates for pht (pysyva huoneistotunnus).
  4. Using operation-id finds matching building and then finds matching apartments for updates and then created updates for pht (pysyva huoneistotunnus).
  5. Buildings-array updates for national-building-id (valtakunnallinenNumero)
  6. Buildings-array updates for building location (x+y).
  7. Review (task) building updates for national-building-id."
  [application operation-id national-building-id location-map apartments-data timestamp]
  (-> (building/push-building-updates application operation-id national-building-id location-map apartments-data timestamp)
      (util/assoc-when-pred not-empty
        $set (merge
              (building/document-buildingid-updates-for-operation application national-building-id operation-id)
              (building/apartment-pht-updates-for-document (:documents application) apartments-data timestamp operation-id)
              (building/apartment-pht-updates-for-building (:buildings application) apartments-data operation-id)
              (building/buildings-array-buildingid-updates-for-operation application national-building-id operation-id)
              (building/buildings-array-location-updates-for-operation application location-map operation-id)
              (building/review-buildings-national-id-updates application operation-id national-building-id timestamp)))))

(defn update-building!
  "Updates building data from REST API to application.
  Updates national-building-id to document and buildings-array.
  Updates building location (x+y) and pht (pysyvahuoneistounnus) for apartments on buildings array.
  Using operation-id finds matching document and then finds matching apartments for pht (pysyva huoneistotunnus) updates.
  Using operation-id finds matching building and then finds matching apartments for pht (pysyva huoneistotunnus) updates."
  [application operation-id national-building-id location-map apartments-update timestamp]
  (when-let [updates (operation-building-updates application operation-id national-building-id location-map apartments-update timestamp)]
    (action/update-application (action/application->command application) updates)
    true))
