(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [sade.util :refer :all]))

(defn- ilmoittaja [hakijat]
  ;(clojure.pprint/pprint hakijat)
  (assert (= 1 (count hakijat)))
  (->ymp-osapuoli (first hakijat)))

(defn meluilmoitus-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)
        meluilmo (first (:meluilmoitus documents))
        kesto (:kesto (:data (first (:ymp-ilm-kesto documents))))
        kello (:kello kesto)
        melu (-> meluilmo :data :melu)]
    {:Ilmoitukset {:toimituksenTiedot (toimituksen-tiedot application lang)
                   :melutarina {:Melutarina {:yksilointitieto (:id application)
                                             :alkuHetki (to-xml-datetime (:submitted application))
                                             :kasittelytietotieto (get-kasittelytieto-ymp application :KasittelyTieto)
                                             :luvanTunnistetiedot (lupatunnus (:id application))
                                             :lausuntotieto (get-statements (:statements application))
                                             :ilmoittaja (ilmoittaja (:hakija documents))
                                             :toiminnanSijainti {:Osoite {:osoitenimi {:teksti (:address application)}
                                                                          :kunta (:municipality application)}
                                                                 :Kunta (:municipality application)
                                                                 :Sijainti (:Sijainti (first (get-sijaintitieto application)))
                                                                 :Kiinteistorekisterinumero (:propertyId application)}
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
                                             :toiminnanKesto {:alkuHetki (to-xml-datetime-from-string (:alku kesto))
                                                              :loppuHetki (to-xml-datetime-from-string (:loppu kesto))
                                                              :arkisin (:arkisin kello)
                                                              :lauantaisin (:lauantait kello)
                                                              :pyhisin (:pyhat kello)}
                                             :melutiedot {:melutaso {:db (:melu10mdBa melu)
                                                                     :paiva (:paivalla melu)
                                                                     :yo (:yolla melu)
                                                                     :mittaaja (:mittaus melu)
                                                                     }}}}}}))