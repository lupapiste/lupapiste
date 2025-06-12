(ns lupapalvelu.mml.property.api
  "Entrypoint for the MML `Kiinteistötietojen kyselypalvelu` API.

  Individual implementation(s) (OGC API) are hidden behind
  the [[lupapalvelu.mml.property.protocol/PropertyApi]] protocol
  and selected with the `mml.property-api.provider` environmental
  variable."
  (:require [lupapalvelu.logging :as log]
            [lupapalvelu.mml.common.client-impl :as client-impl]
            [lupapalvelu.mml.property.client :as client]
            [lupapalvelu.mml.property.protocol :as proto]
            [lupapalvelu.mml.property.util :as property-util]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(defn make-ogc-client
  "OGC API client"
  []
  (log/info "MML Property API provider: OGC")
  (client/->ApiClient client-impl/get!))

(def ^:dynamic *instance* (make-ogc-client))

(sc/defschema WktString (sc/named ssc/NonBlankStr 'wkt-string))

(sc/defschema PropertyInfo
  "Basic information for a single property"
  {:municipality ssc/Kuntanumero
   :name         {:fi ssc/NonBlankStr
                  :sv ssc/NonBlankStr}
   :propertyId   ssc/Kiinteistotunnus})

(sc/defn property-info-by-id! :- (sc/maybe PropertyInfo)
  "Find [[PropertyInfo]] for a single property by id."
  {:always-validate true}
  [property-id :- ssc/Kiinteistotunnus]
  (log/info "property-info-by-id" property-id)
  (proto/-property-info-by-id *instance* property-id))

(sc/defn property-infos-by-point! :- [PropertyInfo]
  "Find all matching [[PropertyInfo]]s for a coordinate."
  {:always-validate true}
  [x :- (sc/cond-pre ssc/LocationX ssc/LocationXString)
   y :- (sc/cond-pre ssc/LocationY ssc/LocationYString)]
  (log/info "property-infos-by-point" x y)
  (proto/-property-infos-by-point *instance* x y))

(def registration-date-format property-util/registration-date-format)

(sc/defschema PropertyDetails
  (st/merge
    {:kiinteistotunnus ssc/Kiinteistotunnus}
    (st/optional-keys
      {:nimi                 ssc/NonBlankStr
       :maapintaala          ssc/DecimalString
       :vesipintaala         ssc/DecimalString
       :olotila              ssc/NatString
       :rekisteriyksikkolaji ssc/NatString
       :rekisterointipvm     (ssc/date-string registration-date-format)})))

(sc/defn property-details-by-id! :- (sc/maybe PropertyDetails)
  "Find [[PropertyDetails]] for a single property by [[ssc/Kiinteistotunnus]]."
  {:always-validate true}
  [property-id :- ssc/Kiinteistotunnus]
  (log/info "property-details-by-id" property-id)
  (some-> (proto/-property-details-by-id *instance* property-id)
          util/strip-blanks
          not-empty))

(sc/defschema PropertyInfoWkt
  {:kiinttunnus          ssc/Kiinteistotunnus
   :kunta                ssc/Kuntanumero
   :nimi                 {:fi ssc/NonBlankStr
                          :sv ssc/NonBlankStr}
   :rekisteriyksikkolaji {:id     ssc/NatString
                          :selite {:fi ssc/NonBlankStr
                                   :sv ssc/NonBlankStr}}
   :wkt                  WktString
   :x                    ssc/LocationXString
   :y                    ssc/LocationYString})

(sc/defn property-infos-by-wkt! :- [PropertyInfoWkt]
  "Find [[PropertyInfoWkt]] by a [[WktString]] geometry and an optional `radius`.
   Note that the `radius` only applies when using a point geometry."
  {:always-validate true}
  ([wkt :- WktString]
   (property-infos-by-wkt! wkt nil))
  ([wkt :- WktString
    radius :- (sc/maybe ssc/NatString)]
   (log/info "property-infos-by-wkt" wkt radius)
   (proto/-property-infos-by-wkt *instance* wkt radius)))

(sc/defn lot-property-id-by-point! :- (sc/maybe ssc/Kiinteistotunnus)
  "Find the [[sade.schemas/Kiinteistotunnus]] of a lot by coordinate."
  {:always-validate true}
  [x :- (sc/cond-pre ssc/LocationX ssc/LocationXString)
   y :- (sc/cond-pre ssc/LocationY ssc/LocationYString)]
  (log/info "lot-property-id-by-point" x y)
  (proto/-lot-property-id-by-point *instance* x y))

(sc/defschema LotInfo
  {:kiinttunnus ssc/Kiinteistotunnus
   :lot-id      ssc/NatString
   :wkt         WktString
   :x           ssc/LocationXString
   :y           ssc/LocationYString})

(sc/defn lots-by-property-id! :- [LotInfo]
  {:always-validate true}
  [property-id :- ssc/NonBlankStr]
  ;; The `property-id` parameter ought to be a valid `ssc/Kiinteistotunnus`,
  ;; but it seems we have itests which use e.g. "00000"
  (log/info "lots-by-property-id" property-id)
  (proto/-lots-by-property-id *instance* property-id))
