(ns lupapalvelu.backing-system.allu-conversion-test
  (:require [schema.core :as sc :refer [defschema Bool]]
            [sade.env :as env]
            [sade.municipality :refer [municipality-codes]]
            [sade.schemas :refer [NonBlankStr Kiinteistotunnus ApplicationId]]
            [sade.schema-generators :as sg]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.states :as states]

            [midje.sweet :refer [facts fact =>]]
            [clojure.test.check :refer [quick-check]]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.backing-system.allu.schemas :refer [SijoituslupaOperation PlacementContract]]
            [lupapalvelu.backing-system.allu.conversion :refer [application->allu-placement-contract]]))

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defschema ValidPlacementApplication
  {:id               ApplicationId
   :state            (apply sc/enum (map name states/all-states))
   :permitSubtype    (sc/enum "sijoituslupa" "sijoitussopimus")
   :organization     NonBlankStr
   :propertyId       Kiinteistotunnus
   :municipality     (apply sc/enum municipality-codes)
   :address          NonBlankStr
   :primaryOperation {:name SijoituslupaOperation}
   :documents        [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true) "description")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")]
   :location-wgs84   [(sc/one sc/Num "longitude") (sc/one sc/Num "latitude")]
   :drawings         [{:geometry-wgs84 geo/SingleGeometry}]})

;;;; Actual Tests
;;;; ==================================================================================================================

(env/with-feature-value :allu true
  (sc/with-fn-validation
    (facts "application->allu-placement-contract"
      (fact "Valid applications produce valid inputs for ALLU."
        (quick-check 10
                     (for-all [application (sg/generator ValidPlacementApplication)]
                       (nil? (sc/check PlacementContract
                                       (application->allu-placement-contract (sg/generate Bool) application)))))
        => passing-quick-check))))
