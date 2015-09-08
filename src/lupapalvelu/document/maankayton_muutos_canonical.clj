(ns lupapalvelu.document.maankayton-muutos-canonical
  (:require [clojure.string :as str]
            [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.document.tools :as tools ]
            [lupapalvelu.permit :as permit]
            [sade.util :as util]))

(defn entry [entry-key m & [force-key]]
  (let [k (or force-key entry-key)
        v (entry-key m)]
    (when-not (nil? v)
      {k v})))

(defn schema-info-filter
  ([docs prop]
   (filter #(get-in % [:schema-info prop]) docs))
  ([docs prop value]
   (filter #(= (get-in % [:schema-info prop]) value) docs)))


(defn ->postiosoite-type [address]
  (merge {:osoitenimi (entry :katu address :teksti)}
         (entry :postinumero address)
         (entry :postitoimipaikannimi address)))

(defmulti osapuolitieto :_selected)


(defmethod osapuolitieto "henkilo"
  [{{contact :yhteystiedot personal :henkilotiedot address :osoite} :henkilo}]
  (merge (entry :turvakieltoKytkin personal :turvakieltokytkin)
         {:henkilotieto {:Henkilo
                         (merge {:nimi (merge (entry :etunimi personal)
                                              (entry :sukunimi personal))}
                                {:osoite (->postiosoite-type address)}
                                (entry :email contact :sahkopostiosoite)
                                (entry :puhelin contact)
                                (entry :hetu personal :henkilotunnus))}}))

(defmethod osapuolitieto "yritys"
  [{company :yritys}]
  (let [{contact :yhteystiedot personal :henkilotiedot} (:yhteyshenkilo company)]
    (merge (entry :turvakieltoKytkin personal :turvakieltokytkin))
    {:Yritys (merge (entry :yritysNimi company :nimi)
                   (entry :liikeJaYhteisoTunnus company )
                   {:postiosoitetieto (->postiosoite-type (:osoite company))}
                   (entry :puhelin contact)
                   (entry :email contact :sahkopostiosoite))}))

(defn process-party [lang {{role :subtype} :schema-info data :data}]
  {:Osapuoli (merge {:roolikoodi (str/capitalize role)
                     :asioimiskieli lang
                     :vainsahkoinenAsiointiKytkin false} (osapuolitieto data))})

(defn filter-parties [docs lang]
  (map (partial process-party lang) (schema-info-filter docs :type "party")))

(defn application-state [app]
  (let [state (-> app :state keyword)
        date (util/to-xml-date (state app))
        {a-first :firstName a-last :lastName} (:authority app)]
    {:Tila
     {:pvm date
      :kasittelija (format "%s %s" a-first a-last)
      :hakemuksenTila (state canonical-common/application-state-to-krysp-state)}}))


(defn maankayton-muutos-canonical [application lang]
  (let [app (tools/unwrapped application)
        docs  (canonical-common/documents-without-blanks app)
        [op-doc] (schema-info-filter docs :op)
        op-name (-> op-doc :schema-info :op :name keyword)
        {op-age :uusiKytkin op-desc :kuvaus} (:data op-doc)
        parties (filter-parties docs lang)
        [{{property :kiinteisto} :data}] (schema-info-filter docs :name "kiinteisto")]
    {:Maankaytonmuutos
     {:maankayttomuutosTieto
      {op-name
       {:toimituksenTiedottieto
        {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
        :hakemustieto
        {:Hakemus
         {:osapuolitieto parties
          :sijaintitieto (canonical-common/get-sijaintitieto application)
          :kohdekiinteisto (:propertyId application)
          :maaraAla (:maaraalaTunnus property)
          :tilatieto (application-state app)}}
        :uusiKytkin (= op-age "uusi")
        :kuvaus op-desc}}}}))
