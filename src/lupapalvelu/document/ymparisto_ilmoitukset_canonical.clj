(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [sade.util :refer [to-xml-date to-xml-datetime assoc-when]]))

(defn meluilmoitus-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)
        meluilmo (first (:meluilmoitus documents))
        kesto (:kesto (:data (first (:ymp-ilm-kesto documents))))
        kello (:kello kesto)
        melu (-> meluilmo :data :melu)]
    {:Ilmoitukset {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
                   :melutarina {:Melutarina {:yksilointitieto (:id application)
                                             :alkuHetki (to-xml-datetime (:submitted application))
                                             :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
                                             :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))
                                             :lausuntotieto (canonical-common/get-statements (:statements application))
                                             :ilmoittaja (canonical-common/get-yhteystiedot (first (:hakija documents)))
                                             :toiminnanSijaintitieto
                                             {:ToiminnanSijainti
                                              {:Osoite {:osoitenimi {:teksti (:address application)}
                                                       :kunta (:municipality application)}
                                               :Kunta (:municipality application)
                                               :Sijainti (:Sijainti (first (canonical-common/get-sijaintitieto application)))
                                               :Kiinteistorekisterinumero (:propertyId application)}}
                                             ; TODO map :Sijainti (:Sijainti (rest (get-sijaintitieto application)))
                                             :toimintatieto {:Toiminta (assoc-when {:yksilointitieto (:id meluilmo)
                                                                                    :alkuHetki (to-xml-datetime (:created meluilmo))}
                                                                                   :rakentaminen
                                                                                   (when (or (-> meluilmo :data :rakentaminen :melua-aihettava-toiminta ) (-> meluilmo :data :rakentaminen :muu-rakentaminen ))
                                                                                     {(keyword (or (-> meluilmo :data :rakentaminen :melua-aihettava-toiminta ) "muu")) (-> meluilmo :data :rakentaminen :kuvaus)})
                                                                                   :tapahtuma (when (-> meluilmo :data :tapahtuma :nimi)
                                                                                                {(keyword (if (-> meluilmo :data :tapahtuma :ulkoilmakonsertti)
                                                                                                            :ulkoilmakonsertti
                                                                                                            :muu))
                                                                                                 (str (-> meluilmo :data :tapahtuma :nimi) " - " (-> meluilmo :data :tapahtuma :kuvaus))}))}
                                             ; FIXME
                                             :toiminnanKesto {:alkuPvm (to-xml-date-from-string (:alku kesto))
                                                              :loppuPvm (to-xml-date-from-string (:loppu kesto))
                                                              ;:arkisin (:arkisin kello)
                                                              ;:lauantaisin (:lauantait kello)
                                                              ;:pyhisin (:pyhat kello)
                                                              }
                                             :melutiedot {:melutaso {:db (:melu10mdBa melu)
                                                                     :paiva (:paivalla melu)
                                                                     :yo (:yolla melu)
                                                                     :mittaaja (:mittaus melu)
                                                                     }}}}}}))