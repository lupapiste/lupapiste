(ns lupapalvelu.xml.krysp.maankayton-muutos-mapping
  (:require [clojure.string :as str]
            [lupapalvelu.document.maankayton-muutos-canonical :as maankayton-muutos-canonical]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]))


(defn ->mapping [muutos]
  (let [osoite [:valtioSuomeksi :valtioKansainvalinen {:osoitenimi :teksti} :ulkomainenLahiosoite
                :postinumero :postitoimipaikannimi :ulkomainenPostitoimipaikka]]
    {:tag :Maankaytonmuutos :ns "mkmu"
     :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MM "1.0.1")
                   :xmlns:mkmu "http://www.paikkatietopalvelu.fi/gml/maankaytonmuutos"}
                  mapping-common/common-namespaces)
     :child [(mapping-common/mapper
               {:maankayttomuutosTieto
                {muutos [{:toimituksenTiedottieto
                          {:ToimituksenTiedot/yht [:aineistonnimi :aineistotoimittaja :tila :toimitusPvm :kuntakoodi :kielitieto]}}
                         {:hakemustieto [{:Hakemus [{:osapuolitieto
                                                     {:Osapuoli
                                                      [:roolikoodi :turvakieltokytkin :asioimiskieli
                                                       {:henkilotieto
                                                        {:Henkilo/yht [{:nimi [:etunimi :sukunimi]}
                                                                       {:osoite osoite}
                                                                       :sahkopostiosoite
                                                                       :faksinumero
                                                                       :puhelin
                                                                       :henkilotunnus]}}
                                                       {:yritystieto
                                                        {:Yritys/yht [:nimi :liikeJaYhteisotunnus
                                                                      {:postiosoitetieto {:postiosoite osoite}}
                                                                      :puhelin
                                                                      :sahkopostiosoite
                                                                      {:verkkolaskutustieto
                                                                       {:Verkkolaskutus [:ovtTunnus :verkkolaskuTunnus :valittajaTunnus]}}]}}
                                                       :vainsahkoinenAsiointiKytkin]}}
                                                    {:sijaintitieto {:Sijainti/yht [{:osoite [:yksilointitieto :alkuHetki {:osoitenimi :teksti}]}
                                                                                    {:piste/gml {:Point :pos}}]}}
                                                    :kohdekiinteisto :maaraAla {:tilatieto {:Tila [:pvm :kasittelija :hakemuksenTila]}}]}]}
                         :toimituksenTila {:liitetieto {:Liite/yht [:kuvaus :linkkiliitteeseen :muokkausHetki :versionumero]}}
                         :uusiKytkin
                         :kuvaus]}})]}))


(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of
  attachment file IDs that ware sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments  (maankayton-muutos-canonical/maankayton-muutos-canonical application lang)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application begin-of-link)
        muutos (-> canonical-without-attachments :Maankaytonmuutos :maankayttomuutosTieto first key)
        canonical (assoc-in
                    canonical-without-attachments
                    [:Maankaytonmuutos :maankayttomuutosTieto muutos :liitetieto]
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        mapping (->mapping muutos)
        xml (emit/element-to-xml canonical mapping)
        attachments-for-write (mapping-common/attachment-details-from-canonical attachments-canonical)]

    (writer/write-to-disk
      application
      attachments-for-write
      xml
      krysp-version
      output-dir
      submitted-application
      lang)))

(defmethod permit/application-krysp-mapper :MM [application lang submitted-application krysp-version output-dir begin-of-link]
  (save-application-as-krysp application lang submitted-application krysp-version output-dir begin-of-link))
