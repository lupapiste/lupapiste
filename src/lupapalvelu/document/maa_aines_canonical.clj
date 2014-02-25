(ns lupapalvelu.document.maa-aines-canonical
  (require [sade.util :as util]
           [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
           [lupapalvelu.document.tools :as tools]))

(defn ->osapuoli [unwrapped-party-doc]
  (if (= (-> unwrapped-party-doc :data :_selected) "yritys")
    (let [yritys (-> unwrapped-party-doc :data :yritys)]
      (assoc (canonical-common/get-henkilo (:yhteyshenkilo yritys))
        :osoite (canonical-common/get-simple-osoite (:osoite yritys))))
    (let [henkilo (-> unwrapped-party-doc :data :henkilo)]
      (assoc (canonical-common/get-henkilo henkilo)
        :osoite (canonical-common/get-simple-osoite (:osoite henkilo))))))

(defn sijainti [application]
  (let [sijaintitieto (first (canonical-common/get-sijaintitieto application))]
    (assoc-in sijaintitieto [:Sijainti :osoite :yksilointitieto] (:propertyId application))))

(defn maa-aines-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        hakija    (-> documents :hakija first)
        maksaja   (-> documents :maksaja first)
        kuvaus    (-> documents :maa-aineslupa-kuvaus first :data :kuvaus)]
    {:MaaAinesluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :maaAineslupaAsiatieto
      {:MaaAineslupaAsia
       {:yksilointitieto (:id application)
        :alkuHetki (util/to-xml-datetime (:created application))
        :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))
        :lausuntotieto (canonical-common/get-statements (:statements application))
        :hakemustieto
        {:Hakemus
         {:hakija (->osapuoli hakija)
          ;ottamistoiminnanYhteyshenkilo ?
          :alueenKiinteistonSijainti (sijainti application)
          ;ottamismaara ?
          ;paatoksenToimittaminen ?
          :viranomaismaksujenSuorittaja (->osapuoli maksaja)
          ;ottamissuunnitelmatieto TODO
          }
         }
        :sijaintitieto (sijainti application)
        :koontiKentta kuvaus
        }}}}))
