(ns lupapalvelu.document.asianhallinta_canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]
           [clojure.string :as s]
           [sade.util :as util]
           [lupapalvelu.i18n :as i18n]))


;; UusiAsia, functions prefixed with ua-

(def uusi-asia {:UusiAsia
                {:Tyyppi nil
                 :Kuvaus nil
                 :Kuntanumero nil
                 :Hakijat nil
                 :Maksaja nil
                 :HakemusTunnus nil
                 :VireilletuloPvm nil
                 :Liitteet nil
                 :Asiointikieli nil
                 :Toimenpiteet nil
                 :Viiteluvat {:Viitelupa nil}}})

(def ^:private ua-root-element {:UusiAsia nil})

(defn- ua-get-asian-tyyppi-string [application]
  ; KasiteltavaHakemus, TODO later: Tiedoksianto
  "KasiteltavaHakemus")

(defn- ua-get-yhteystiedot [data]
  (util/strip-nils
    {:Jakeluosoite (get-in data [:osoite :katu])
     :Postinumero (get-in data [:osoite :postinumero])
     :Postitoimipaikka (get-in data [:osoite :postitoimipaikannimi])
     :Maa nil ; TODO
     :Email (get-in data [:yhteystiedot :email])
     :Puhelin (get-in data [:yhteystiedot :puhelin])}))

(defn- ua-get-yhteyshenkilo [data]
  (util/strip-nils
    {:Etunimi (get-in data [:yhteyshenkilo :henkilotiedot :etunimi])
     :Sukunimi (get-in data [:yhteyshenkilo :henkilotiedot :sukunimi])
     :Yhteystiedot (ua-get-yhteystiedot (:yhteyshenkilo data))}))

(defn- ua-get-henkilo [data]
  (util/strip-nils
    {:Etunimi (get-in data [:henkilo :henkilotiedot :etunimi])
     :Sukunimi (get-in data [:henkilo :henkilotiedot :sukunimi])
     :Yhteystiedot (ua-get-yhteystiedot (:henkilo data))
     :Henkilotunnus (get-in data [:henkilo :henkilotiedot :hetu])
     :VainSahkoinenAsiointi nil ; TODO tarviiko tata ja Turvakieltoa?
     :Turvakielto nil}))

(defn- ua-get-yritys [data]
  {:Nimi (get-in data [:yritys :yritysnimi])
   :Ytunnus (get-in data [:yritys :liikeJaYhteisoTunnus])
   :Yhteystiedot (ua-get-yhteystiedot (:yritys data))
   :Yhteyshenkilo (ua-get-yhteyshenkilo (:yritys data))})

(defn- ua-get-hakijat [documents]
  (when (seq documents)
    {:Hakija (map
               (fn [doc]
                 (let [hakija-data (:data doc)
                       sel (:_selected hakija-data)]
                   (condp = sel
                     "henkilo" {:Henkilo (ua-get-henkilo hakija-data)}
                     "yritys" {:Yritys (ua-get-yritys hakija-data)})))
               documents)}))

(defn- ua-get-maksaja [document]
  (let [sel (get-in document [:_selected])
        maksaja-map (util/strip-nils
                      {:Laskuviite (get-in document [:laskuviite])
                       :Verkkolaskutustieto (when (= "yritys" sel)
                                              {:OVT-tunnus (get-in document [:yritys :verkkolaskutustieto :ovtTunnus])
                                               :Verkkolaskutunnus (get-in document [:yritys :verkkolaskutustieto :verkkolaskuTunnus])
                                               :Operaattoritunnus (get-in document [:yritys :verkkolaskutustieto :valittajaTunnus])})})]

    (condp = sel
       "yritys" (assoc-in maksaja-map [:Yritys] (ua-get-yritys document))
       "henkilo" (assoc-in maksaja-map [:Henkilo] (ua-get-henkilo document)))))

(defn- ua-get-metatiedot [attachment]
  (seq [{:Avain "type-group" :Arvo (get-in attachment [:type :type-group])}
        {:Avain "type-id"    :Arvo (get-in attachment [:type :type-id])}]))

(defn- ua-get-liite [attachment]
  (util/strip-nils
    {:Kuvaus (get-in attachment [:latestVersion :filename])
     :Tyyppi (get-in attachment [:latestVersion :contentType])
     :LinkkiLiitteeseen (get-in attachment [:latestVersion :filename]) ;TODO
     :Luotu (util/to-xml-date (:modified attachment))
     :Metatiedot {:Metatieto (ua-get-metatiedot attachment)}}))

(defn- ua-get-liitteet [{:keys [attachments]}]
  (when (seq attachments)
    {:Liite (map ua-get-liite attachments)}))

(defn- ua-get-toimenpiteet [{:keys [operations]} lang]
  (when (seq operations)
    {:Toimenpide (map #(i18n/localize lang "operations" (:name %)) operations)}))


;; TaydennysAsiaan, prefix: ta-


;; AsianPaatos, prefix: ap-


;; AsianTunnusVastaus, prefix: atr-


(defn application-to-asianhallinta-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)]
    (-> (assoc-in ua-root-element [:UusiAsia :Tyyppi] (ua-get-asian-tyyppi-string application))
      (assoc-in [:UusiAsia :Kuvaus] (:title application))
      (assoc-in [:UusiAsia :Kuntanumero] (:municipality application))
      (assoc-in [:UusiAsia :Hakijat] (ua-get-hakijat (:hakija documents)))
      (assoc-in [:UusiAsia :Maksaja] (ua-get-maksaja (:data (first (:maksaja documents)))))
      (assoc-in [:UusiAsia :HakemusTunnus] (:id application))
      (assoc-in [:UusiAsia :VireilletuloPvm] (util/to-xml-date (:submitted application)))
      (assoc-in [:UusiAsia :Liitteet] (ua-get-liitteet application))
      (assoc-in [:UusiAsia :Asiointikieli] lang)
      (assoc-in [:UusiAsia :Toimenpiteet] (ua-get-toimenpiteet application lang)))))
