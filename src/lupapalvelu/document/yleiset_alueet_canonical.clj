(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.document.canonical-common :refer :all]))

(defn- get-henkilo [henkilo]
;  (println "\n get-henkilo(), henkilo: " henkilo)
  {:nimi {:etunimi (-> henkilo :henkilotiedot :etunimi :value)
          :sukunimi (-> henkilo :henkilotiedot :sukunimi :value)}
   :osoite {:osoitenimi {:teksti (-> henkilo :osoite :katu :value)}
            :postinumero (-> henkilo :osoite :postinumero :value)
            :postitoimipaikannimi (-> henkilo :osoite :postitoimipaikannimi :value)}
   :sahkopostiosoite (-> henkilo :yhteystiedot :email :value)
   :puhelin (-> henkilo :yhteystiedot :puhelin :value)
   :henkilotunnus (-> henkilo :henkilotiedot :hetu :value)})

(defn- get-henkilo-reduced [henkilo]
;  (println "\n get-henkilo-reduced(), henkilo: " henkilo)
  (dissoc (get-henkilo henkilo) :osoite :henkilotunnus))

(defn- get-yritys [yritys]
;  (println "\n get-yritys(), yritys: " yritys)
  (merge
    {:nimi (-> yritys :yritysnimi :value)
     :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
     :postiosoitetieto {:Postiosoite {:osoitenimi {:teksti (-> yritys :osoite :katu :value)}
                                      :postinumero (-> yritys :osoite :postinumero :value)
                                      :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)}}}))

(defn- get-yritys-maksaja [yritys]
;  (println "\n get-yritys-maksaja(), yritys: " yritys)
  (merge
    {:nimi (-> yritys :yritysnimi :value)
     :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
     :postiosoite {:osoitenimi {:teksti (-> yritys :osoite :katu :value)}
                   :postinumero (-> yritys :osoite :postinumero :value)
                   :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)}}))

(defn- get-hakija [hakija-doc]
;  (println "\n get-hakija(), hakija-doc: ")
;  (clojure.pprint/pprint hakija-doc)
;  (println "\n")
  (merge
    (if (= (-> hakija-doc :_selected :value) "yritys")
      {:yritystieto  {:Yritys (get-yritys (:yritys hakija-doc))}
       :henkilotieto {:Henkilo (get-henkilo-reduced (-> hakija-doc :yritys :yhteyshenkilo))}}
      ;; If _selected == "henkilo", then yritys info is left out...
      {:henkilotieto {:Henkilo (get-henkilo (:henkilo hakija-doc))}})
    {:rooliKoodi "hakija"}))

(defn- get-vastuuhenkilo-osoitetieto [osoite]
  {:osoite {:osoitenimi {:teksti (-> osoite :katu :value)}
            :postinumero (-> osoite :postinumero :value)
            :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)}})

(defn- get-vastuuhenkilo [vastuuhenkilo type roolikoodi]
;  (println "\n get-vastuuhenkilo(), vastuuhenkilo (type: \"" type "\", roolikoodi: \"" roolikoodi "\"): ")
;  (clojure.pprint/pprint vastuuhenkilo)
;  (println "\n")
  (merge
    (if (= type :yritys)
      ;; yritys-tyyppinen vastuuhenkilo
      {:sukunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :sukunimi :value)
       :etunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :etunimi :value)
       :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :yritys :osoite))
       :puhelinnumero (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :puhelin :value)
       :sahkopostiosoite (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :email :value)}
      ;; henkilo-tyyppinen vastuuhenkilo
      {:sukunimi (-> vastuuhenkilo :henkilo :henkilotiedot :sukunimi :value)
       :etunimi (-> vastuuhenkilo :henkilo :henkilotiedot :etunimi :value)
       :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :henkilo :osoite))
       :puhelinnumero (-> vastuuhenkilo :henkilo :yhteystiedot :puhelin :value)
       :sahkopostiosoite (-> vastuuhenkilo :henkilo :yhteystiedot :email :value)})
    {:rooliKoodi roolikoodi}))

(defn- get-tyomaasta-vastaava [tyomaasta-vastaava]
;  (println "\n get-tyomaasta-vastaava(), tyomaasta-vastaava: ")
;  (clojure.pprint/pprint tyomaasta-vastaava)
;  (println "\n")
  (if (= (-> tyomaasta-vastaava :_selected :value) "yritys")
    ;; yritys-tyyppinen tyomaasta-vastaava, siirretaan yritysosa omaksi osapuolekseen
    {:osapuolitieto {:Osapuoli {:yritystieto {:Yritys (get-yritys (:yritys tyomaasta-vastaava))}
                                :rooliKoodi "ty\u00f6nsuorittaja"}}
     :vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo
                                           tyomaasta-vastaava
                                           :yritys
                                           "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")}}
    ;; henkilo-tyyppinen tyomaasta-vastaava
    {:vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo
                                           tyomaasta-vastaava
                                           :henkilo
                                           "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")}}))

(defn- get-maksaja [maksaja-doc]
;  (println "\n maksaja-doc: ")
;  (clojure.pprint/pprint maksaja-doc)
;  (println "\n")
  (merge
    (if (= (-> maksaja-doc :_selected :value) "yritys")
      ;; yritys-tyyppinen maksaja, siirretaan yritysosa omaksi osapuolekseen
      {:vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo               ;; vastuuhenkilotieto
                                             maksaja-doc
                                             :yritys
                                             "maksajan vastuuhenkil\u00f6")}
       :yritystieto {:Yritys (get-yritys-maksaja (:yritys maksaja-doc))}}    ;; maksajatieto
      ;; henkilo-tyyppinen maksaja
      {:henkilotieto {:Henkilo (get-henkilo (:henkilo maksaja-doc))}})       ;; maksajatieto
    {:laskuviite (-> maksaja-doc :laskuviite :value)}))

(defn- get-handler [application]
  (if-let [handler (:authority application)]
    {:henkilotieto {:Henkilo {:nimi {:etunimi  (:firstName handler)
                                     :sukunimi (:lastName handler)}}}}
    empty-tag))

(defn- get-kasittelytieto [application]
;  (println "\n get-kasittelytieto , :modified:" (:modified application))
;  (println "\n get-kasittelytieto , :state:" (keyword (:state application)))
;  (println "\n get-kasittelytieto , :state to krysp state:" ((keyword (:state application)) application-state-to-krysp-state))
;  (println "\n get-kasittelytieto , :id:" (:id application))

  {:Kasittelytieto {:muutosHetki (to-xml-datetime (:modified application))
                    :hakemuksenTila ((keyword (:state application)) application-state-to-krysp-state)
                    :asiatunnus (:id application)
                    :paivaysPvm (to-xml-date ((state-timestamps (keyword (:state application))) application))
                    :kasittelija (get-handler application)}})

(defn- get-sijaintitieto [application]
;  (println "\n get-sijaintitieto , sijainti:" sijainti)
  {:Sijainti {:osoite {:yksilointitieto (:id application)
                       :alkuHetki (to-xml-datetime (now))
                       :osoitenimi {:teksti (:address application)}}
              :piste {:Point {:pos (str (:x (:location application)) " " (:y (:location application)))}}}})

;;
;; TODO: Mihin kielitieto (lang) lisataan? Rakval-puolella on lisatiedoissa asioimiskieli.
;;
(defn- permits [application lang]
  (let [documents-by-type (by-type (:documents application))]
;    (println "\n documents-by-type: \n")
;    (clojure.pprint/pprint documents-by-type)
;    (println "\n application: ")
;    (clojure.pprint/pprint application)
;    (clojure.pprint/pprint (:yleiset-alueet-maksaja documents-by-type))
;    (println "(:data (:yleiset-alueet-maksaja documents-by-type)): " (:data (:yleiset-alueet-maksaja documents-by-type)))

    (let [operation-name-key (-> application :operations first :name keyword)
          lupa-name-key (operation-name-key ya-operation-type-to-schema-name-key)
          maksaja (get-maksaja (:data (first (:yleiset-alueet-maksaja documents-by-type))))
          hankkeen-kuvaus (:data (first (:yleiset-alueet-hankkeen-kuvaus-kaivulupa documents-by-type)))
          hakija (get-hakija (:data (first (:hakija-ya documents-by-type))))
          tyomaasta-vastaava (if (= lupa-name-key :Tyolupa)
                               (get-tyomaasta-vastaava (:data (first (:tyomaastaVastaava documents-by-type))))
                               {})
          tyoaika-doc (:data (first (:tyoaika documents-by-type)))
          body {lupa-name-key {:kasittelytietotieto (get-kasittelytieto application)
                               :alkuPvm (to-xml-date-from-string (-> tyoaika-doc :tyoaika-alkaa-pvm :value))
                               :loppuPvm (to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm :value))
                               :sijaintitieto (get-sijaintitieto application)
                               ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter the resulting nil out.
                               :osapuolitieto (into [] (filter :Osapuoli [{:Osapuoli hakija}
                                                                          (:osapuolitieto tyomaasta-vastaava)]))
                               ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
                               :vastuuhenkilotieto (into [] (filter :Vastuuhenkilo [(:vastuuhenkilotieto tyomaasta-vastaava)
                                                                                    (:vastuuhenkilotieto maksaja)]))
                               :maksajatieto {:Maksaja (dissoc maksaja :vastuuhenkilotieto)}
                               :lausuntotieto (get-statements (:statements application))
                               :lupaAsianKuvaus (-> hankkeen-kuvaus :kayttotarkoitus :value)
                               :sijoituslupaviitetieto {:Sijoituslupaviite {:vaadittuKytkin false  ;; TODO: Muuta trueksi?
                                                                            :tunniste (-> hankkeen-kuvaus :sijoitusLuvanTunniste :value)}}
                               :kayttotarkoitus (operation-name-key ya-operation-type-to-usage-description)}}]

      (if (= lupa-name-key :Tyolupa)
        (assoc-in body [lupa-name-key :johtoselvitysviitetieto]
          {:Johtoselvitysviite {:vaadittuKytkin false ;; TODO: Muuta trueksi?
                                ;:tunniste "..."      ;; TODO: Tarvitaanko tunnistetta?
                                }})
        body))))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [app (assoc application :documents
              (clojure.walk/postwalk empty-strings-to-nil (:documents application)))]
    {:YleisetAlueet {:toimituksenTiedot
                     {:aineistonnimi (:title app)
                      :aineistotoimittaja "lupapiste@solita.fi"
                      :tila toimituksenTiedot-tila
                      :toimitusPvm (to-xml-date (now))
                      :kuntakoodi (:municipality app)
                      :kielitieto ""}
                     :yleinenAlueAsiatieto (permits app lang)}}))

