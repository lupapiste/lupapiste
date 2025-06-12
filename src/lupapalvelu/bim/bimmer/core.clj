(ns lupapalvelu.bim.bimmer.core
  "The BIM extraction and checking integration using the `bimmer` module,
  with IFCOpenShell data extraction and Future Insight's Clearly API for checking."
  (:require [lupapalvelu.bim.bimmer.schema :as bimmer-schema]
            [lupapalvelu.bim.protocol :as bim-protocol]
            [sade.core :refer [fail!]]))

(def default-psets-query
  "Fallback psets query used when no derived query is present in the context.
  The automatic upload path always supplies a query via `btl/assoc-ifc-data-query`;
  this fallback applies only to the manual extraction path."
  {:IfcSite     [{:name "FI_Kiinteistö"}]
   :IfcBuilding [{:name "FI_RakennuksenSijainti"}
                 {:name "FI_RH1"}
                 {:name "FI_Kohde"}
                 {:name "FI_Suunnittelija"}
                 {:name "FI_Talotekniikka"}]
   :IfcSpace    [{:name    "FI_Laajuustieto"
                  :replace {"Tyyppi" {"Huoneistoala"       :area
                                      "Kerrosala"          :area
                                      "Kokonaisala"        :area
                                      "Kellarin pinta-ala" :area
                                      "Kokonaistilavuus"   :volume}}}
                 {:name "FI_Huoneisto"}]})

(defrecord Extraction []
  bim-protocol/BIMExtraction

  (-extraction-request [_ context]
    (-> context
        (select-keys [:bucket :object-key])
        (merge {:query    (-> (or (:query context) default-psets-query)
                              (update-keys name))
                :topic-id :bim/to-bim-service})))

  (-extraction-handler [_ _] ; Handler is in bimmer (see the docstring for `bim-protocol`)
    (fail! "Wrong pubsub topic for BIM integration"))

  (-extraction-response [_ message]
    (bimmer-schema/valid-extraction-response! message)
    (-> message :data :properties)))

(defrecord Checking []
  bim-protocol/BIMChecking

  (-checking-request [_ {:keys [check-type] :as context}]
    (-> context
        (select-keys [:bucket :object-key])
        (merge {:check-uuid (if (= check-type "compliance")
                              "aea64384-b812-4222-a8a6-20a35214ae82"  ;; rava3pro demo
                              "8a4b8037-2f47-444f-81c8-23d4a13ab09e") ;; rava3pro ids
                :topic-id   :bim/to-bim-service})))

  (-checking-handler [_ _ _] ; Handler is in bimmer (see the docstring for `bim-protocol`)
    (fail! "Wrong pubsub topic for BIM integration"))

  (-checking-response [_ message]
    (bimmer-schema/valid-checking-response! message)
    (-> message :data :check-results)))
