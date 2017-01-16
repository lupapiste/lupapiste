(ns lupapalvelu.rest.applications-data
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-usage-description
                                                           drawings-as-krysp]]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical-operations]]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [warnf]]
            [lupapalvelu.rest.schemas :refer [HakemusTiedot]]))

(defn- transform-operation [operation]
  (-> operation
      (select-keys [:uusi :muuMuutosTyo :laajennus :purkaminen :kaupunkikuvaToimenpide])
      (assoc :rakennuksenTiedot (dissoc (get-in operation [:rakennustieto :Rakennus :rakennuksenTiedot])))
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
  [:id :primaryOperation.name :propertyId :address :municipality
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