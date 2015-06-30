(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:require [taoensso.timbre :as timbre :refer [debug]]
            [clojure.java.io :as io]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.tools :as tools]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.document.rakennuslupa_canonical :refer [application-to-canonical
                                                                 katselmus-canonical
                                                                 unsent-attachments-to-canonical]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.pdf-export :as pdf-export]
            [lupapalvelu.xml.disk-writer :as writer]))

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


(def- rakennustunnus
  [{:tag :valtakunnallinenNumero}
   {:tag :jarjestysnumero}
   {:tag :kiinttun}
   {:tag :rakennusnro}])

(def- rakennustunnus_213
  (conj rakennustunnus
    {:tag :katselmusOsittainen}
    {:tag :kayttoonottoKytkin}))

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
                    {:tag :asuinhuoneistot :child [huoneisto]}]}
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

(def- katselmustieto
  {:tag :katselmustieto
   :child [{:tag :Katselmus
            :child [{:tag :rakennustunnus :child rakennustunnus}
                    {:tag :tilanneKoodi}
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
                    {:tag :poikkeamat}]}]})

(def rakennuslupa_to_krysp_212
  {:tag :Rakennusvalvonta
   :ns "rakval"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :R "2.1.2")
                 :xmlns:rakval "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"}
           mapping-common/common-namespaces)
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :rakennusvalvontaAsiatieto
            :child [{:tag :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [mapping-common/tilamuutos]}
                             {:tag :luvanTunnisteTiedot
                              :child [mapping-common/lupatunnus]}
                             {:tag :viitelupatieto :child [mapping-common/lupatunnus]}
                             {:tag :osapuolettieto
                              :child [mapping-common/osapuolet]}
                             {:tag :rakennuspaikkatieto
                              :child [mapping-common/rakennuspaikka]}
                             {:tag :toimenpidetieto
                              :child [{:tag :Toimenpide
                                       :child [{:tag :uusi :child [{:tag :kuvaus}]}
                                               {:tag :laajennus :child [{:tag :laajennuksentiedot
                                                                         :child [{:tag :tilavuus}
                                                                                 {:tag :kerrosala}
                                                                                 {:tag :kokonaisala}
                                                                                 {:tag :huoneistoala
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
                                               {:tag :kaupunkikuvaToimenpide :child [{:tag :kuvaus}]}
                                               {:tag :rakennustieto :child [rakennus]}
                                               {:tag :rakennelmatieto
                                                :child [{:tag :Rakennelma :child [{:tag :yksilointitieto :ns "yht"}
                                                                                  {:tag :alkuHetki :ns "yht"}
                                                                                  (mapping-common/sijaintitieto)
                                                                                  {:tag :kuvaus :child [{:tag :kuvaus}]}
                                                                                  {:tag :kokonaisala}]}]}]}]}
                             katselmustieto
                             {:tag :lausuntotieto :child [mapping-common/lausunto_211]}
                             {:tag :lisatiedot
                              :child [{:tag :Lisatiedot
                                       :child [{:tag :salassapitotietoKytkin}
                                      {:tag :asioimiskieli}
                                      {:tag :vakuus
                                       :child [{:tag :vakuudenLaji}
                                               {:tag :voimassaolopvm}
                                               {:tag :vakuudenmaara}
                                               {:tag :vakuuspaatospykala}]}]}]}
                             {:tag :liitetieto
                              :child [{:tag :Liite :child mapping-common/liite-children_211}]}
                             {:tag :kayttotapaus}
                             {:tag :asianTiedot
                              :child [{:tag :Asiantiedot
                                       :child [{:tag :vahainenPoikkeaminen}
                                                {:tag :rakennusvalvontaasianKuvaus}]}]}]}]}]})

(def- katselmus_213
  {:tag :katselmustieto
   :child [{:tag :Katselmus
            :child [{:tag :katselmuksenRakennustieto :child [{:tag :KatselmuksenRakennus :child rakennustunnus_213}]}
                    {:tag :muuTunnustieto :child [{:tag :MuuTunnus :child [{:tag :tunnus :ns "yht"} {:tag :sovellus :ns "yht"}]}]}
                    {:tag :tilanneKoodi}
                    {:tag :pitoPvm}
                    {:tag :osittainen}
                    {:tag :pitaja}
                    {:tag :katselmuksenLaji}
                    {:tag :vaadittuLupaehtonaKytkin}
                    {:tag :huomautukset :child [{:tag :huomautus :child [{:tag :kuvaus}
                                                                         {:tag :maaraAika}
                                                                         {:tag :toteamisHetki}
                                                                         {:tag :toteaja}]}]}
                    {:tag :katselmuspoytakirja :child mapping-common/liite-children_211}
                    {:tag :tarkastuksenTaiKatselmuksenNimi}
                    {:tag :lasnaolijat}
                    {:tag :poikkeamat}]}]})

(def- katselmus_215
  (update-in katselmus_213 [:child] mapping-common/update-child-element
      [:Katselmus :katselmuspoytakirja]
      {:tag :katselmuspoytakirja :child mapping-common/liite-children_213}))

(def rakennuslupa_to_krysp_213
  (-> rakennuslupa_to_krysp_212
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :R "2.1.3"))
    (update-in [:child] mapping-common/update-child-element
      [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto]
      katselmus_213)
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
      {:tag :osapuolettieto :child [mapping-common/osapuolet_212]})))

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
      katselmus_215)

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
                 [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto]
                 {:tag :osapuolettieto :child [mapping-common/osapuolet_215]})))

(def rakennuslupa_to_krysp_218
  (-> rakennuslupa_to_krysp_216
   (assoc-in [:attr :xsi:schemaLocation]
     (mapping-common/schemalocation :R "2.1.8"))))

(defn get-rakennuslupa-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" rakennuslupa_to_krysp_212
    "2.1.3" rakennuslupa_to_krysp_213
    "2.1.4" rakennuslupa_to_krysp_214
    "2.1.5" rakennuslupa_to_krysp_215
    "2.1.6" rakennuslupa_to_krysp_216
    "2.1.8" rakennuslupa_to_krysp_218
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- save-katselmus-xml [application
                           lang
                           output-dir
                           task-id
                           task-name
                           started
                           buildings
                           user
                           katselmuksen-nimi
                           tyyppi
                           osittainen
                           pitaja
                           lupaehtona
                           huomautukset
                           lasnaolijat
                           poikkeamat
                           krysp-version
                           begin-of-link
                           attachment-target]
  (let [attachments (filter #(= attachment-target (:target %)) (:attachments application))
        poytakirja  (some #(when (=  {:type-group "muut", :type-id "katselmuksen_tai_tarkastuksen_poytakirja"} (:type %) ) %) attachments)
        attachments-wo-pk (filter #(not= (:id %) (:id poytakirja)) attachments)
        canonical-attachments (when attachment-target (mapping-common/get-attachments-as-canonical
                                                        {:attachments attachments-wo-pk :title (:title application)}
                                                        begin-of-link attachment-target))
        canonical-pk-liite (first (mapping-common/get-attachments-as-canonical
                                     {:attachments [poytakirja] :title (:title application)}
                                     begin-of-link attachment-target))
        canonical-pk (:Liite canonical-pk-liite)

        all-canonical-attachments (seq (filter identity (conj canonical-attachments canonical-pk-liite)))

        canonical-without-attachments (katselmus-canonical application lang task-id task-name started buildings user
                                                           katselmuksen-nimi tyyppi osittainen pitaja lupaehtona
                                                           huomautukset lasnaolijat poikkeamat)
        canonical (-> canonical-without-attachments
                    (#(if (seq canonical-attachments)
                      (assoc-in % [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto] canonical-attachments)
                      %))
                    (#(if poytakirja
                       (assoc-in % [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto :Katselmus :katselmuspoytakirja] canonical-pk)
                       %)))

        xml (element-to-xml canonical (get-rakennuslupa-mapping krysp-version))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]

    (writer/write-to-disk application attachments-for-write xml krysp-version output-dir)))

(defn save-katselmus-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application katselmus user lang krysp-version output-dir begin-of-link]
  (let [find-national-id (fn [{:keys [kiinttun rakennusnro]}]
                           (:nationalId (some #(when (and (= (:propertyId %)) (= rakennusnro (:localShortId %))) %) (:buildings application))))
        data (tools/unwrapped (:data katselmus))
        {:keys [katselmuksenLaji vaadittuLupaehtona rakennus]} data
        {:keys [pitoPvm pitaja lasnaolijat poikkeamat tila]} (:katselmus data)
        huomautukset (-> data :katselmus :huomautukset)
        buildings    (map
                       (fn [{rakennus :rakennus :as b}]
                         (if (ss/blank? (:valtakunnallinenNumero rakennus))
                           (assoc-in b [:rakennus :valtakunnallinenNumero] (find-national-id rakennus))
                           b))
                       (vals rakennus))]
    (save-katselmus-xml
      application
      lang
      output-dir
      (:id katselmus)
      (:taskname katselmus)
      pitoPvm
      buildings
      user
      katselmuksenLaji
      :katselmus
      tila
      pitaja
      vaadittuLupaehtona
      huomautukset
      lasnaolijat
      poikkeamat
      krysp-version
      begin-of-link
      {:type "task" :id (:id katselmus)})))

(permit/register-function permit/R :review-krysp-mapper save-katselmus-as-krysp)

(defn save-aloitusilmoitus-as-krysp [application lang output-dir started {:keys [index localShortId nationalId propertyId] :as building} user krysp-version]
  (let [building-id {:rakennus {:jarjestysnumero index
                                :kiinttun        propertyId
                                :rakennusnro     localShortId
                                :valtakunnallinenNumero nationalId}}]
    (save-katselmus-xml application lang output-dir nil "Aloitusilmoitus" started [building-id] user "Aloitusilmoitus" :katselmus nil nil nil nil nil nil krysp-version nil nil)))

(defn save-unsent-attachments-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments (unsent-attachments-to-canonical application lang)

        attachments-canonical (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical (assoc-in canonical-without-attachments
                    [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                    attachments-canonical)

        xml (element-to-xml canonical (get-rakennuslupa-mapping krysp-version))
        attachments-for-write (mapping-common/attachment-details-from-canonical attachments-canonical)]

    (writer/write-to-disk application attachments-for-write xml krysp-version output-dir)))

(defn- map-tyonjohtaja-patevyysvaatimusluokka [canonical]
  (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :tyonjohtajatieto]
    #(map (fn [tj]
            (update-in tj [:Tyonjohtaja :patevyysvaatimusluokka]
              (fn [luokka]
                (if (and luokka (not (#{"AA" "ei tiedossa"} luokka)))
                  "ei tiedossa" ; values that are not supported in 2.1.2 will be converted to "ei tiedossa"
                  luokka))))
       %)))

(defn- map-enums-212 [canonical]
  (map-tyonjohtaja-patevyysvaatimusluokka canonical))

(defn- map-enums
  "Map enumerations in canonical into values supperted by given KRYSP version"
  [canonical krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" (map-enums-212 canonical)
    canonical ; default: no conversions
    ))

(defn- rakennuslupa-element-to-xml [canonical krysp-version]
  (element-to-xml (map-enums canonical krysp-version) (get-rakennuslupa-mapping krysp-version)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments  (application-to-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments
                                [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto]))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments-canonical (mapping-common/get-attachments-as-canonical application begin-of-link)
        attachments-with-generated-pdfs (conj attachments-canonical
                                          {:Liite
                                           {:kuvaus "Vireille tullut hakemus"
                                            :linkkiliitteeseen (str begin-of-link (writer/get-submitted-filename (:id application)))
                                            :muokkausHetki (util/to-xml-datetime (:submitted application))
                                            :versionumero 1
                                            :tyyppi "hakemus_vireilletullessa"}}
                                          {:Liite
                                           {:kuvaus "K\u00e4sittelyj\u00e4rjestelm\u00e4\u00e4n siirrett\u00e4ess\u00e4"
                                            :linkkiliitteeseen (str begin-of-link (writer/get-current-filename (:id application)))
                                            :muokkausHetki (util/to-xml-datetime (now))
                                            :versionumero 1
                                            :tyyppi "hakemus_taustajarjestelmaan_siirrettaessa"}})
        canonical-with-statement-attachments  (mapping-common/add-statement-attachments
                                                canonical-without-attachments
                                                statement-attachments
                                                [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto])
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                    attachments-with-generated-pdfs)
        xml (rakennuslupa-element-to-xml canonical krysp-version)
        all-canonical-attachments (concat attachments-canonical (mapping-common/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]

    (writer/write-to-disk
      application
      attachments-for-write
      xml
      krysp-version
      output-dir
      submitted-application
      lang)))

(permit/register-function permit/R :app-krysp-mapper save-application-as-krysp)
