(ns lupapalvelu.document.vesihuolto-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.i18n :as i18n]
            [sade.strings :refer [lower-case]]))


(defn- get-talousvedet [talousvedet]
  (when talousvedet
    {:talousvedet
     {:hankinta (if (= (:hankinta talousvedet) "other")
                  {:muu (:muualta talousvedet)}
                  {:hankinta (lower-case (:hankinta talousvedet))})
      :johdatus (:johdatus talousvedet)
      :riittavyys (:riittavyys talousvedet)}}))

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
  (when hulevesi
    (if (= (:hulevedet hulevesi) "other")
      {:hulevedet {:muu (:johdetaanMuualle hulevesi)}}
      {:hulevedet {:hulevedet ((keyword (:hulevedet hulevesi)) hulevesi->krysp-hulevesi)}})))

(defn- get-vapautus-kohde [{property-id :propertyId} documents]
    (let [kiinteisto (:data (first (:vesihuolto-kiinteisto documents)))]
      (merge {:kiinteistorekisteritunnus property-id
             :kiinteistonRakennusTieto
             (for [[_ rakennus] (sort (:kiinteistoonKuuluu kiinteisto))]
               (let [kohteenVarustelutaso (remove nil? (map (fn [[k v]] (when v (get-varuste-as-krysp k))) (:kohteenVarustelutaso rakennus)))] {:KiinteistonRakennus
                               {:kayttotarkoitustieto {:kayttotarkoitus (lower-case (:rakennuksenTyypi rakennus))}
                                :kohteenVarustelutaso (not-empty kohteenVarustelutaso)
                                :haetaanVapautustaKytkin (true? (:vapautus rakennus))}}))}
             (hulevedet (:data (first (:hulevedet documents))))
             (get-talousvedet (:data (first (:talousvedet documents))))
             (when (:jatevedet documents)
               {:jatevedet (:kuvaus (:data (first (:jatevedet documents))))}))))

(defn vapautus-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)
        kuvaus (-> (:hankkeen-kuvaus-vesihuolto documents) first :data :kuvaus)
        operation-name (->> (:primaryOperation application) :name (i18n/localize lang "operations"))
        asian-kuvaus (str operation-name " / " kuvaus)
        hakija-key (keyword (operations/get-applicant-doc-schema-name application))]
    {:Vesihuoltolaki
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :vapautukset
      {:Vapautus
       {:kasittelytietotieto (get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (lupatunnus application)
        :lausuntotieto (get-statements (:statements application))
        :vapautusperuste ""
        :vapautushakemustieto
        {:Vapautushakemus
         ;; KuntaGML supports only one hakija at maximum.
         {:hakija (->> (get documents hakija-key)
                       (map get-yhteystiedot)
                       (remove nil?)
                       (take 1))
          :kohde (get-vapautus-kohde application documents)
          :sijaintitieto (get-sijaintitieto application)}}
        :asianKuvaus asian-kuvaus}}}})
  )
