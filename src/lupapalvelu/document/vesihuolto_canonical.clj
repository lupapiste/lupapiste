(ns lupapalvelu.document.vesihuolto-canonical
  (require [lupapalvelu.document.vesihuolto-schemas :as vh-schemas]
           [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]))


(defn get-talousvedet [talousvedet]
  {:talousvedet
   {:hankinta (:hakinta talousvedet)
    :johdatus (:johdatus talousvedet)
    :riittavyys (:riittavyys talousvedet)}})


(defn get-vapautus-kohde [{property-id :PropertyId} documents]
    (let [kiinteisto (first (:vesihuolto-kiinteisto documents))]
      (merge {:kiinteistorekisteritunnus property-id
             :kiinteistonRakennusTieto
             (for [rakennus (:kiinteistoonKuuluu (:vesihuolto-kiinteisto documents))]
               {:KiinteistonRakennus
                {:kayttotarkoitustieto (:rakennuksenTyypi rakennus)
                 :kohteenVarustelutaso (map (fn [[k v]] (when v (name k))) (:kohteenVarustelutaso rakennus))
                 :vapautus (true? (:vapautus rakennus))}})}
             {:hulevedet (:hulevedet (first (:hulevedet documents)))}
             (get-talousvedet (first (:talousvedet documents)))
             {:jatevedet (:kuvaus (first (:jatevedet documents)))})))

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
         {:hakija (get-henkilo (first (:hakija documents)))
          :kohde (get-vapautus-kohde application documents)}}
        :sijaintitieto (get-sijaintitieto application)

        :asianKuvaus (:kuvaus (first (:hankkeen-kuvaus-vesihuolto documents)))
        }}}})
  )