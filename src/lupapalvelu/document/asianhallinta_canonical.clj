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

(defn- ua-get-hakijat [documents]
  (for [hakija documents
        :let [hakija (:data hakija)]]
    (merge {:Hakija
            {:Henkilo
             {:Etunimi (get-in hakija [:henkilo :henkilotiedot :etunimi])
             :Sukunimi (get-in hakija [:henkilo :henkilotiedot :sukunimi])
             :Yhteystiedot {
              :Jakeluosoite (get-in hakija [:henkilo :osoite :katu])
              :Postinumero (get-in hakija [:henkilo :osoite :postinumero])
              :Postitoimipaikka (get-in hakija [:henkilo :osoite :postitoimipaikannimi])
              :Maa "Suomi" ; TODO voiko olla muu?
              :Email (get-in hakija [:henkilo :yhteystiedot :email])
              :Puhelin (get-in hakija [:henkilo :yhteystiedot :puhelin])}
             :Henkilotunnus (get-in hakija [:henkilo :henkilotiedot :hetu])
             :VainSahkoinenAsiointi nil ; TODO tarviiko tätä ja Turvakieltoa?
             :Turvakielto nil}}})))

;; TaydennysAsiaan, prefix: ta-


;; AsianPaatos, prefix: ap-


;; AsianTunnusVastaus, prefix: atr-


(defn application-to-asianhallinta-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)]
    (-> (assoc-in ua-root-element [:UusiAsia :Tyyppi] (ua-get-asian-tyyppi-string application))
      (assoc-in [:UusiAsia :Kuvaus] (:title application))
      (assoc-in [:UusiAsia :Kuntanumero] (:municipality application))
      (assoc-in [:UusiAsia :Hakijat] (ua-get-hakijat (:hakija documents))))))

