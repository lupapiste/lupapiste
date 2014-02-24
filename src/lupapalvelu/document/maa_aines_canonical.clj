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

(defn maa-aines-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        hakija    (-> documents :hakija first)
        kuvaus    nil]
    {:MaaAinesluvat
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :maaAineslupaAsiatieto
      {:MaaAineslupaAsia
       {:yksilointitieto (:id application)
        :alkuHetki (util/to-xml-datetime (:created application))
        :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :KasittelyTieto)
        :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))
        :lausuntotieto (canonical-common/get-statements (:statements application))
        :hakemustieto {:Hakemus {:hakija (->osapuoli hakija)}}
        :sijaintitieto (first (canonical-common/get-sijaintitieto application))
        }}}}))
