(ns lupapalvelu.opendata.applications-data
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical-operations]]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [warnf]]
            [lupapalvelu.opendata.schemas :refer [HakemusTiedot]]))

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

(defn process-buildings [application]
  (-> application
      (assoc :toimenpiteet (get-toimenpiteet application))))

(defn process-application [application]
  (-> application
      (merge {:asiointitunnus    (:id application)
              :kiinteistoTunnus  (:propertyId application)
              :osoite            (:address application)
              :kuntakoodi        (:municipality application)
              :sijaintiETRS      (:location application)})
      (assoc :saapumisPvm        (util/to-xml-date (:submitted application)))
      process-buildings
      (dissoc :id :propertyId :address :municipality :location :primaryOperation :submitted :documents)))

(defn schema-verify [checker data]
  (let [errors (checker data)]
    (if errors
      (do
        (warnf "Skipping data item [ID=%s], the result does not conform to HakemusTiedot schema: %s" (:asiointitunnus data) errors))
      data)))

(defn applications-by-organization [organization]
  (->> (mongo/select :applications {:permitType   :R
                                    :organization organization
                                    :state        :submitted
                                    :primaryOperation.name {$not {$in [:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2]}}}
                [:id :primaryOperation.name :propertyId :address :municipality
                 :submitted :location :documents])
       (map process-application)
       (map (partial schema-verify (sc/checker HakemusTiedot)))
       (remove nil?)))