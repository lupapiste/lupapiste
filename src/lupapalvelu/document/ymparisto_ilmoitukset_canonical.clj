(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [sade.util :as util]))

(defn meluilmoitus-canonical [application lang]
  (let [unwrapped-docs {:documents (tools/unwrapped (:documents application))}
        documents (canonical-common/documents-by-type-without-blanks unwrapped-docs)
        meluilmo (first (:meluilmoitus documents))
        kesto (-> (:ymp-ilm-kesto documents) first :data :kesto)
        kello (apply merge (filter map? (vals kesto)))
        melu (-> meluilmo :data :melu)]
    {:Ilmoitukset {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
                   :melutarina {:Melutarina {:yksilointitieto (:id application)
                                             :alkuHetki (util/to-xml-datetime (:submitted application))
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
                                             :toimintatieto {:Toiminta (util/assoc-when {:yksilointitieto (:id meluilmo)
                                                                                         :alkuHetki (util/to-xml-datetime (:created meluilmo))}
                                                                                   :rakentaminen
                                                                                   (when (or (-> meluilmo :data :rakentaminen :melua-aihettava-toiminta ) (-> meluilmo :data :rakentaminen :muu-rakentaminen ))
                                                                                     {(keyword (or (-> meluilmo :data :rakentaminen :melua-aihettava-toiminta ) "muu")) (-> meluilmo :data :rakentaminen :kuvaus)})
                                                                                   :tapahtuma (when (-> meluilmo :data :tapahtuma :nimi)
                                                                                                {(keyword (if (-> meluilmo :data :tapahtuma :ulkoilmakonsertti)
                                                                                                            :ulkoilmakonsertti
                                                                                                            :muu))
                                                                                                 (str (-> meluilmo :data :tapahtuma :nimi) " - " (-> meluilmo :data :tapahtuma :kuvaus))}))}
                                             ; FIXME
                                             :toiminnanKesto (merge
                                                               {:alkuPvm (util/to-xml-date-from-string (:alku kesto))
                                                                :loppuPvm (util/to-xml-date-from-string (:loppu kesto))}
                                                               (util/convert-values kello util/to-xml-time-from-string))
                                             :melutiedot {:melutaso {:db (:melu10mdBa melu)
                                                                     :paiva (:paivalla melu)
                                                                     :yo (:yolla melu)
                                                                     :mittaaja (:mittaus melu)
                                                                     }}}}}}))