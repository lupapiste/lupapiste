(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.kiinteistotoimitus-canonical :refer [operation-name]]
            [sade.core :refer [fail!]]))

(defmulti fetch-fetch-fn :permitType)

(defmethod fetch-fetch-fn :default
  [{:keys [permitType]}]
  (permit/get-application-xml-getter permitType))

(defmethod fetch-fetch-fn "KT"
  [application]
  (let [op-name (-> application :primaryOperation :name)
        krysp-name (case op-name
                     "rajankaynti"     "KiinteistonMaaritys"
                     "rasitetoimitus"  "Rasitetoimitus"
                     ;; KRYSP element is deducted from kiinteistonmuodostusTyyppi.
                     ;; For example, kiinteistolajin-muutos -> KiinteistolajinMuutos
                     "kiinteistonmuodostus" (let [doc (some #(and (= (-> % :schema-info :op :id)
                                                                     (-> application :primaryOperation :id))
                                                                  %)
                                                            (:documents application))]
                                              (-> doc :data :kiinteistonmuodostus
                                                  :kiinteistonmuodostusTyyppi :value
                                                  operation-name name)))]
    (partial (permit/get-application-xml-getter :permit/KT) krysp-name)))

(defn get-application-xml [{:keys [id permitType] :as application} search-type & [raw?]]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs application)]
    (if-let [fetch-fn (fetch-fetch-fn application)]
      (fetch-fn url credentials id search-type raw?)
      (do
        (error "No fetch function for" permitType (:organization application))
        (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))
