(ns lupapalvelu.verdict-robot.schemas
  "Schema definitions for the outgoing Pate verdict JSON."
  (:require [lupapalvelu.location :refer [LocationOperation XY]]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.statement-schemas :refer [statement-statuses]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema optional-key]]))

(defschema Pvm
  "`YYYY-MM-dd`. The dates are in the Finnish timezone."
  (ssc/date-string "YYYY-MM-dd"))

(defschema Katselmus
  {:nimi   sc/Str
   :laji   (apply sc/enum (vals helper/review-type-map))
   :tunnus sc/Str})

(defschema Naapuri
  {:kiinteistotunnus ssc/Kiinteistotunnus
   :pvm              Pvm
   :kuultu           sc/Bool})

(defschema HankkeenVaativuus
  {(optional-key :vaativuus) (sc/enum "poikkeuksellisen vaativa" "vaativa" "tavanomainen" "vähäinen")
   (optional-key :selite)    sc/Str})

(defschema Lausunto
  {:lausunnonantaja sc/Str ; Palomestari Sonja Sibbo
   :pvm             Pvm
   :lausuntotieto   (apply sc/enum statement-statuses) })

(defschema Vakuus
  {(optional-key :pvm)   Pvm
   (optional-key :summa) sc/Str
   (optional-key :laji)  sc/Str})

(defschema Rakennus
  {(optional-key :tunnus)                ssc/Rakennustunnus
   (optional-key :tunniste)              sc/Str ; A
   (optional-key :selite)                sc/Str ; My beautiful house
   (optional-key :paloluokka)            sc/Str
   (optional-key :vssLuokka)             sc/Str
   (optional-key :rakennetutAutopaikat)  sc/Str
   (optional-key :kiinteistonAutopaikat) sc/Str
   (optional-key :autopaikatYhteensa)    sc/Str})

(defschema Paivamaarat
  "All the other keys are optional except `paatosPvm`"
  (->> (helper/verdict-dates)
       (map (util/fn-> name (str "Pvm") keyword))
       (map #(vector (optional-key %) Pvm))
       (into {:paatosPvm Pvm})))

(defschema Toimija
  {(optional-key :nimi)   ssc/NonBlankStr
   (optional-key :nimike) ssc/NonBlankStr})

(defschema Paatos
  {:tunnus                                     ssc/NonBlankStr
   :paatostieto                                (apply sc/enum (vals helper/verdict-code-map))
   :paatostyyppi                               (sc/enum "lautakunta" "viranhaltija")
   :kieli                                      (apply sc/enum (map name helper/supported-languages))
   :paivamaarat                                Paivamaarat
   (optional-key :paatoksentekija)             Toimija
   (optional-key :kasittelija)                 Toimija
   (optional-key :paatosteksti)                sc/Str
   (optional-key :korvaaPaatoksen)             ssc/NonBlankStr
   (optional-key :pykala)                      sc/Str
   (optional-key :perustelut)                  sc/Str
   (optional-key :sovelletutOikeusohjeet)      sc/Str
   (optional-key :toimenpidetekstiJulkipanoon) sc/Str
   (optional-key :vaaditutTyonjohtajat)        [(apply sc/enum (vals helper/foreman-roles))]
   (optional-key :vaaditutKatselmukset)        [Katselmus]
   (optional-key :vaaditutErityissuunnitelmat) [sc/Str]
   (optional-key :muutLupaehdot)               [sc/Str]
   (optional-key :naapurienKuuleminen)         sc/Str
   (optional-key :hankkeenVaativuus)           HankkeenVaativuus
   (optional-key :lisaselvitykset)             sc/Str
   (optional-key :poikkeamiset)                sc/Str
   (optional-key :rakennusoikeus)              sc/Str
   (optional-key :kaavanKayttotarkoitus)       sc/Str
   (optional-key :aloittamisoikeusVakuudella)  Vakuus
   (optional-key :rakennushanke)               sc/Str
   (optional-key :osoite)                      sc/Str
   (optional-key :rakennukset)                 [Rakennus]})

(defschema Base
  {:versio           (sc/eq 2)
   :asiointitunnus   ssc/ApplicationId
   :kiinteistotunnus ssc/Kiinteistotunnus
   :osoite           sc/Str})

(defschema PaatosSanoma
  (merge Base
         {:paatos                    Paatos
          (optional-key :naapurit)   [Naapuri]
          (optional-key :lausunnot)  [Lausunto]
          (optional-key :menettely)  sc/Str
          (optional-key :avainsanat) [sc/Str]}))

(defschema PoistoSanoma
  (merge Base
         {:poistettuPaatos ssc/NonBlankStr}))

(defschema Sanoma
  (let [;; Integration message id value.
        msg-id-schema {:sanomatunnus ssc/NonBlankStr}]
    (sc/conditional :paatos (merge PaatosSanoma msg-id-schema)
                    :poistettuPaatos (merge PoistoSanoma msg-id-schema))))

;; --------------------------
;; Operation locations
;; --------------------------

(defschema ApplicationOperationLocations
  {:application-id ssc/ApplicationId
   :operations     [LocationOperation]})

(defschema LocationAck
  "Each key is an operation id as keyword."
  {sc/Keyword {:location     XY
               :message-id   ssc/ObjectIdStr
               :acknowledged ssc/Timestamp}})

(defschema OperationLocationsMessage
  {:message-id ssc/NonBlankStr ;; The same as the integration message id
   :data       [ApplicationOperationLocations]})
