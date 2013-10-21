(ns lupapalvelu.xml.krysp.mapping-common
  (:require [lupapalvelu.document.canonical-common :refer [to-xml-datetime]]
            [lupapalvelu.attachment :refer [encode-filename]]))


(def tunnus-children [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}])

(def ^:private piste {:tag :piste  :ns "yht"
                      :child [{:tag :Point
                               :child [{:tag :pos}]}]})

(def ^:private postiosoite-children [{:tag :kunta}
                                     {:tag :osoitenimi :child [{:tag :teksti}]}
                                     {:tag :postinumero}
                                     {:tag :postitoimipaikannimi}])

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def postiosoite-children-ns-yht (into [] (map (fn [m] (assoc m :ns "yht")) postiosoite-children)))

(def ^:private osoite {:tag :osoite  :ns "yht"
                       :child postiosoite-children})

(def sijantitieto {:tag :sijaintitieto
                   :child [{:tag :Sijainti
                            :child [{:tag :tyhja  :ns "yht"}
                                     osoite
                                     piste
                                     {:tag :sijaintiepavarmuus  :ns "yht"}
                                     {:tag :luontitapa  :ns "yht"}]}]})

(def ^:private rakennusoikeudet [:tag :rakennusoikeudet
                                 :child [{:tag :kayttotarkoitus
                                          :child [{:tag :pintaAla}
                                                  {:tag :kayttotarkoitusKoodi}]}]])

(def ^:private kiinteisto [{:tag :kiinteisto
                            :child (conj [{:tag :kiinteisto
                                           :child [{:tag :kylanimi}
                                                   {:tag :tilannimi}
                                                   {:tag :kiinteistotunnus}
                                                   {:tag :maaraAlaTunnus}]}
                                          {:tag :palsta}
                                          {:tag :kokotilaKytkin}
                                          {:tag :hallintaperuste}
                                          {:tag :vuokraAluetunnus}
                                          {:tag :kaavanaste}
                                          {:tag :kerrosala}
                                          {:tag :tasosijainti}
                                          {:tag :rakennusoikeusYhteensa}
                                          {:tag :uusiKytkin}]
                                     osoite
                                     sijantitieto
                                     rakennusoikeudet)}])

(def rakennuspaikka {:tag :Rakennuspaikka
                     :child [{:tag :yksilointitieto :ns "yht"}
                             {:tag :alkuHetki :ns "yht"}
                             {:tag :rakennuspaikanKiinteistotieto :ns "yht"
                              :child [{:tag :RakennuspaikanKiinteisto
                                       :child [{:tag :kiinteistotieto
                                                :child [{:tag :Kiinteisto
                                                         :child [{:tag :kylanimi}
                                                                 {:tag :tilannimi}
                                                                 {:tag :kiinteistotunnus}
                                                                 {:tag :maaraAlaTunnus}]}]}
                                               {:tag :palsta}
                                               {:tag :kokotilaKytkin}
                                               {:tag :hallintaperuste}
                                               {:tag :vuokraAluetunnus}]}]}
                             {:tag :kaavanaste :ns "yht"}
                             {:tag :kerrosala :ns "yht"}
                             {:tag :tasosijainti :ns "yht" }
                             {:tag :rakennusoikeudet  :ns "yht"
                              :child [{:tag :kayttotarkoitus
                                       :child [{:tag :pintaAla}
                                               {:tag :kayttotarkoitusKoodi}]}]}
                             {:tag :rakennusoikeusYhteensa :ns "yht" }
                             {:tag :uusiKytkin :ns "yht"}]})


(def ^:private henkilo-child [{:tag :nimi
                               :child [{:tag :etunimi}
                                       {:tag :sukunimi}]}
                              {:tag :osoite :child postiosoite-children}
                              {:tag :sahkopostiosoite}
                              {:tag :faksinumero}
                              {:tag :puhelin}
                              {:tag :henkilotunnus}])

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def henkilo-child-ns-yht (into [] (map (fn [m] (assoc m :ns "yht")) henkilo-child)))

(def yritys-child [{:tag :nimi}
                   {:tag :liikeJaYhteisotunnus}
                   {:tag :kayntiosoite :child postiosoite-children}
                   {:tag :kotipaikka}
                   {:tag :postiosoite :child postiosoite-children}
                   {:tag :faksinumero}
                   {:tag :puhelin}
                   {:tag :www}
                   {:tag :sahkopostiosoite}])

(def yritys-child-ns-yht [{:tag :nimi}
                          {:tag :liikeJaYhteisotunnus}
                          {:tag :kayntiosoite :child postiosoite-children-ns-yht}
                          {:tag :kotipaikka}
                          {:tag :postiosoite :child postiosoite-children-ns-yht}
                          {:tag :faksinumero}
                          {:tag :puhelin}
                          {:tag :www}
                          {:tag :sahkopostiosoite}])

(def henkilo {:tag :henkilo :ns "yht"
              :child henkilo-child})

(def yritys {:tag :yritys :ns "yht"
             :child yritys-child})

(def osapuoli-body {:tag :Osapuoli
                    :child [{:tag :kuntaRooliKoodi}
                            {:tag :VRKrooliKoodi}
                            henkilo
                            yritys
                            {:tag :turvakieltoKytkin}]})

(def osapuolet
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto
            :child [osapuoli-body]}
           {:tag :suunnittelijatieto
            :child [{:tag :Suunnittelija
                     :child [{:tag :suunnittelijaRoolikoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}]}]}
           {:tag :tyonjohtajatieto
            :child [{:tag :Tyonjohtaja
                     :child [{:tag :tyonjohtajaRooliKoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             {:tag :valmistumisvuosi}
                             ;{:tag :kokemusvuodet}  ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             {:tag :tyonjohtajaHakemusKytkin}]}]}
           {:tag :naapuritieto}]})

(def tilamuutos
  {:tag :Tilamuutos :ns "yht"
   :child [{:tag :pvm}
           {:tag :tila}
           {:tag :kasittelija :child [henkilo]}]})

(def lupatunnus {:tag :LupaTunnus :ns "yht" :child [{:tag :muuTunnustieto
                                                     :child [{:tag :MuuTunnus :child [{:tag :tunnus}
                                                                                      {:tag :sovellus}]}]}
                                                    {:tag :saapumisPvm}]})

(def toimituksenTiedot [{:tag :aineistonnimi :ns "yht"}
                        {:tag :aineistotoimittaja :ns "yht"}
                        {:tag :tila :ns "yht"}
                        {:tag :toimitusPvm :ns "yht"}
                        {:tag :kuntakoodi :ns "yht"}
                        {:tag :kielitieto :ns "yht"}])


(defn get-file-name-on-server [file-id file-name]
  (str file-id "_" (encode-filename file-name)))

(defn get-submitted-filename [application-id]
  (str application-id "_submitted_application.pdf"))

(defn get-current-filename [application-id]
  (str application-id "_current_application.pdf"))

(defn statements-ids-with-status [lausuntotieto]
  (reduce
    (fn [r l]
      (if (get-in l [:Lausunto :lausuntotieto :Lausunto :puoltotieto :Puolto :puolto])
        (conj r (get-in l [:Lausunto :id]))
        r))
    #{} lausuntotieto))
