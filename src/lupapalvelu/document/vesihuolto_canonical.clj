(ns lupapalvelu.document.vesihuolto-canonical
  (require [lupapalvelu.document.vesihuolto-schemas :as vh-schemas]
           [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]
           [clojure.string :refer [lower-case]]))


(defn- get-talousvedet [talousvedet]
  {:talousvedet
   {:hankinta (:hakinta talousvedet)
    :johdatus (:johdatus talousvedet)
    :riittavyys (:riittavyys talousvedet)}})

(def varuste->krysp-varuste {:Lamminvesivaraaja "Lämminvesivaraaja"
                             :Kuivakaymala "Kuivakäymälä"
                             :WC "WC(vesikäymälä)"})

(defn get-varuste-as-krysp [varuste]
  (or (varuste varuste->krysp-varuste)
      (name varuste)))

(defn- get-vapautus-kohde [{property-id :propertyId} documents]
    (let [kiinteisto (:data (first (:vesihuolto-kiinteisto documents)))]
      (merge {:kiinteistorekisteritunnus property-id
             :kiinteistonRakennusTieto
             (for [[_ rakennus] (sort (:kiinteistoonKuuluu kiinteisto))]
               (let [kohteenVarustelutaso (remove nil? (map (fn [[k v]] (when v (get-varuste-as-krysp k))) (:kohteenVarustelutaso rakennus)))] {:KiinteistonRakennus
                               {:kayttotarkoitustieto {:kayttotarkoitus (lower-case (:rakennuksenTyypi rakennus))}
                                :kohteenVarustelutaso (not-empty kohteenVarustelutaso)
                                :haetaanVapautustaKytkin (true? (:vapautus rakennus))}}))}
             {:hulevedet (:hulevedet (:data (first (:hulevedet documents))))}
             (get-talousvedet (:data (first (:talousvedet documents))))
             {:jatevedet (:kuvaus (:data (first (:jatevedet documents))))})))

(defn vapautus-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)]
    {:Vesihuoltolaki
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :vapautukset
      {:Vapautus
       {:kasittelytietotieto (get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (lupatunnus (:id application))
        :lausuntotieto (get-statements (:statements application))
        :vapautusperuste ""
        :vapautushakemustieto
        {:Vapautushakemus
         {:hakija (get-henkilo (:henkilo (:data (first (:hakija documents)))))
          :kohde (get-vapautus-kohde application documents)
          :sijaintitieto (get-sijaintitieto application)}}
        :asianKuvaus (:kuvaus (:data (first (:hankkeen-kuvaus-vesihuolto documents))))
        }}}})
  )