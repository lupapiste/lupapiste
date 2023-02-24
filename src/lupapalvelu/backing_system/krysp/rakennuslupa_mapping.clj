(ns lupapalvelu.backing-system.krysp.rakennuslupa-mapping
  (:require [clojure.walk :as walk]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.rakennuslupa-canonical :as canonical]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.util :as util]))

;RakVal

(def- huoneisto {:tag :huoneisto
                          :child [{:tag :muutostapa}
                                  {:tag :huoneluku}
                                  {:tag :keittionTyyppi}
                                  {:tag :huoneistoala}
                                  {:tag :varusteet
                                   :child [{:tag :WCKytkin}
                                           {:tag :ammeTaiSuihkuKytkin}
                                           {:tag :saunaKytkin}
                                           {:tag :parvekeTaiTerassiKytkin}
                                           {:tag :lamminvesiKytkin}]}
                                  {:tag :huoneistonTyyppi}
                                  {:tag :huoneistotunnus
                                   :child [{:tag :porras}
                                           {:tag :huoneistonumero}
                                           {:tag :jakokirjain}]}]})

(def- huoneisto_224 (update huoneisto :child conj {:tag :valtakunnallinenHuoneistotunnus}))

(def- rakennustunnus
  [{:tag :valtakunnallinenNumero}
   {:tag :jarjestysnumero}
   {:tag :kiinttun}
   {:tag :rakennusnro}])

(def- rakennustunnus_213
  (conj rakennustunnus
    {:tag :katselmusOsittainen}
    {:tag :kayttoonottoKytkin}))

(def- muu-tunnus
  [{:tag :MuuTunnus :child [{:tag :tunnus :ns "yht"}
                            {:tag :sovellus :ns "yht"}]}])

(def- rakennustunnus_216
  (mapping-common/merge-into-coll-after-tag rakennustunnus_213 :valtakunnallinenNumero [{:tag :kunnanSisainenPysyvaRakennusnumero}]))

(def- rakennustunnus_220
  (conj rakennustunnus_216
        {:tag :muuTunnustieto :child muu-tunnus}
        {:tag :rakennuksenSelite}))

(def- rakennus
  {:tag :Rakennus
   :child [{:tag :yksilointitieto :ns "yht"}
           {:tag :alkuHetki :ns "yht"}
           (mapping-common/sijaintitieto)
           {:tag :rakennuksenTiedot
            :child [{:tag :rakennustunnus :child rakennustunnus}
                    {:tag :kayttotarkoitus}
                    {:tag :tilavuus}
                    {:tag :kokonaisala}
                    {:tag :kellarinpinta-ala}
                    {:tag :BIM :child []}
                    ; <osoite> is not mapped, authority sets the address in backend system
                    {:tag :kerrosluku}
                    {:tag :kerrosala}
                    {:tag :rakentamistapa}
                    {:tag :kantavaRakennusaine :child [{:tag :muuRakennusaine}
                                                       {:tag :rakennusaine}]}
                    {:tag :julkisivu
                     :child [{:tag :muuMateriaali}
                             {:tag :julkisivumateriaali}]}
                    {:tag :verkostoliittymat :child [{:tag :viemariKytkin}
                                                     {:tag :vesijohtoKytkin}
                                                     {:tag :sahkoKytkin}
                                                     {:tag :maakaasuKytkin}
                                                     {:tag :kaapeliKytkin}]}
                    {:tag :energialuokka}
                    {:tag :energiatehokkuusluku}
                    {:tag :energiatehokkuusluvunYksikko}
                    {:tag :paloluokka}
                    {:tag :lammitystapa}
                    {:tag :lammonlahde :child [{:tag :polttoaine}
                                               {:tag :muu}]}
                    {:tag :varusteet
                     :child [{:tag :sahkoKytkin}
                             {:tag :kaasuKytkin}
                             {:tag :viemariKytkin}
                             {:tag :vesijohtoKytkin}
                             {:tag :lamminvesiKytkin}
                             {:tag :aurinkopaneeliKytkin}
                             {:tag :hissiKytkin}
                             {:tag :koneellinenilmastointiKytkin}
                             {:tag :saunoja}
                             {:tag :uima-altaita}
                             {:tag :vaestonsuoja}]}
                    {:tag :jaahdytysmuoto}
                    {:tag :asuinhuoneistot :child [huoneisto
                                                   {:tag :asuntojenPintaala}
                                                   {:tag :asuntojenLkm}]}]}
           {:tag :rakentajatyyppi}
           {:tag :omistajatieto
            :child [{:tag :Omistaja
                     :child [{:tag :kuntaRooliKoodi :ns "yht"}
                             {:tag :VRKrooliKoodi :ns "yht"}
                             mapping-common/henkilo
                             mapping-common/yritys_211
                             {:tag :omistajalaji :ns "rakval"
                              :child [{:tag :muu}
                                      {:tag :omistajalaji}]}]}]}]})

(def- rakennus_215
  (update-in rakennus [:child] mapping-common/update-child-element
      [:omistajatieto :Omistaja]
      {:tag :Omistaja :child [{:tag :kuntaRooliKoodi :ns "yht"}
                              {:tag :VRKrooliKoodi :ns "yht"}
                              mapping-common/henkilo
                              mapping-common/yritys_213
                              {:tag :omistajalaji :ns "rakval"
                               :child [{:tag :muu}
                                       {:tag :omistajalaji}]}]}))

(def- rakennus_216
  (-> rakennus_215
      (update-in [:child]
                 mapping-common/update-child-element
                 [:rakennuksenTiedot :rakennustunnus]
                 {:tag :rakennustunnus :child rakennustunnus_216})))

(def- rakennus_220
  (-> rakennus
      (update-in [:child] mapping-common/update-child-element
                 [:omistajatieto :Omistaja]
                 {:tag :Omistaja :child [{:tag :kuntaRooliKoodi :ns "yht"}
                                         {:tag :VRKrooliKoodi :ns "yht"}
                                         mapping-common/henkilo_215
                                         mapping-common/yritys_215
                                         {:tag :turvakieltoKytkin :ns "yht"}
                                         {:tag :suoramarkkinointikieltoKytkin :ns "yht"}
                                         {:tag :omistajalaji :ns "rakval"
                                          :child [{:tag :muu}
                                                  {:tag :omistajalaji}]}]})
      (update-in [:child]
                 mapping-common/update-child-element
                 [:rakennuksenTiedot :rakennustunnus]
                 {:tag :rakennustunnus :child rakennustunnus_220})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennuksenTiedot]
                 #(update-in % [:child] mapping-common/merge-into-coll-after-tag :kerrosala [{:tag :rakennusoikeudellinenKerrosala}]))))

(def- rakennus_223
  (-> rakennus_220
      (update-in [:child] mapping-common/update-child-element
                 [:omistajatieto :Omistaja]
                 #(update-in % [:child]
                             mapping-common/update-child-element [:henkilo] mapping-common/henkilo_219))
      (update-in [:child] mapping-common/update-child-element
                 [:omistajatieto :Omistaja]
                 #(update-in % [:child]
                             mapping-common/update-child-element [:yritys] mapping-common/yritys_219))
      (update-in [:child] concat [{:tag :kokoontumistilanHenkilomaara}
                                  {:tag :tilapainenRakennusKytkin} {:tag :tilapainenRakennusvoimassaPvm}])))

(def- rakennus_224
  (-> rakennus_223
      (update-in [:child] mapping-common/update-child-element
                 [:rakennuksenTiedot]
                 #(update-in % [:child] conj {:tag :rakennusluokka}))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennuksenTiedot :asuinhuoneistot]
                 #(update % :child (fn [vec] (into [huoneisto_224] (rest vec)))))))

(def- katselmus-body [{:tag :tilanneKoodi}
                    {:tag :pitoPvm}
                    {:tag :osittainen}
                    {:tag :pitaja}
                    {:tag :katselmuksenLaji}
                    {:tag :vaadittuLupaehtonaKytkin}
                    {:tag :huomautukset
                     :child [{:tag :huomautus
                              :child [{:tag :kuvaus}
                                      {:tag :maaraAika}
                                      {:tag :toteamisHetki}
                                      {:tag :toteaja}]}]}
                    {:tag :katselmuspoytakirja :child mapping-common/liite-children_211}
                    {:tag :tarkastuksenTaiKatselmuksenNimi}
                    {:tag :lasnaolijat}
                      {:tag :poikkeamat}])

(def- katselmustieto
  {:tag :katselmustieto
   :child [{:tag :Katselmus
            :child (concat
                     [{:tag :rakennustunnus :child rakennustunnus}]
                     katselmus-body)}]})

(def- katselmustieto_213
  {:tag :katselmustieto
   :child [{:tag :Katselmus
            :child (concat
                     [{:tag :katselmuksenRakennustieto :child [{:tag :KatselmuksenRakennus :child rakennustunnus_213}]}
                      {:tag :muuTunnustieto :child [{:tag :MuuTunnus :child [{:tag :tunnus :ns "yht"}
                                                                             {:tag :sovellus :ns "yht"}]}]}]
                     katselmus-body)}]})

(def- katselmustieto_215
  (update-in katselmustieto_213 [:child] mapping-common/update-child-element
    [:Katselmus :katselmuspoytakirja]
    {:tag :katselmuspoytakirja :child mapping-common/liite-children_213}))

(def- katselmustieto_216
  (-> katselmustieto_215
      (update-in [:child] mapping-common/update-child-element
                 [:Katselmus :verottajanTvLlKytkin]
                 #(update % :child concat [{:tag :verottajanTvLlKytkin}]))
      (update-in [:child] mapping-common/update-child-element
                 [:Katselmus :katselmuksenRakennustieto :KatselmuksenRakennus]
                 {:tag :KatselmuksenRakennus :child rakennustunnus_216})))

(def- katselmustieto_220
  (-> katselmustieto_216
    (update-in [:child] mapping-common/update-child-element
      [:Katselmus :katselmuksenRakennustieto :KatselmuksenRakennus]
      {:tag :KatselmuksenRakennus :child rakennustunnus_220})
    (update-in [:child] mapping-common/update-child-element
      [:Katselmus :katselmuspoytakirja]
      {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_216}]})))

(def- katselmustieto_223
      (-> katselmustieto_220
          (update-in [:child] mapping-common/update-child-element
                     [:Katselmus :liitetieto]
                     {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_219}]})))

(def- avainsanatieto_222
  {:tag :avainsanaTieto :child [{:tag :Avainsana}]})

(def- menettely-tos_222
  {:tag :menettelyTOS})

(def- avainarvotieto_222
  {:tag   :avainArvoTieto
   :child [{:tag   :AvainArvoPari
            :child [{:tag :avain :ns "yht"}
                    {:tag :arvo :ns "yht"}]}]})

(def- rakennelma_223
  [{:tag :Rakennelma :child
         [{:tag :yksilointitieto :ns "yht"}
          {:tag :alkuHetki :ns "yht"}
          (mapping-common/sijaintitieto)
          {:tag :kuvaus :child [{:tag :kuvaus}]}
          {:tag :kokoontumistilanHenkilomaara}
          {:tag :tilapainenRakennelmaKytkin}
          {:tag :tilapainenRakennelmavoimassaPvm}
          {:tag :kokonaisala}
          {:tag :kayttotarkoitus}]}])

(def rakennuslupa_to_krysp_212
  {:tag   :Rakennusvalvonta
   :ns    "rakval"
   :attr  (merge {:xsi:schemaLocation (mapping-common/schemalocation :R "2.1.2")
                  :xmlns:rakval       "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"}
                (mapping-common/common-namespaces :R "2.1.2"))
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag   :rakennusvalvontaAsiatieto
            :child [{:tag   :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [mapping-common/tilamuutos]}
                             {:tag   :luvanTunnisteTiedot
                              :child [mapping-common/lupatunnus]}
                             {:tag :viitelupatieto :child [mapping-common/lupatunnus]}
                             {:tag   :osapuolettieto
                              :child [mapping-common/osapuolet_210]}
                             {:tag   :rakennuspaikkatieto
                              :child [mapping-common/rakennuspaikka]}
                             {:tag   :toimenpidetieto
                              :child [{:tag   :Toimenpide
                                       :child [{:tag :uusi :child [{:tag :kuvaus}]}
                                               {:tag :laajennus :child [{:tag   :laajennuksentiedot
                                                                         :child [{:tag :tilavuus}
                                                                                 {:tag :kerrosala}
                                                                                 {:tag :kokonaisala}
                                                                                 {:tag   :huoneistoala
                                                                                  :child [{:tag :pintaAla :ns "yht"}
                                                                                          {:tag :kayttotarkoitusKoodi :ns "yht"}]}]}
                                                                        {:tag :kuvaus}
                                                                        {:tag :perusparannusKytkin}]}
                                               {:tag :purkaminen :child [{:tag :kuvaus}
                                                                         {:tag :purkamisenSyy}
                                                                         {:tag :poistumaPvm }]}
                                               {:tag :muuMuutosTyo :child [{:tag :muutostyonLaji}
                                                                           {:tag :kuvaus}
                                                                           {:tag :perusparannusKytkin}]}
                                               {:tag :uudelleenrakentaminen :child [{:tag :muutostyonLaji}
                                                                                    {:tag :kuvaus}
                                                                                    {:tag :perusparannusKytkin}]}
                                               {:tag :kaupunkikuvaToimenpide :child [{:tag :kuvaus}]}
                                               {:tag :rakennustieto :child [rakennus]}
                                               {:tag   :rakennelmatieto
                                                :child [{:tag :Rakennelma :child [{:tag :yksilointitieto :ns "yht"}
                                                                                  {:tag :alkuHetki :ns "yht"}
                                                                                  (mapping-common/sijaintitieto)
                                                                                  {:tag :kuvaus :child [{:tag :kuvaus}]}
                                                                                  {:tag :kokonaisala}]}]}]}]}
                             katselmustieto
                             {:tag :lausuntotieto :child [mapping-common/lausunto_211]}
                             {:tag   :lisatiedot
                              :child [{:tag   :Lisatiedot
                                       :child [{:tag :salassapitotietoKytkin}
                                               {:tag :asioimiskieli}
                                               {:tag   :vakuus
                                                :child [{:tag :vakuudenLaji}
                                                        {:tag :voimassaolopvm}
                                                        {:tag :vakuudenmaara}
                                                        {:tag :vakuuspaatospykala}]}]}]}
                             {:tag   :liitetieto
                              :child [{:tag :Liite :child mapping-common/liite-children_211}]}
                             {:tag :kayttotapaus}
                             {:tag   :asianTiedot
                              :child [{:tag   :Asiantiedot
                                       :child [{:tag :vahainenPoikkeaminen}
                                               {:tag :rakennusvalvontaasianKuvaus}]}]}]}]}]})

(def rakennuslupa_to_krysp_213
  (-> rakennuslupa_to_krysp_212
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :R "2.1.3"))
    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto]
      katselmustieto_213)
    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
      {:tag :osapuolettieto :child [mapping-common/osapuolet_211]})
    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto :Rakennus :rakennuksenTiedot]
      #(update-in % [:child] conj {:tag :liitettyJatevesijarjestelmaanKytkin}))
    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :rakennuspaikkatieto]
      {:tag :rakennuspaikkatieto :child [mapping-common/rakennuspaikka_211]})))

(def rakennuslupa_to_krysp_214
  (-> rakennuslupa_to_krysp_213
      (assoc-in [:attr :xsi:schemaLocation]
        (mapping-common/schemalocation :R "2.1.4"))
      (update-in [:child] mapping-common/update-child-element
        [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
        {:tag :osapuolettieto :child [mapping-common/osapuolet_212]})
      (update-in [:child] mapping-common/update-child-element
        [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide]
        (fn [toimenpide]
          (update toimenpide :child
            (util/fn->> (mapv
                          #(if (#{:uusi :laajennus :uudelleenrakentaminen :muuMuutosTyo} (:tag %))
                             (update % :child conj {:tag :raukeamisPvm})
                             %))))))))

(def rakennuslupa_to_krysp_215
  (-> rakennuslupa_to_krysp_214
    (assoc-in [:attr :xsi:schemaLocation]
      (mapping-common/schemalocation :R "2.1.5"))

    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
      {:tag :osapuolettieto :child [mapping-common/osapuolet_213]})

    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto :Liite]
      {:tag :Liite :child mapping-common/liite-children_213})

    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto]
      katselmustieto_215)

    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto]
      {:tag :lausuntotieto :child [mapping-common/lausunto_213]})

    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto]
      {:tag :rakennustieto :child [rakennus_215]})))

(def rakennuslupa_to_krysp_216
  (-> rakennuslupa_to_krysp_215
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :R "2.1.6"))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto]
                 katselmustieto_216)
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
                 {:tag :osapuolettieto :child [mapping-common/osapuolet_215]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto]
                 {:tag :rakennustieto :child [rakennus_216]})))

(def rakennuslupa_to_krysp_218
  (-> rakennuslupa_to_krysp_216
   (assoc-in [:attr :xsi:schemaLocation]
     (mapping-common/schemalocation :R "2.1.8"))))

(def rakennuslupa_to_krysp_220
  (-> rakennuslupa_to_krysp_218
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :R "2.2.0"))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
                 {:tag :osapuolettieto :child [mapping-common/osapuolet_216]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :laajennus :laajennuksentiedot]
                 #(update % :child mapping-common/merge-into-coll-after-tag :kerrosala
                          [{:tag :rakennusoikeudellinenKerrosala}]))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto]
                 {:tag :rakennustieto :child [rakennus_220]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennelmatieto :Rakennelma]
                 #(update-in % [:child] concat [{:tag :kayttotarkoitus}]))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto]
                 katselmustieto_220)
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto]
                 {:tag :lausuntotieto :child [mapping-common/lausunto_216]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto :Liite]
                 {:tag :Liite :child mapping-common/liite-children_216})))

(def rakennuslupa_to_krysp_222
  (-> rakennuslupa_to_krysp_220
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :R "2.2.2")
                  :xmlns:rakval "http://www.kuntatietopalvelu.fi/gml/rakennusvalvonta"}
                 (mapping-common/common-namespaces :R "2.2.2"))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
                 {:tag :osapuolettieto :child [mapping-common/osapuolet_218]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
                 #(update % :child concat [avainsanatieto_222 menettely-tos_222 avainarvotieto_222]))
      (update :child mapping-common/update-child-element
              [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :muuMuutosTyo]
              #(update % :child conj {:tag :rakennustietojaEimuutetaKytkin}))
      (update :child mapping-common/update-child-element
              [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lisatiedot :Lisatiedot]
              #(update % :child conj {:tag :asiakirjatToimitettuPvm}))
      (update :child mapping-common/update-child-element
              [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
              #(update % :child mapping-common/merge-into-coll-after-tag :lausuntotieto
                       [{:tag :paatostieto :child [mapping-common/paatokset_218]}]))))

(def rakennuslupa_to_krysp_223
  (-> rakennuslupa_to_krysp_222
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :R "2.2.3")
                  :xmlns:rakval "http://www.kuntatietopalvelu.fi/gml/rakennusvalvonta"}
                 (mapping-common/common-namespaces :R "2.2.3"))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
                 {:tag :osapuolettieto :child [mapping-common/osapuolet_219]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto]
                 {:tag :rakennustieto :child [rakennus_223]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto]
                 katselmustieto_223)
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto :Liite]
                 {:tag :Liite :child mapping-common/liite-children_219})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennelmatieto :Rakennelma]
                 #(update % :child mapping-common/merge-into-coll-after-tag :kuvaus [{:tag :kokoontumistilanHenkilomaara}
                                                                                     {:tag :tilapainenRakennelmaKytkin}
                                                                                     {:tag :tilapainenRakennelmavoimassaPvm}]))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto]
                 {:tag :lausuntotieto :child [mapping-common/lausunto_219]})
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto]
                 {:tag :paatostieto :child [mapping-common/paatokset_219]})))

(def rakennuslupa_to_krysp_224
  (-> rakennuslupa_to_krysp_223
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :R "2.2.4")
                  :xmlns:rakval "http://www.kuntatietopalvelu.fi/gml/rakennusvalvonta"}
                 (mapping-common/common-namespaces :R "2.2.4"))
      (update-in [:child] mapping-common/update-child-element
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto]
                 {:tag :rakennustieto :child [rakennus_224]})))

(defn get-rakennuslupa-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" rakennuslupa_to_krysp_212
    "2.1.3" rakennuslupa_to_krysp_213
    "2.1.4" rakennuslupa_to_krysp_214
    "2.1.5" rakennuslupa_to_krysp_215
    "2.1.6" rakennuslupa_to_krysp_216
    "2.1.8" rakennuslupa_to_krysp_218
    "2.2.0" rakennuslupa_to_krysp_220
    "2.2.2" rakennuslupa_to_krysp_222
    "2.2.3" rakennuslupa_to_krysp_223
    "2.2.4" rakennuslupa_to_krysp_224
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))


(defn- katselmus-pk? [{:keys [type-group type-id]}]
  (and (= type-group "katselmukset_ja_tarkastukset")
    (#{"katselmuksen_tai_tarkastuksen_poytakirja" "aloituskokouksen_poytakirja"} type-id)))

(defmethod permit/review-krysp-mapper :R [application organization review user lang krysp-version begin-of-link]
  (let [target-pred #(= {:type "task" :id (:id review)} (:target %))
        attachments (filter target-pred  (:attachments application))
        poytakirja  (some #(when (katselmus-pk? (:type %)) %) attachments)
        attachments-wo-pk (filter #(not= (:id %) (:id poytakirja)) attachments)
        canonical-attachments (attachments-canon/get-attachments-as-canonical
                                {:attachments attachments-wo-pk :title (:title application)}
                                organization
                                begin-of-link (every-pred target-pred attachments-canon/no-statements-no-verdicts))
        canonical-pk-liite (first (attachments-canon/get-attachments-as-canonical
                                    {:attachments [poytakirja] :title (:title application)}
                                    organization
                                    begin-of-link (every-pred target-pred attachments-canon/no-statements-no-verdicts)))
        canonical-pk (:Liite canonical-pk-liite)

        all-canonical-attachments (seq (filter identity (conj canonical-attachments canonical-pk-liite)))

        canonical-without-attachments (common/review->canonical application review {:lang lang :user user})
        canonical (-> canonical-without-attachments
                      (#(if (seq canonical-attachments)
                          (assoc-in % [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto] canonical-attachments)
                          %))
                      (#(if poytakirja
                          (-> %
                              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto :Katselmus :katselmuspoytakirja] canonical-pk)
                              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto :Katselmus :liitetieto :Liite] canonical-pk))
                          %)))

        xml (element-to-xml canonical (get-rakennuslupa-mapping krysp-version))

        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]
    {:xml xml
     :attachments attachments-for-write}))

(defn building-extinction-as-krysp [application lang krysp-version]
  (let [canonical (canonical/building-extinction-canonical application lang)]
    {:xml (element-to-xml canonical (get-rakennuslupa-mapping krysp-version))}))

(defn save-unsent-attachments-as-krysp
  [application organization lang krysp-version begin-of-link]
  (let [canonical-without-attachments (canonical/unsent-attachments-to-canonical application lang)

        attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        canonical (assoc-in canonical-without-attachments
                    [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                    attachments-canonical)

        xml (element-to-xml canonical (get-rakennuslupa-mapping krysp-version))
        attachments-for-write (mapping-common/attachment-details-from-canonical attachments-canonical)]
    {:xml xml
     :attachments attachments-for-write}))

(defn- patevyysvaatimusluokka212 [luokka]
  (if (and luokka (not (#{"AA" "ei tiedossa"} luokka)))
    "ei tiedossa" ; values that are not supported in 2.1.2 will be converted to "ei tiedossa"
    luokka))

(defn- map-tyonjohtaja-patevyysvaatimusluokka [canonical]
  (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :tyonjohtajatieto]
    #(map (fn [tj]
            (-> tj
              (update-in [:Tyonjohtaja :patevyysvaatimusluokka] patevyysvaatimusluokka212)
              (update-in [:Tyonjohtaja :vaadittuPatevyysluokka] patevyysvaatimusluokka212)))
       %)))

(def designer-roles-mapping-new-to-old-222 {"muu" "ei tiedossa"})

(def designer-roles-mapping-new-to-old-220 (merge designer-roles-mapping-new-to-old-222
                                                  {"rakennussuunnittelija"                          "ARK-rakennussuunnittelija"
                                                   "kantavien rakenteiden suunnittelija"            "RAK-rakennesuunnittelija"
                                                   "pohjarakenteiden suunnittelija"                 "GEO-suunnittelija"
                                                   "ilmanvaihdon suunnittelija"                     "IV-suunnittelija"
                                                   "kiinteist\u00f6n vesi- ja viem\u00e4r\u00f6intilaitteiston suunnittelija"  "KVV-suunnittelija"
                                                   "rakennusfysikaalinen suunnittelija"             "ei tiedossa"
                                                   "kosteusvaurion korjausty\u00f6n suunnittelija"  "ei tiedossa"}))

(defn map-suunnittelija-kuntaroolikoodi [mapping canonical]
  (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :suunnittelijatieto]
    #(map (fn [suunnittelija]
            (update-in suunnittelija [:Suunnittelija :suunnittelijaRoolikoodi]
              (fn [role] (or (mapping role) role))))
       %)))

(def hakijan-asiamies-mapping-new-to-old-220 {"Hakijan asiamies"  "ei tiedossa"})

(defn map-hakijan-asiamies-pre220 [canonical]
  (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :osapuolitieto]
    #(map (fn [osapuoli]
            (update-in osapuoli [:Osapuoli :kuntaRooliKoodi]
              (fn [role] (or (hakijan-asiamies-mapping-new-to-old-220 role) role)))) %)))

(def rakennelman-kayttotarkoitus-pre-222 {"Aurinkopaneeli" "Muu rakennelma"
                                          "Varastointis\u00e4ili\u00f6" "Muu rakennelma"})

(defn map-rakennelman-kayttotarkoitus-pre-222 [canonical]
  (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto]
             (partial map (fn [{{{{kayttotarkoitus :kayttotarkoitus} :Rakennelma} :rakennelmatieto} :Toimenpide :as toimenpide}]
                            (if kayttotarkoitus
                              (assoc-in toimenpide [:Toimenpide :rakennelmatieto :Rakennelma :kayttotarkoitus]
                                        (get rakennelman-kayttotarkoitus-pre-222 kayttotarkoitus kayttotarkoitus))
                              toimenpide)))))

(def omistaja-kuntaroolikoodi-pre223 {"Rakennuksen laajarunkoisen osan arvioija" "Rakennuksen omistaja"})

(defn- get-old-values-for-canonical
  "Can be used when last keyword value for first-path is a seq and inner-path does not contain a seq"
  [first-path inner-path mapping-of-values canonical]
  (if (get-in canonical first-path)
    (update-in canonical first-path
               #(map (partial mapping-common/old-values-from-mapping inner-path mapping-of-values) %))
    canonical))

(defn- map-paloluokka-pre-223
  "Every :paloluokka with P0 value is removed from canonical if krysp version is going to be < 2.2.3"
  [canonical]
  (let [path-to-toimenpidetieto [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto]
        inner-path-to-rakennukseTiedot [:Toimenpide :rakennustieto :Rakennus :rakennuksenTiedot]]
    (if (get-in canonical path-to-toimenpidetieto)
      (update-in canonical path-to-toimenpidetieto #(map
                                                      (fn [canonical]
                                                        (if (= "P0" (get-in canonical (conj inner-path-to-rakennukseTiedot :paloluokka)))
                                                          (update-in canonical inner-path-to-rakennukseTiedot dissoc :paloluokka)
                                                          canonical)) %))
      canonical)))

;Can be removed if kuntaRooliKoodi for Omistaja can't be anything else than "Rakennuksen omistaja"
(defn- map-omistaja-kuntaroolikoodi-pre223
  "Values of :toimenpidetieto and :omistajatieto are sequences"
  [canonical]
  (let [path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto]]
    (if (get-in canonical path)
      (update-in canonical path
                 #(map (partial get-old-values-for-canonical
                                [:Toimenpide :rakennustieto :Rakennus :omistajatieto]
                                [:Omistaja :kuntaRooliKoodi]
                                omistaja-kuntaroolikoodi-pre223) %))
      canonical)))

(def map-enums-222 (comp map-paloluokka-pre-223
                         map-omistaja-kuntaroolikoodi-pre223))

(def map-enums-220 (comp map-enums-222
                         (partial map-suunnittelija-kuntaroolikoodi designer-roles-mapping-new-to-old-222)
                         map-rakennelman-kayttotarkoitus-pre-222))

(def map-enums-213-218 (comp map-enums-220
                             (partial map-suunnittelija-kuntaroolikoodi designer-roles-mapping-new-to-old-220)
                             map-hakijan-asiamies-pre220))

(def map-enums-212 (comp map-enums-213-218
                         map-tyonjohtaja-patevyysvaatimusluokka))

(defn- common-map-enums [canonical krysp-version]
  (let [path-to-RakennusvalvontaAsia  [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
        path-to-lausuntotieto         (conj path-to-RakennusvalvontaAsia :lausuntotieto)
        path-to-rakennuspaikkatieto   (conj path-to-RakennusvalvontaAsia :rakennuspaikkatieto)
        path-to-osapuolitieto         (conj path-to-RakennusvalvontaAsia :osapuolettieto :Osapuolet :osapuolitieto)]
    (cond-> canonical
            (get-in canonical path-to-lausuntotieto) (update-in path-to-lausuntotieto mapping-common/lausuntotieto-map-enum :R krysp-version)
            (get-in canonical path-to-rakennuspaikkatieto) (update-in path-to-rakennuspaikkatieto mapping-common/rakennuspaikkatieto-map-enum :R krysp-version)
            (get-in canonical path-to-osapuolitieto) (update-in path-to-osapuolitieto mapping-common/osapuolitieto-map-enum :R krysp-version))))

(defn- map-enums
  "Map enumerations in canonical into values supported by given KRYSP version"
  [canonical krysp-version]
  {:pre [krysp-version]}
  (-> (case (name krysp-version)
        "2.1.2" (map-enums-212 canonical)
        "2.1.3" (map-enums-213-218 canonical)
        "2.1.4" (map-enums-213-218 canonical)
        "2.1.5" (map-enums-213-218 canonical)
        "2.1.6" (map-enums-213-218 canonical)
        "2.1.7" (map-enums-213-218 canonical)
        "2.1.8" (map-enums-213-218 canonical)
        "2.2.0" (map-enums-220 canonical)
        "2.2.2" (map-enums-222 canonical)
        canonical)
      (common-map-enums krysp-version)))

(defn rakennuslupa-element-to-xml [canonical krysp-version]
  (element-to-xml (map-enums canonical krysp-version) (get-rakennuslupa-mapping krysp-version)))

(defn add-paattymispvm-in-foreman-application [canonical-application foreman-application]
  (if-let [date (-> foreman-application :foremanTermination :ended)]
    (->> (date/xml-date date)
         (mapping-common/assoc-canonical-foreman-field canonical-application :paattymisPvm))
    canonical-application))

(defn save-aloitusilmoitus-as-krysp
  [{:keys [application organization lang user] :as command} notice-form krysp-version begin-of-link]
  (let [target-pred #(= {:type "notice-form" :id (:id notice-form)} (:target %))
        attachments (filter target-pred  (:attachments application))
        canonical-attachments (attachments-canon/get-attachments-as-canonical
                                {:attachments   attachments
                                 :title         (:title application)
                                 :organization  (:organization application)}
                                organization
                                begin-of-link (every-pred target-pred attachments-canon/no-statements-no-verdicts))
        canonical (cond-> (canonical/aloitusilmoitus-canonical command notice-form)
                          (seq canonical-attachments)
                          (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto
                                     :RakennusvalvontaAsia :liitetieto] canonical-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical canonical-attachments)]
    {:xml (rakennuslupa-element-to-xml canonical krysp-version)
     :attachments attachments-for-write}))

(defn rh-tietojen-muutos-kuntagml
  "Regular R mapping with 'RH-tietojen muutos' and without attachments."
  [{:keys [lang application]} krysp-version]
  (let [canonical (-> (meta-fields/enrich-with-link-permit-data application)
                      (common/application->canonical (name lang))
                      (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :kayttotapaus]
                                "RH-tietojen muutos"))]
    {:xml (rakennuslupa-element-to-xml canonical krysp-version)}))

(defn check-operations!
  "Throws if `application` has `aiemmalla-luvalla-hakeminen` without proper operations"
  [application lang]
  (doseq [doc   (some-> application :documents tools/unwrapped)
          :when (some-> doc :schema-info :name (util/=as-kw :aiemman-luvan-toimenpide))]
    (when-not (canonical/get-aiemman-luvan-toimenpide doc application)
      (throw (ex-info (i18n/localize lang :kuntagml-toimenpide.error.type) {})))))

(defn- get-fresh-attachment [application filter-fn]
  (->> (domain/get-application-no-access-checking (:id application) [:attachments])
       :attachments
       (util/find-first filter-fn)))

(def link-placeholder->filter-fn
  "A map for replacing attachment placeholder links with the actual attachment, using the first attachment
  that matches the filter-fn"
  {:ENCUMBRANCE_DESCRIPTION_LINK_PLACEHOLDER (fn [{:keys [latestVersion type]}]
                                               (and (-> type :type-id (= "kuvaus"))
                                                    (-> type :type-group (= "muut"))
                                                    (some-> latestVersion :user :id (= "batchrun-user"))))})

(defn add-non-attachment-links
  "Ugly solution since rasite-tai-yhteisjarjestely has a mandatory link outside the attachemnt list
  to an attachment which breaks the otherwise beautiful separation of the canonical model and the attachments list"
  [canonical application begin-of-link]
  (let [replace (fn [canonical placeholder]
                  (walk/postwalk-replace
                    {placeholder (->> placeholder
                                      (link-placeholder->filter-fn)
                                      (get-fresh-attachment application)
                                      (attachments-canon/get-attachment-link begin-of-link))}
                    canonical))]
    (cond-> canonical
      (->> application :documents (some #(-> % :schema-info :name (= "rasite-tai-yhteisjarjestely"))))
      (replace :ENCUMBRANCE_DESCRIPTION_LINK_PLACEHOLDER))))

(defmethod permit/application-krysp-mapper :R
  [application organization lang krysp-version begin-of-link]
  (check-operations! application lang)
  (let [canonical-without-attachments  (common/application->canonical application lang)
        canonical-without-attachments (if-not (org/pate-scope? application)
                                        (util/dissoc-in canonical-without-attachments
                                                        [:Rakennusvalvonta :rakennusvalvontaAsiatieto
                                                         :RakennusvalvontaAsia :luvanTunnisteTiedot
                                                         :LupaTunnus :VRKLupatunnus])
                                        canonical-without-attachments)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments
                                      [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto]))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application organization begin-of-link statement-given-ids)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        canonical-with-statement-attachments  (attachments-canon/add-statement-attachments
                                                canonical-without-attachments
                                                statement-attachments
                                                [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto])
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (-> canonical
                (add-paattymispvm-in-foreman-application application)
                (add-non-attachment-links application begin-of-link)
                (rakennuslupa-element-to-xml krysp-version))
        all-canonical-attachments (concat attachments-canonical (attachments-canon/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]
    {:xml xml
     :attachments attachments-for-write}))
