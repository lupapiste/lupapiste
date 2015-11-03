(ns lupapalvelu.xml.krysp.mapping-common
  (:require [clojure.string :as str]
            [lupapalvelu.i18n :refer [with-lang loc localize]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(def- rakval-yht {"2.1.2" "2.1.0"
                  "2.1.3" "2.1.1"
                  "2.1.4" "2.1.2"
                  "2.1.5" "2.1.3"
                  "2.1.6" "2.1.5"
                  "2.1.8" "2.1.5"
                  "2.2.0" "2.1.6"})

(def- ya-yht {"2.1.2" "2.1.0"
              "2.1.3" "2.1.3"
              "2.2.0" "2.1.5"
              "2.2.1" "2.1.6"})

(def- poik-yht {"2.1.2" "2.1.0"
                "2.1.3" "2.1.1"
                "2.1.4" "2.1.2"
                "2.1.5" "2.1.3"
                "2.2.0" "2.1.5"
                "2.2.1" "2.1.6"})

(def- ymp-yht {"2.1.2" "2.1.3"})

(def- vvvl-yht {"2.1.3" "2.1.3"})

(def- kt-yht {"0.9"   "2.1.3"
              "0.9.1" "2.1.4"
              "0.9.2" "2.1.5"
              "1.0.0" "2.1.5"
              "1.0.1" "2.1.5"
              "1.0.2" "2.1.6"})

(def- mm-yht {"0.9"   "2.1.5"
              "1.0.0" "2.1.5"
              "1.0.1" "2.1.6"})

(def- yht-version
  {:R rakval-yht
   :P poik-yht
   :YA ya-yht
   :MAL ymp-yht
   :YI ymp-yht
   :YL ymp-yht
   :VVVL vvvl-yht
   :KT kt-yht
   :MM mm-yht})

(defn get-yht-version [permit-type ns-version]
  {:pre [(permit/valid-permit-type? (name permit-type))
         ((-> yht-version keys set) (keyword permit-type))]}
  (get-in yht-version [(keyword permit-type) ns-version] "0.0.0"))

(defn xsd-filename [ns-name]
  (case ns-name
    "yleisenalueenkaytonlupahakemus" "YleisenAlueenKaytonLupahakemus.xsd"
    "maa_ainesluvat" "maaAinesluvat.xsd"
    (str (ss/suffix ns-name "/") ".xsd")))

(defn- paikkatietopalvelu [ns-name ns-version]
  (format "http://www.paikkatietopalvelu.fi/gml/%s http://www.paikkatietopalvelu.fi/gml/%s/%s/%s"
    ns-name
    ns-name
    ns-version
    (xsd-filename ns-name)))

(defn schemalocation [permit-type ns-version]
  {:pre [(get-yht-version permit-type ns-version)]}
  (let [ns-name (permit/get-metadata permit-type :wfs-krysp-ns-name)]
    (str
     (paikkatietopalvelu "yhteiset" (get-yht-version permit-type ns-version))
     "\nhttp://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd\n"
     (paikkatietopalvelu ns-name ns-version))))

(def common-namespaces
  {:xmlns:yht   "http://www.paikkatietopalvelu.fi/gml/yhteiset"
   :xmlns:gml   "http://www.opengis.net/gml"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi   "http://www.w3.org/2001/XMLSchema-instance"})

(defn update-child-element
  "Utility for updating mappings: replace child in a given path with v.
     children: sequence of :tag, :child maps
     path: keyword sequence
     v: the new value or a function that produces the new value from the old"
  [children path v]
  (map
    #(if (= (:tag %) (first path))
      (if (seq (rest path))
        (update-in % [:child] update-child-element (rest path) v)
        (if (fn? v)
          (v %)
          v))
      %)
    children))

(defn in-yhteiset-ns [coll] (mapv (fn [m] (assoc m :ns "yht")) coll))

(def tunnus-children [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}])

(def tunnus-children-216 [{:tag :valtakunnallinenNumero}
                          {:tag :kunnanSisainenPysyvaRakennusnumero}
                          {:tag :jarjestysnumero}
                          {:tag :kiinttun}
                          {:tag :rakennusnro}
                          {:tag :aanestysalue}
                          {:tag :katselmusOsittainen}
                          {:tag :kayttoonottoKytkin}
                          {:tag :muuTunnus}
                          {:tag :rakennuksenSelite}])

(def- postiosoite-children [{:tag :kunta}
                            {:tag :osoitenimi :child [{:tag :teksti}]}
                            {:tag :postinumero}
                            {:tag :postitoimipaikannimi}])

(def- postiosoite-children-215 [{:tag :kunta}
                                {:tag :valtioSuomeksi}
                                {:tag :valtioKansainvalinen}
                                {:tag :osoitenimi :child [{:tag :teksti}]}
                                {:tag :ulkomainenLahiosoite}
                                {:tag :postinumero}
                                {:tag :postitoimipaikannimi}
                                {:tag :ulkomainenPostitoimipaikka}])

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def postiosoite-children-ns-yht (in-yhteiset-ns postiosoite-children))
(def postiosoite-children-ns-yht-215 (in-yhteiset-ns postiosoite-children-215))

(def gml-point {:tag :Point :ns "gml" :child [{:tag :pos}]})

(def sijantiType {:tag :Sijainti
                  :child [{:tag :osoite :ns "yht"
                           :child [{:tag :yksilointitieto}
                                   {:tag :alkuHetki}
                                   {:tag :osoitenimi
                                    :child [{:tag :teksti}]}]}
                          {:tag :piste :ns "yht"
                           :child [gml-point]}
                          {:tag :viiva :ns "yht"
                           :child [{:tag :LineString :ns "gml"
                                    :child [{:tag :pos}]}]}
                          {:tag :alue :ns "yht"
                           :child [{:tag :Polygon :ns "gml"
                                    :child [{:tag :exterior
                                             :child [{:tag :LinearRing
                                                      :child [{:tag :pos}]}]} ]}]}
                          {:tag :tyhja :ns "yht"}]})

(def sijantiType_215
  (update-in sijantiType [:child] conj
    {:tag :nimi :ns "yht"}
    {:tag :kuvaus :ns "yht"}
    {:tag :korkeusTaiSyvyys :ns "yht"}
    {:tag :pintaAla :ns "yht"}))

(defn sijaintitieto
  "Takes an optional xml namespace for Sijainti element"
  [& [xmlns]]
  {:tag :sijaintitieto
   :child [(merge
             sijantiType
             (when xmlns {:ns xmlns}))]})

(def- rakennusoikeudet [:tag :rakennusoikeudet
                        :child [{:tag :kayttotarkoitus
                                 :child [{:tag :pintaAla}
                                         {:tag :kayttotarkoitusKoodi}]}]])

(def yksilointitieto {:tag :yksilointitieto :ns "yht"})

(def alkuHetki {:tag :alkuHetki :ns "yht"})

(def rakennuspaikanKiinteistotieto {:tag :rakennuspaikanKiinteistotieto :ns "yht"
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
                                                     {:tag :vuokraAluetunnus}]}]})

(def rakennuspaikka {:tag :Rakennuspaikka
                     :child [yksilointitieto
                             alkuHetki
                             rakennuspaikanKiinteistotieto
                             {:tag :kaavanaste :ns "yht"}
                             {:tag :kerrosala :ns "yht"}
                             {:tag :tasosijainti :ns "yht" }
                             {:tag :rakennusoikeudet  :ns "yht"
                              :child [{:tag :kayttotarkoitus
                                       :child [{:tag :pintaAla}
                                               {:tag :kayttotarkoitusKoodi}]}]}
                             {:tag :rakennusoikeusYhteensa :ns "yht" }
                             {:tag :uusiKytkin :ns "yht"}]})

(def rakennuspaikka_211 (update-in rakennuspaikka [:child] conj {:tag :kaavatilanne :ns "yht"}))

(def rakennuspaikka_216 {:tag :Rakennuspaikka
                         :child [yksilointitieto
                                 alkuHetki
                                 rakennuspaikanKiinteistotieto
                                 {:tag :kaavanaste :ns "yht"}
                                 {:tag :kerrosala :ns "yht"}
                                 {:tag :rakennusoikeudellinenKerrosala :ns "yht"}
                                 {:tag :tasosijainti :ns "yht" }
                                 {:tag :rakennusoikeudet  :ns "yht"
                                  :child [{:tag :kayttotarkoitus
                                           :child [{:tag :pintaAla}
                                                   {:tag :kayttotarkoitusKoodi}]}]}
                                 {:tag :rakennusoikeusYhteensa :ns "yht" }
                                 {:tag :uusiKytkin :ns "yht"}
                                 {:tag :kaavatilanne :ns "yht"}]})

(def- henkilo-child [{:tag :nimi
                      :child [{:tag :etunimi}
                              {:tag :sukunimi}]}
                     {:tag :osoite :child postiosoite-children}
                     {:tag :sahkopostiosoite}
                     {:tag :faksinumero}
                     {:tag :puhelin}
                     {:tag :henkilotunnus}])

(def- henkilo-child-215
  (update-child-element henkilo-child [:osoite] {:tag :osoite :child postiosoite-children-215}))

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def henkilo-child-ns-yht (in-yhteiset-ns henkilo-child))
(def henkilo-child-ns-yht-215 (in-yhteiset-ns henkilo-child-215))

(def yritys-child_211 [{:tag :nimi}
                       {:tag :liikeJaYhteisotunnus}
                       {:tag :kayntiosoite :child postiosoite-children}
                       {:tag :kotipaikka}
                       {:tag :postiosoite :child postiosoite-children}
                       {:tag :faksinumero}
                       {:tag :puhelin}
                       {:tag :www}
                       {:tag :sahkopostiosoite}])

(def verkkolaskutus_213
  {:tag :Verkkolaskutus
   :ns "yht"
   :child [{:tag :ovtTunnus}
           {:tag :verkkolaskuTunnus}
           {:tag :valittajaTunnus}]})

(def yritys-child_213
  (in-yhteiset-ns (-> yritys-child_211
                    (conj {:tag :verkkolaskutustieto :child [verkkolaskutus_213]})
                    (update-child-element [:kayntiosoite] {:tag :kayntiosoitetieto :child [{:tag :kayntiosoite :child postiosoite-children}]})
                    (update-child-element [:postiosoite]  {:tag :postiosoitetieto  :child [{:tag :postiosoite  :child postiosoite-children}]}))))

(def yritys-child_215
  (in-yhteiset-ns (-> yritys-child_213
                    (update-child-element [:kayntiosoitetieto :kayntiosoite] {:tag :kayntiosoite :child postiosoite-children-215})
                    (update-child-element [:postiosoitetieto :postiosoite]   {:tag :postiosoite  :child postiosoite-children-215}))))

(def yritys-child-ns-yht_211 [{:tag :nimi}
                              {:tag :liikeJaYhteisotunnus}
                              {:tag :kayntiosoite :child postiosoite-children-ns-yht}
                              {:tag :kotipaikka}
                              {:tag :postiosoite :child postiosoite-children-ns-yht}
                              {:tag :faksinumero}
                              {:tag :puhelin}
                              {:tag :www}
                              {:tag :sahkopostiosoite}])

(def yritys-child-ns-yht_213 [{:tag :nimi}
                              {:tag :liikeJaYhteisotunnus}
                              {:tag :kayntiosoitetieto :child [{:tag :kayntiosoite :child postiosoite-children-ns-yht}]}
                              {:tag :postiosoitetieto  :child [{:tag :postiosoite  :child postiosoite-children-ns-yht}]}
                              {:tag :kotipaikka}
                              {:tag :faksinumero}
                              {:tag :puhelin}
                              {:tag :www}
                              {:tag :sahkopostiosoite}
                              {:tag :verkkolaskutustieto :child [verkkolaskutus_213]}])

(def yritys-child-ns-yht_215
  (-> yritys-child-ns-yht_213
    (update-child-element [:kayntiosoitetieto :kayntiosoite] {:tag :kayntiosoite :child postiosoite-children-ns-yht-215})
    (update-child-element [:postiosoitetieto :postiosoite]   {:tag :postiosoite  :child postiosoite-children-ns-yht-215})))

(def henkilo {:tag :henkilo :ns "yht" :child henkilo-child})
(def henkilo_215 {:tag :henkilo :ns "yht" :child henkilo-child-215})

(def yritys_211 {:tag :yritys :ns "yht" :child yritys-child_211})
(def yritys_213 {:tag :yritys :ns "yht" :child yritys-child_213})
(def yritys_215 {:tag :yritys :ns "yht" :child yritys-child_215})

(def osapuoli-body_211 {:tag :Osapuoli
                        :child [{:tag :kuntaRooliKoodi}
                                {:tag :VRKrooliKoodi}
                                henkilo
                                yritys_211
                                {:tag :turvakieltoKytkin}]})

(def osapuoli-body_213 (update-in osapuoli-body_211 [:child] update-child-element [:yritys] yritys_213))

(def osapuoli-body_215
  (-> osapuoli-body_213
    (update-in [:child] update-child-element [:henkilo] henkilo_215)
    (update-in [:child] update-child-element [:yritys] yritys_215)))

(def osapuoli-body_216
  (update-in osapuoli-body_215 [:child] conj {:tag :suoramarkkinointikieltoKytkin}))

(def- naapuri {:tag :naapuritieto
               :child [{:tag :Naapuri
                        :child [{:tag :henkilo}
                                {:tag :kiinteistotunnus}
                                {:tag :hallintasuhde}]}]})

(def- naapuri-216 {:tag :naapuritieto
                   :child [{:tag :Naapuri
                            :child [{:tag :henkilo}
                                    {:tag :osoite}
                                    {:tag :kiinteistotunnus}
                                    {:tag :hallintasuhde}
                                    {:tag :saanutTiedoksiannonKytkin}
                                    {:tag :huomautettavaaKytkin}
                                    {:tag :haluaaPaatoksenKytkin}
                                    {:tag :huomautus}]}]})

(def tyonjohtaja_210
  {:tag :Tyonjohtaja
   :child [{:tag :tyonjohtajaRooliKoodi}
           {:tag :VRKrooliKoodi}
           henkilo
           yritys_211
           {:tag :patevyysvaatimusluokka}
           {:tag :koulutus}
           {:tag :valmistumisvuosi}
           {:tag :tyonjohtajaHakemusKytkin}
           {:tag :vaadittuPatevyysluokka}]})

(def tyonjohtaja_211
  {:tag :Tyonjohtaja
   :child [{:tag :tyonjohtajaRooliKoodi}
           {:tag :VRKrooliKoodi}
           henkilo
           yritys_211
           {:tag :patevyysvaatimusluokka}
           {:tag :vaadittuPatevyysluokka}
           {:tag :koulutus}
           {:tag :valmistumisvuosi}
           {:tag :alkamisPvm}
           {:tag :paattymisPvm}
           {:tag :tyonjohtajaHakemusKytkin}
           {:tag :kokemusvuodet}
           {:tag :sijaistustieto
            :child [{:tag :Sijaistus
                     :child [{:tag :sijaistettavaHlo}
                             {:tag :sijaistettavaRooli}
                             {:tag :alkamisPvm}
                             {:tag :paattymisPvm}]}]}]})

(def tyonjohtaja_212
  {:tag :Tyonjohtaja
   :child [{:tag :tyonjohtajaRooliKoodi}
           {:tag :VRKrooliKoodi}
           henkilo
           yritys_211
           {:tag :patevyysvaatimusluokka}
           {:tag :vaadittuPatevyysluokka}
           {:tag :koulutus}
           {:tag :valmistumisvuosi}
           {:tag :alkamisPvm}
           {:tag :paattymisPvm}
           ;{:tag :valvottavienKohteidenMaara}  ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
           {:tag :tyonjohtajaHakemusKytkin}
           {:tag :kokemusvuodet}
           {:tag :vastattavaTyotieto
            :child [{:tag :VastattavaTyo
                     :child [{:tag :vastattavaTyo} ; string
                             {:tag :alkamisPvm} ; date
                             {:tag :paattymisPvm}]}]}
           {:tag :sijaistettavaHlo}]})

(def tyonjohtaja_213 (update-in tyonjohtaja_212 [:child] (comp vec update-child-element) [:yritys] yritys_213))

(def tyonjohtaja_215 (-> tyonjohtaja_213
                       (update-in [:child] conj {:tag :vainTamaHankeKytkin})
                       (update-in [:child] update-child-element [:yritys] yritys_215)
                       (update-in [:child] update-child-element [:henkilo] henkilo_215)))

(def tyonjohtajatieto_210
  {:tag :tyonjohtajatieto :child [tyonjohtaja_210]})

(def tyonjohtajatieto_211
  {:tag :tyonjohtajatieto :child [tyonjohtaja_211]})

(def tyonjohtajatieto_212
  {:tag :tyonjohtajatieto :child [tyonjohtaja_212]})

(def tyonjohtajatieto_213
  {:tag :tyonjohtajatieto :child [tyonjohtaja_213]})

(def tyonjohtajatieto_215
  {:tag :tyonjohtajatieto :child [tyonjohtaja_215]})

(def osapuolet_210
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto
            :child [osapuoli-body_211]}
           {:tag :suunnittelijatieto
            :child [{:tag :Suunnittelija
                     :child [{:tag :suunnittelijaRoolikoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys_211
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             ]}]}
           tyonjohtajatieto_210
           naapuri]})

(def suunnittelijatieto_211
  {:tag :suunnittelijatieto
   :child [{:tag :Suunnittelija
            :child [{:tag :suunnittelijaRoolikoodi}
                    {:tag :VRKrooliKoodi}
                    henkilo
                    yritys_211
                    {:tag :patevyysvaatimusluokka}
                    {:tag :koulutus}
                    {:tag :valmistumisvuosi}
                    {:tag :kokemusvuodet}]}]})

(def suunnittelijatieto_213
  (update-in suunnittelijatieto_211 [:child] update-child-element [:Suunnittelija :yritys] yritys_213))

(def suunnittelijatieto_215
  (-> suunnittelijatieto_213
    (update-in [:child] update-child-element [:Suunnittelija :yritys] yritys_215)
    (update-in [:child] update-child-element [:Suunnittelija :henkilo] henkilo_215)))

(def osapuolet_211
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto :child [osapuoli-body_211]}
           suunnittelijatieto_211
           tyonjohtajatieto_211
           naapuri]})

(def osapuolet_212
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto :child [osapuoli-body_211]}
           suunnittelijatieto_211
           tyonjohtajatieto_212
           naapuri]})

(def osapuolet_213
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto :child [osapuoli-body_213]}
           suunnittelijatieto_213
           tyonjohtajatieto_213
           naapuri]})

(def osapuolet_215
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto :child [osapuoli-body_215]}
           suunnittelijatieto_215
           tyonjohtajatieto_215
           naapuri]})

(def osapuolet_216
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto :child [osapuoli-body_216]}
           suunnittelijatieto_215
           tyonjohtajatieto_215
           naapuri-216]})

(def tilamuutos
  {:tag :Tilamuutos :ns "yht"
   :child [{:tag :pvm}
           {:tag :tila}
           {:tag :kasittelija :child [henkilo]}]})

(def lupatunnus {:tag :LupaTunnus :ns "yht"
                 :child [{:tag :kuntalupatunnus}
                         {:tag :muuTunnustieto :child [{:tag :MuuTunnus :child [{:tag :tunnus} {:tag :sovellus}]}]}
                         {:tag :saapumisPvm}
                         {:tag :viittaus}]})

(def toimituksenTiedot [{:tag :aineistonnimi :ns "yht"}
                        {:tag :aineistotoimittaja :ns "yht"}
                        {:tag :tila :ns "yht"}
                        {:tag :toimitusPvm :ns "yht"}
                        {:tag :kuntakoodi :ns "yht"}
                        {:tag :kielitieto :ns "yht"}])

(def liite-children_211 [{:tag :kuvaus :ns "yht"}
                         {:tag :linkkiliitteeseen :ns "yht"}
                         {:tag :muokkausHetki :ns "yht"}
                         {:tag :versionumero :ns "yht"}
                         {:tag :tekija :ns "yht"
                          :child [{:tag :kuntaRooliKoodi}
                                  {:tag :VRKrooliKoodi}
                                  henkilo
                                  yritys_211]}
                         {:tag :tyyppi :ns "yht"}
                         {:tag :metatietotieto :ns "yht"
                          :child [{:tag :metatieto
                                   :child [{:tag :metatietoArvo}
                                           {:tag :metatietoNimi}]}]}])

(def liite-children_213 (update-child-element liite-children_211 [:tekija :yritys] yritys_213))

(def liite-children_216 (conj liite-children_213
                          {:tag :rakennustunnustieto :ns "yht"
                           :child [{:tag :Rakennustunnus
                                    :child tunnus-children-216}]}))

; yht:LausuntoRvPType or yak:LausuntoType
(def lausunto_211 {:tag :Lausunto
                   :child [{:tag :viranomainen :ns "yht"}
                           {:tag :pyyntoPvm :ns "yht"}
                           {:tag :lausuntotieto :ns "yht"
                            :child [{:tag :Lausunto
                                     :child [{:tag :viranomainen}
                                             {:tag :lausunto}
                                             {:tag :liitetieto
                                              :child [{:tag :Liite :child liite-children_211}]}
                                             {:tag :lausuntoPvm}
                                             {:tag :puoltotieto
                                              :child [{:tag :Puolto
                                                       :child [{:tag :puolto}]}]}]}]}]})

(def lausunto_213
  (update-in lausunto_211 [:child]
    update-child-element
    [:lausuntotieto :Lausunto :liitetieto :Liite]
    {:tag :Liite :child liite-children_213}))

(def lausunto_216
  (update-in lausunto_213 [:child]
    update-child-element
    [:lausuntotieto :Lausunto :liitetieto :Liite]
    {:tag :Liite :child liite-children_216}))

(def ymp-kasittelytieto-children [{:tag :muutosHetki :ns "yht"}
                                  {:tag :hakemuksenTila :ns "yht"}
                                  {:tag :asiatunnus :ns "yht"}
                                  {:tag :paivaysPvm :ns "yht"}
                                  {:tag :kasittelija :ns "yht"
                                   :child [{:tag :henkilo
                                            :child [{:tag :nimi
                                                     :child [{:tag :etunimi}
                                                             {:tag :sukunimi}]}]}]}])

(def yhteystietotype-children_213
  (in-yhteiset-ns
    [{:tag :henkilotunnus}
     {:tag :sukunimi}
     {:tag :etunimi}
     {:tag :yTunnus}
     {:tag :yrityksenNimi}
     {:tag :yhteyshenkilonNimi}
     {:tag :osoitetieto :child [{:tag :Osoite :child postiosoite-children}]}
     {:tag :puhelinnumero}
     {:tag :sahkopostiosoite}
     {:tag :suoramarkkinointikielto}
     {:tag :verkkolaskutustieto :child [verkkolaskutus_213]}]))

(def maksajatype-children_213
  (conj yhteystietotype-children_213 {:tag :laskuviite :ns "yht"}))

(def maksajatype-children_215
  (update-child-element maksajatype-children_213
    [:osoitetieto :Osoite]
    {:tag :Osoite :child postiosoite-children-ns-yht-215}))

(defn get-child-element [mapping path]
  (let [children (if (map? mapping) (:child mapping) mapping)]
    (some
      #(when (= (:tag %) (first path))
         (if (seq (rest path))
           (get-child-element % (rest path))
           %))
      children)))

(defn statements-ids-with-status [lausuntotieto]
  (reduce
    (fn [r l]
      (if (get-in l [:Lausunto :lausuntotieto :Lausunto :puoltotieto :Puolto :puolto])
        (conj r (get-in l [:Lausunto :id]))
        r))
    #{} lausuntotieto))

(defn get-Liite [title link attachment type file-id filename & [meta]]
  {:kuvaus title
   :linkkiliitteeseen link
   :muokkausHetki (util/to-xml-datetime (:modified attachment))
   :versionumero 1
   :tyyppi type
   :metatietotieto meta
   :fileId file-id
   :filename filename})

(defn- create-metatieto [k v]
  {:metatieto {:metatietoNimi k :metatietoArvo v}
   :Metatieto {:metatietoNimi k :metatietoArvo v}})

(defn- get-attachment-meta [attachment]
  (let [signatures (:signatures attachment)
        latestVersion (:latestVersion attachment)
        liitepohja [(create-metatieto "liiteId" (:id attachment))]
        signatures (->> signatures
                           (filter #(and
                                     (= (get-in % [:version :major]) (get-in latestVersion [:version :major]))
                                     (= (get-in % [:version :minor]) (get-in latestVersion [:version :minor]))))
                           (map #(let [firstName (get-in %2 [:user :firstName])
                                       lastName (get-in %2 [:user :lastName])
                                       created (util/to-xml-datetime (:created %2))
                                       count %1]
                                  [(create-metatieto (str "allekirjoittaja_" count) (str firstName " " lastName))
                                   (create-metatieto (str "allekirjoittajaAika_" count) created)]) (range))
                           (flatten)
                           (vec))]
    (if (empty? signatures)
      liitepohja
      (into liitepohja signatures))))

(defn get-liite-for-lausunto [attachment application begin-of-link]
  (let [type "Lausunto"
        title (str (:title application) ": " type "-" (:id attachment))
        file-id (get-in attachment [:latestVersion :fileId])
        attachment-file-name (writer/get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
        link (str begin-of-link attachment-file-name)
        meta (get-attachment-meta attachment)]
    {:Liite (get-Liite title link attachment type file-id attachment-file-name meta)}))

(defn get-statement-attachments-as-canonical [application begin-of-link allowed-statement-ids]
  (let [statement-attachments-by-id (group-by
                                      (util/fn-> :target :id keyword)
                                      (filter
                                        (util/fn-> :target :type (= "statement"))
                                        (:attachments application)))
        canonical-attachments (for [id allowed-statement-ids]
                                {(keyword id) (for [attachment ((keyword id) statement-attachments-by-id)]
                                                (get-liite-for-lausunto attachment application begin-of-link))})]
    (not-empty canonical-attachments)))

(defn get-attachments-as-canonical [{:keys [attachments title]} begin-of-link & [target]]
  (not-empty (for [attachment attachments
                   :when (and (:latestVersion attachment)
                           (not= "statement" (-> attachment :target :type))
                           (not= "verdict" (-> attachment :target :type))
                           (or (nil? target) (= target (:target attachment))))
                   :let [type-group (get-in attachment [:type :type-group])
                         type-id (get-in attachment [:type :type-id])
                         attachment-localized-name (localize "fi" (ss/join "." ["attachmentType" type-group type-id]))
                         attachment-title (if (:contents attachment)
                                            (str attachment-localized-name ": " (:contents attachment))
                                            attachment-localized-name)
                         file-id (get-in attachment [:latestVersion :fileId])
                         attachment-file-name (writer/get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
                         link (str begin-of-link attachment-file-name)
                         meta (get-attachment-meta attachment)]]
               {:Liite (get-Liite attachment-title link attachment type-id file-id attachment-file-name meta)})))

(defn add-statement-attachments [canonical statement-attachments lausunto-path]
  (if (empty? statement-attachments)
    canonical
    (reduce
      (fn [c a]
        (let [lausuntotieto (get-in c lausunto-path)
              lausunto-id (name (first (keys a)))
              paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id) %) lausuntotieto)
              index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
              paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausuntotieto :Lausunto :liitetieto] ((keyword lausunto-id) a))
              paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
          (assoc-in c lausunto-path paivitetty)))
      canonical
      statement-attachments)))

(defn flatten-statement-attachments [statement-attachments]
  (let [attachments (for [statement statement-attachments] (vals statement))]
    (reduce concat (reduce concat attachments))))

(defn attachment-details-from-canonical
  "Returns sequence of attachment details as maps from canonical"
  [attachments]
  (map
    (fn [a]
      {:fileId (get-in a [:Liite :fileId])
       :filename (get-in a [:Liite :filename])})
    attachments))

(defn- make-seq [a]
  (if (sequential? a)
    a
    [a]))

(defn taggy
  "Returns list [map ns] where the map contains :tag and :ns (if given).
  The tag name is defined by argument k. The format is
  :tagname/ns where the namespace part is optional.  Note: namespace
  is returned but not used on this element.  The namespace for this
  element is the ns argument or nothing."
  [k & [ns]]
  (let [[tag new-ns] (-> k str rest str/join (str/split #"/"))]
    [(merge (when ns {:ns ns})
            {:tag (keyword tag)}) (or new-ns ns)]))

(defmulti mapper
  "Recursively generates a 'traditional' mapping (with :tag, :ns
  and :child properties) from the shorthand form. As the shorthand
  uses lists, maps and keywords, each type is handled by its
  corresponding method.
  Note: The root element must be defined separately. See the
  ->mapping functions in the client code for details."
  (fn [& args]
    (let [arg (first args)]
      (if (map? arg)
        :map
        (if (keyword? arg)
          :keyword
          (if (sequential? arg)
            :sequential))))))

(defmethod mapper :map [m & [ns]]
  (let [k (-> m keys first)
        [tag ns] (taggy k ns)
        v (k m)]
    ;; Mapping sanity check
    (assert (= (count m) 1) k)
    (assoc tag :child (make-seq (mapper v ns)))))

(defmethod mapper :keyword [kw & [ns]]
  (first (taggy kw ns)))

(defmethod mapper :sequential [xs & [ns]]
  (map #(mapper % ns) xs))
