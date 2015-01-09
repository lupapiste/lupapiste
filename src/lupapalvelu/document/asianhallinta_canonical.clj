(ns lupapalvelu.document.asianhallinta_canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]
           [clojure.string :as s]))


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

(defn- ua-get-henkilo [data]
  {:Etunimi (get-in data [:henkilo :henkilotiedot :etunimi])
   :Sukunimi (get-in data [:henkilo :henkilotiedot :sukunimi])
   :Yhteystiedot {
                  :Jakeluosoite (get-in data [:henkilo :osoite :katu])
                  :Postinumero (get-in data [:henkilo :osoite :postinumero])
                  :Postitoimipaikka (get-in data [:henkilo :osoite :postitoimipaikannimi])
                  :Maa "Suomi" ; TODO voiko olla muu?
                  :Email (get-in data [:henkilo :yhteystiedot :email])
                  :Puhelin (get-in data [:henkilo :yhteystiedot :puhelin])}
   :Henkilotunnus (get-in data [:henkilo :henkilotiedot :hetu])
   :VainSahkoinenAsiointi nil ; TODO tarviiko tätä ja Turvakieltoa?
   :Turvakielto nil})

(defn- ua-get-hakijat [documents]
  (for [hakija documents
        :let [hakija (:data hakija)]]
    (merge {:Hakija ; TODO yritys
            {:Henkilo (ua-get-henkilo hakija)}})))

(defn- ua-get-maksaja [document]
  (let [sel (get-in document [:data :_selected])]
    {(condp = sel
       "yritys" {}
       "henkilo" {})
     :Laskuviite (get-in document [:data :laskuviite])
     :Verkkolaskutustieto (when (= "yritys" sel)
                            {:OVT-tunnus (get-in document [:data :yritys :verkkolaskutustieto :ovtTunnus])
                             :Verkkolaskutunnus (get-in document [:data :yritys :verkkolaskutustieto :verkkolaskuTunnus])
                             :Operaattoritunnus (get-in document [:data :yritys :verkkolaskutustieto :valittajaTunnus])})}))

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
      (assoc-in [:UusiAsia :Maksaja] (ua-get-maksaja (first (:maksaja documents)))))))
