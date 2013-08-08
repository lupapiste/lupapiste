(ns lupapalvelu.xml.krysp.mapping-common)

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

(def henkilo
  {:tag :henkilo :ns "yht"
   :child [{:tag :nimi
            :child [{:tag :etunimi}
                    {:tag :sukunimi}]}
           {:tag :osoite :child postiosoite-children}
           {:tag :sahkopostiosoite}
           {:tag :faksinumero}
           {:tag :puhelin}
           {:tag :henkilotunnus}]})

(def yritys {:tag :yritys :ns "yht"
             :child [{:tag :nimi}
                     {:tag :kayntiosoite :child postiosoite-children}
                     {:tag :liikeJaYhteisotunnus}
                     {:tag :kotipaikka}
                     {:tag :postiosoite :child postiosoite-children}
                     {:tag :faksinumero}
                     {:tag :puhelin}
                     {:tag :www}
                     {:tag :sahkopostiosoite}]})

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
           {:tag :tyonjohtajatieto}
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

(def lausunto {:tag :Lausunto
               :child [{:tag :viranomainen :ns "yht"}
                       {:tag :pyyntoPvm :ns "yht"}
                       {:tag :lausuntotieto :ns "yht"
                        :child [{:tag :Lausunto
                                 :child [{:tag :viranomainen}
                                         {:tag :lausunto}
                                         {:tag :liitetieto
                                          :child [{:tag :Liite
                                                   :child [{:tag :kuvaus :ns "yht"}
                                                           {:tag :linkkiliitteeseen :ns "yht"}
                                                           {:tag :muokkausHetki :ns "yht"}
                                                           {:tag :versionumero :ns "yht"}
                                                           {:tag :tekija :ns "yht"
                                                            :child [{:tag :kuntaRooliKoodi}
                                                                    {:tag :VRKrooliKoodi}
                                                                    henkilo
                                                                    yritys]}
                                                           {:tag :tyyppi :ns "yht"}]}]}
                                         {:tag :lausuntoPvm}
                                         {:tag :puoltotieto
                                          :child [{:tag :Puolto
                                                   :child [{:tag :puolto}]}]}]}]}]})
