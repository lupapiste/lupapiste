(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.operations :as operations]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- convert-kesto-row [[_ row]]
  (apply merge
         {:alkuPvm  (date/xml-date (:alku row))
          :loppuPvm (date/xml-date (:loppu row))}
         (-> (dissoc row :alku :loppu)
             (util/convert-values util/to-xml-time-from-string))))

(defn meluilmoitus-canonical [application lang]
  (let [unwrapped-docs {:documents (tools/unwrapped (:documents application))}
        documents (canonical-common/stripped-documents-by-type unwrapped-docs)
        meluilmo (first (:meluilmoitus documents))
        sijainti-seq (canonical-common/get-sijaintitieto application)
        rakentamisen-kuvaus (-> meluilmo :data :rakentaminen :kuvaus)
        muu-rakentaminen (-> meluilmo :data :rakentaminen :muu-rakentaminen)
        muu-rakentaminen? (not (ss/blank? muu-rakentaminen))
        kestot (-> (:ymp-ilm-kesto documents) first :data :kesto)
        melu (-> meluilmo :data :melu)
        hakija-key (keyword (operations/get-applicant-doc-schema-name application))]
    {:Ilmoitukset {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
                   :melutarina {:Melutarina {:yksilointitieto (:id application)
                                             :alkuHetki (date/xml-datetime (:submitted application))
                                             :kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
                                             :luvanTunnistetiedot (canonical-common/lupatunnus application)
                                             :lausuntotieto (canonical-common/get-statements (:statements application))
                                             :ilmoittaja (canonical-common/get-yhteystiedot (first (get documents hakija-key)))
                                             :toiminnanSijaintitieto
                                             (cons
                                               {:ToiminnanSijainti
                                                {:Osoite {:osoitenimi {:teksti (:address application)}
                                                         :kunta (:municipality application)}
                                                 :Kunta (:municipality application)
                                                 :Sijainti (:Sijainti (first sijainti-seq))
                                                 :Kiinteistorekisterinumero (:propertyId application)}}
                                               (map
                                                 (fn [sijainti]
                                                   {:ToiminnanSijainti (assoc sijainti :Osoite {:osoitenimi {:teksti canonical-common/empty-tag}})}) ; Osoite is mandatory
                                                 (rest sijainti-seq)))
                                             :toimintatieto {:Toiminta (util/assoc-when {:yksilointitieto (:id meluilmo)
                                                                                         :alkuHetki (date/xml-datetime (:created meluilmo))}
                                                                                   :rakentaminen
                                                                                   (when (or (-> meluilmo :data :rakentaminen :melua-aihettava-toiminta ) muu-rakentaminen?)
                                                                                     {(keyword (or (-> meluilmo :data :rakentaminen :melua-aihettava-toiminta ) "muu"))
                                                                                      (if muu-rakentaminen?
                                                                                        (str muu-rakentaminen ": " rakentamisen-kuvaus)
                                                                                        rakentamisen-kuvaus)})
                                                                                   :tapahtuma (when (-> meluilmo :data :tapahtuma :nimi)
                                                                                                {(keyword (if (-> meluilmo :data :tapahtuma :ulkoilmakonsertti)
                                                                                                            :ulkoilmakonsertti
                                                                                                            :muu))
                                                                                                 (str (-> meluilmo :data :tapahtuma :nimi) " - " (-> meluilmo :data :tapahtuma :kuvaus))}))}
                                             :toiminnanKesto (first (map convert-kesto-row kestot))
                                             :melutiedot {:koneidenLkm (-> meluilmo :data :rakentaminen :koneet)
                                                          :melutaso {:db (:melu10mdBa melu)
                                                                     :paiva (:paivalla melu)
                                                                     :yo (:yolla melu)
                                                                     :mittaaja (:mittaus melu)}}}}}}))

(defmethod canonical-common/application->canonical :YI [application lang]
  (meluilmoitus-canonical application lang))
