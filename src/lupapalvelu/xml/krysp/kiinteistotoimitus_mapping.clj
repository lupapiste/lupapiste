(ns lupapalvelu.xml.krysp.kiinteistotoimitus-mapping
  (:require [clojure.walk :as walk]
            [lupapalvelu.document.kiinteistotoimitus-canonical :refer [kiinteistotoimitus-canonical] ]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]))

(defn ->mapping []
  (let [osoite [{:osoitenimi :teksti} :postinumero :postitoimipaikannimi]
        toimitus [{:toimituksenTiedottieto
                   {:ToimituksenTiedot/yht [:aineistonnimi :aineistotoimittaja :tila :toimitusPvm :kuntakoodi
                                            :kielitieto]}}
                  {:toimitushakemustieto
                   [{:Toimitushakemus
                     [{:osapuolitieto
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
                      {:liitetieto {:Liite/yht [:kuvaus :linkkiliitteeseen :muokkausHetki :versionumero]}}
                      {:kiinteistotieto {:Kiinteisto :kiinteistotunnus}}
                      {:maaraAlatieto {:MaaraAla :maaraAlatunnus}}
                      {:tilatieto {:Tila [:pvm :kasittelija :hakemuksenTila]}}]}]}
                  :toimituksenTila
                  {:kiinteistotieto {:Kiinteisto :kiinteistotunnus}}
                  {:maaraAlatieto {:MaaraAla :maaraAlatunnus}}]
        basic (conj toimitus :kuvaus)
        toimitus-types {:featureMembers/kiito [{:Lohkominen (concat toimitus [:lohkomisenTyyppi :kuvaus])}
                         {:YhtAlueenOsuuksienSiirto basic}
                         {:Rasitetoimitus (conj toimitus {:kayttooikeustieto
                                                          {:KayttoOikeus [:kayttooikeuslaji :kayttaja :antaja
                                                                          :valiaikainenKytkin :paattymispvm]}})}
                         {:KiinteistolajinMuutos basic}
                         {:YleisenAlueenLohkominen basic}
                         {:KiinteistojenYhdistaminen basic}
                         {:Halkominen basic}
                         {:KiinteistonMaaritys (concat toimitus [:selvitettavaAsia :kuvaus])}
                         {:Tilusvaihto basic}]}]
    {:tag :Kiinteistotoimitus :ns "kiito"
     :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :KT "1.0.2")
                   :xmlns:kiito "http://www.paikkatietopalvelu.fi/gml/kiinteistotoimitus"}
                  mapping-common/common-namespaces)
     :child [(mapping-common/mapper toimitus-types "gml")]}))

(defn bind-attachments
  "As agreed with CGI, the attachments are part of
  Toimitushakemus. Thus, we need to add (the same) application
  attachments to every operative document."
  [canon attachments]
  (defn binder [[k xs]]
    {k (map #(assoc-in % [:toimitushakemustieto :Toimitushakemus :liitetieto] attachments) xs)})
  (if attachments
    (let [m (-> canon :Kiinteistotoimitus :featureMembers)
          attached (reduce #(conj %1 (binder %2)) {} m)]
      (assoc-in canon [:Kiinteistotoimitus :featureMembers] attached))
    canon))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of
  attachment file IDs that were sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments (kiinteistotoimitus-canonical application lang)
        attachments-canonical (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical (bind-attachments canonical-without-attachments attachments-canonical)
        mapping (->mapping)
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

(permit/register-function permit/KT :app-krysp-mapper save-application-as-krysp)
