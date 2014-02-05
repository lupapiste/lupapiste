(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:require [taoensso.timbre :as timbre :refer [debug]]
            [clojure.java.io :as io]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.tools :as tools]
            [sade.util :refer :all]
            [lupapalvelu.document.rakennuslupa_canonical :refer [application-to-canonical
                                                                 katselmus-canonical
                                                                 unsent-attachments-to-canonical]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.ke6666 :as ke6666]))

;RakVal

(def ^:private huoneisto {:tag :huoneisto
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


(def ^:private rakennustunnus
  [{:tag :jarjestysnumero}
   {:tag :kiinttun}
   {:tag :rakennusnro}])

(def ^:private rakennustunnus_213
  (conj rakennustunnus
    {:tag :katselmusOsittainen}
    {:tag :kayttoonottoKytkin}))

(def ^:private yht-rakennus
  [{:tag :yksilointitieto :ns "yht"}
   {:tag :alkuHetki :ns "yht"}
   mapping-common/sijantitieto
   {:tag :rakennuksenTiedot
    :child [{:tag :rakennustunnus :child rakennustunnus}
            {:tag :kayttotarkoitus}
            {:tag :tilavuus}
            {:tag :kokonaisala}
            {:tag :kellarinpinta-ala}
            {:tag :BIM :child []}
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
                 {:tag :asuinhuoneistot :child [huoneisto]}
                 ]}
   {:tag :rakentajatyyppi}
   {:tag :omistajatieto
    :child [{:tag :Omistaja
             :child [{:tag :kuntaRooliKoodi :ns "yht"}
                     {:tag :VRKrooliKoodi :ns "yht"}
                     mapping-common/henkilo
                     mapping-common/yritys
                     {:tag :omistajalaji :ns "rakval"
                      :child [{:tag :muu}
                              {:tag :omistajalaji}]}]}]}])

(def ^:private rakennus {:tag :Rakennus :child yht-rakennus})

(def ^:private katselmustieto
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
                    {:tag :katselmuspoytakirja :child mapping-common/liite-children}
                    {:tag :tarkastuksenTaiKatselmuksenNimi}
                    {:tag :lasnaolijat}
                    {:tag :poikkeamat}]}]})

(def rakennuslupa_to_krysp_212
  {:tag :Rakennusvalvonta
   :ns "rakval"
   :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta
                               http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.1.2/rakennusvalvonta.xsd"
          :xmlns:rakval "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:xlink "http://www.w3.org/1999/xlink"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
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
                                               {:tag :rakennustieto
                                                :child [rakennus]}
                                               {:tag :rakennelmatieto
                                                :child [{:tag :Rakennelma :child [{:tag :yksilointitieto :ns "yht"}
                                                                                  {:tag :alkuHetki :ns "yht"}
                                                                                  mapping-common/sijantitieto
                                                                                  {:tag :kuvaus :child [{:tag :kuvaus}]}
                                                                                  {:tag :kokonaisala}]}]}]}]}
                             katselmustieto
                             {:tag :lausuntotieto :child [mapping-common/lausunto]}
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
                              :child [{:tag :Liite
                                       :child [{:tag :kuvaus :ns "yht"}
                                               {:tag :linkkiliitteeseen :ns "yht"}
                                               {:tag :muokkausHetki :ns "yht"}
                                               {:tag :versionumero :ns "yht"}
                                               {:tag :tekija :ns "yht"
                                                :child [{:tag :kuntaRooliKoodi}
                                                        {:tag :VRKrooliKoodi}
                                                        mapping-common/henkilo
                                                        mapping-common/yritys]}
                                               {:tag :tyyppi :ns "yht"}]}]}
                             {:tag :kayttotapaus}
                             {:tag :asianTiedot
                              :child [{:tag :Asiantiedot
                                       :child [{:tag :vahainenPoikkeaminen}
                                                {:tag :rakennusvalvontaasianKuvaus}]}]}]}]}]})

(def ^:private katselmus_213
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
                    {:tag :katselmuspoytakirja :child mapping-common/liite-children}
                    {:tag :tarkastuksenTaiKatselmuksenNimi}
                    {:tag :lasnaolijat}
                    {:tag :poikkeamat}]}]})

(def rakennuslupa_to_krysp_213
  (->
    (assoc-in rakennuslupa_to_krysp_212 [:attr :xsi:schemaLocation]
      "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.1/yhteiset.xsd
       http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.1.3/rakennusvalvonta.xsd")

    (update-in [:child] mapping-common/update-child-element [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto] katselmus_213)))


(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" rakennuslupa_to_krysp_212
    "2.1.3" rakennuslupa_to_krysp_213
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- write-application-pdf-versions [output-dir application submitted-application lang]
  (let [id (:id application)
        submitted-file (io/file (str output-dir "/" (mapping-common/get-submitted-filename id)))
        current-file (io/file (str output-dir "/" (mapping-common/get-current-filename id)))]
    (ke6666/generate submitted-application lang submitted-file)
    (ke6666/generate application lang current-file)))

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

        xml (element-to-xml canonical (get-mapping krysp-version))]

    (mapping-common/write-to-disk application all-canonical-attachments nil xml krysp-version output-dir)))

(defn save-katselmus-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application katselmus user lang krysp-version output-dir begin-of-link]
  (let [data (tools/unwrapped (:data katselmus))
        {:keys [katselmuksenLaji vaadittuLupaehtona]} data
        {:keys [pitoPvm pitaja lasnaolijat poikkeamat tila]} (:katselmus data)
        huomautukset (-> data :katselmus :huomautukset)
        buildings    (-> data :rakennus vals)]
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

(defn save-aloitusilmoitus-as-krysp [application lang output-dir started {:keys [index buildingId propertyId] :as building} user krysp-version]
  (let [building-id {:rakennus {:jarjestysnumero index
                                :kiinttun        propertyId
                                :rakennusnro     buildingId}}]
    (save-katselmus-xml application lang output-dir nil "Aloitusilmoitus" started [building-id] user "Aloitusilmoitus" :katselmus nil nil nil nil nil nil krysp-version nil nil))
  )

(defn save-unsent-attachments-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments (unsent-attachments-to-canonical application lang)

        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical (assoc-in canonical-without-attachments
                    [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                    attachments)

        xml (element-to-xml canonical (get-mapping krysp-version))]

    (mapping-common/write-to-disk application attachments nil xml krysp-version output-dir)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments  (application-to-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments
                                [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto]))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        attachments-with-generated-pdfs (conj attachments
                                          {:Liite
                                           {:kuvaus "Application when submitted"
                                            :linkkiliitteeseen (str begin-of-link (mapping-common/get-submitted-filename (:id application)))
                                            :muokkausHetki (to-xml-datetime (:submitted application))
                                            :versionumero 1
                                            :tyyppi "hakemus_vireilletullessa"}}
                                          {:Liite
                                           {:kuvaus "Application when sent from Lupapiste"
                                            :linkkiliitteeseen (str begin-of-link (mapping-common/get-current-filename (:id application)))
                                            :muokkausHetki (to-xml-datetime (now))
                                            :versionumero 1
                                            :tyyppi "hakemus_taustajarjestelmaan_siirrettaessa"}})
        canonical-with-statement-attachments  (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                    attachments-with-generated-pdfs)
        xml (element-to-xml canonical (get-mapping krysp-version))]

    (mapping-common/write-to-disk
      application attachments
      statement-attachments
      xml
      krysp-version
      output-dir
      #(write-application-pdf-versions output-dir application submitted-application lang))))

(permit/register-function permit/R :app-krysp-mapper save-application-as-krysp)
