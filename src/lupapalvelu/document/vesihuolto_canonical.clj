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

(def varuste->krysp-varuste {:Lamminvesivaraaja "L\u00e4mminvesivaraaja"
                             :Kuivakaymala "Kuivak\u00e4ym\u00e4l\u00e4"
                             :WC "WC(vesik\u00e4ym\u00e4l\u00e4)"})

(defn get-varuste-as-krysp [varuste]
  (or (varuste varuste->krysp-varuste)
      (name varuste)))


(def hulevesi->krysp-hulevesi
  {:ojaan "johdetaan rajaojaan tai muuhun ojaan"
   :imeytetaan "imeytet\u00e4\u00e4n maaper\u00e4\u00e4n"})

(defn hulevedet [hulevesi]
  (:or (:hulevesi hulevesi->krysp-hulevesi)
       hulevesi))

(defn- get-vapautus-kohde [{property-id :propertyId} documents]
    (let [kiinteisto (:data (first (:vesihuolto-kiinteisto documents)))]
      (merge {:kiinteistorekisteritunnus property-id
             :kiinteistonRakennusTieto
             (for [[_ rakennus] (sort (:kiinteistoonKuuluu kiinteisto))]
               (let [kohteenVarustelutaso (remove nil? (map (fn [[k v]] (when v (get-varuste-as-krysp k))) (:kohteenVarustelutaso rakennus)))] {:KiinteistonRakennus
                               {:kayttotarkoitustieto {:kayttotarkoitus (lower-case (:rakennuksenTyypi rakennus))}
                                :kohteenVarustelutaso (not-empty kohteenVarustelutaso)
                                :haetaanVapautustaKytkin (true? (:vapautus rakennus))}}))}
             {:hulevedet (:hulevedet (hulevedet (:data (first (:hulevedet documents)))))}
             (get-talousvedet (:data (first (:talousvedet documents))))
             {:jatevedet (:kuvaus (:data (first (:jatevedet documents))))})))

(defn- hakija [hakijat]
  ;(clojure.pprint/pprint hakijat)
  (assert (= 1 (count hakijat)))
  (->ymp-osapuoli (first hakijat)))

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
         {:hakija (remove nil? (map get-yhteystiedot (:hakija documents)))
          :kohde (get-vapautus-kohde application documents)
          :sijaintitieto (get-sijaintitieto application)}}
        :asianKuvaus (:kuvaus (:data (first (:hankkeen-kuvaus-vesihuolto documents))))
        }}}})
  )