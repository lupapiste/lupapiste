(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]))

(defn- ilmoittaja [hakijat]
  (assert (= 1 (count hakijat)))
  (let [hakija (first hakijat)]
    (if (= (-> hakija :_selected :value) "yritys")
      (= 1 1)
      {:nimi "Yksityishenkil\u00f6"
       :postiosoite (get-simple-osoite (-> hakija :data :henkilo :osoite))
       :yhteyshenkilo (get-henkilo (-> hakija :data :henkilo))})))

(defn meluilmoitus-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)]
    {:Ilmoitukset {:toimutuksenTiedot (toimituksen-tiedot application lang)
                   :melutarina {:kasittelytietotieto (get-kasittelytieto application)
                                :luvanTunnistetiedot (lupatunnus (:id application))
                                :lausuntotieto (get-statements (:statements application))
                                :ilmoittaja (ilmoittaja (:hakija documents))
                                :toiminnanSijainti {:Osoite {:osoitenimi {:teksti (:address application)}
                                                            :kunta (:municipality application)}
                                                   :Kunta (:municipality application)
                                                   :Sijainti (:Sijainti (first (get-sijaintitieto application)))
                                                   :Kiinteistorekisterinumero (:propertyId application)}}}}))