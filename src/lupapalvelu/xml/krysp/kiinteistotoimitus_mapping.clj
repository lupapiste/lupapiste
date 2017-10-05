(ns lupapalvelu.xml.krysp.kiinteistotoimitus-mapping
  (:require [clojure.walk :as walk]
            [lupapalvelu.document.kiinteistotoimitus-canonical :refer [kiinteistotoimitus-canonical]]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]))

(def osoite [:valtioSuomeksi :valtioKansainvalinen {:osoitenimi :teksti} :ulkomainenLahiosoite
             :postinumero :postitoimipaikannimi :ulkomainenPostitoimipaikka])

(def yht-liite {:Liite/yht [:kuvaus :linkkiliitteeseen :muokkausHetki :versionumero]})


(def toimitus-hakemus_102
  {:Toimitushakemus
   [{:hakemustunnustieto {:Hakemustunnus/yht [:tunnus :sovellus]}}
    {:osapuolitieto
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
    {:liitetieto yht-liite}
    {:kiinteistotieto {:Kiinteisto :kiinteistotunnus}}
    {:maaraAlatieto {:MaaraAla :maaraAlatunnus}}
    {:tilatieto {:Tila [:pvm :kasittelija :hakemuksenTila]}}]})

(defn toimitus-feature [toimitus-hakemus]
  [{:toimituksenTiedottieto
    {:ToimituksenTiedot/yht [:aineistonnimi :aineistotoimittaja :tila :toimitusPvm :kuntakoodi
                             :kielitieto]}}
   {:toimitushakemustieto [toimitus-hakemus]}
   :toimituksenTila
   {:kiinteistotieto {:Kiinteisto :kiinteistotunnus}}
   {:maaraAlatieto {:MaaraAla :maaraAlatunnus}}])

(def toimitus_102 (toimitus-feature toimitus-hakemus_102))

(def kiito-liite-type                               ; since 1.0.3, used in AbstractToimitusFeatureType, not in toimitushakemus!
  {:Liite
   [:kuvaus/yht
    :linkkiliitteeseen/yht
    :muokkausHetki/yht
    :versionumero/yht
    {:tekija {:Osapuoli/yht (:child mapping-common/osapuoli-body_218)}}
    :tyyppi
    {:metatietotieto {:metatieto [:metatietoArvo :metatietoNimi]}}]})

(defn- insert-kayttotapaus
  "on 1.0.5 kayttotapaus was added to AbstractToimitusFeatureType"
  [e]
  (let [elem-name (if (map? e)
                    (-> e (keys) (first))
                    e)]
    (if (= :kiinteistotieto elem-name)
      [:kayttotapaus e]                                  ; "put" kayttotapaus before kiinteistotieto
      [e])))

(def toimitus_105
  (vec (mapcat insert-kayttotapaus (toimitus-feature toimitus-hakemus_102))))

(defn kiito-mapping-with-toimitus [version toimitus-types]
  {:tag :Kiinteistotoimitus :ns "kiito"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :KT version)
                 :xmlns:kiito "http://www.paikkatietopalvelu.fi/gml/kiinteistotoimitus"}
                (mapping-common/common-namespaces :KT version))
   :child [(mapping-common/mapper toimitus-types "gml")]})

(defn toimitus-types [toimitus]
  (let [toimitus-with-kuvaus (conj toimitus :kuvaus)]
    {:featureMembers/kiito
     [{:Lohkominen (concat toimitus [:lohkomisenTyyppi :kuvaus])}
      {:YhtAlueenOsuuksienSiirto toimitus-with-kuvaus}
      {:Rasitetoimitus (conj toimitus
                             {:kayttooikeustieto
                              {:KayttoOikeus [:kayttooikeuslaji :kayttaja :antaja
                                              :valiaikainenKytkin :paattymispvm]}})}
      {:KiinteistolajinMuutos toimitus-with-kuvaus}
      {:YleisenAlueenLohkominen toimitus-with-kuvaus}
      {:KiinteistojenYhdistaminen toimitus-with-kuvaus}
      {:Halkominen toimitus-with-kuvaus}
      {:KiinteistonMaaritys (concat toimitus [:selvitettavaAsia :kuvaus])}
      {:Tilusvaihto toimitus-with-kuvaus}]}))

(def kiinteistotoimitus_to_krysp_102
  (kiito-mapping-with-toimitus "1.0.2" (toimitus-types toimitus_102)))

(def kiinteistotoimitus_to_krysp_105
  (-> (kiito-mapping-with-toimitus "1.0.5" (toimitus-types toimitus_105))
      (update :attr assoc :xmlns:kiito "http://www.kuntatietopalvelu.fi/gml/kiinteistotoimitus")))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "1.0.2" kiinteistotoimitus_to_krysp_102
    "1.0.5" kiinteistotoimitus_to_krysp_105
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn bind-attachments
  "As agreed with CGI, the attachments are part of
  Toimitushakemus. Thus, we need to add (the same) application
  attachments to every operative document."
  [canon attachments]
  (letfn [(binder [[k xs]]
            {k (map #(assoc-in %
                               [:toimitushakemustieto :Toimitushakemus :liitetieto]
                               attachments)
                    xs)})]
    (if attachments
      (let [m (-> canon :Kiinteistotoimitus :featureMembers)
            attached (reduce #(conj %1 (binder %2)) {} m)]
        (assoc-in canon [:Kiinteistotoimitus :featureMembers] attached))
      canon)))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of
  attachment file IDs that were sent."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments (kiinteistotoimitus-canonical application lang)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application begin-of-link)
        canonical (bind-attachments canonical-without-attachments
                                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        mapping (get-mapping krysp-version)
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

(defmethod permit/application-krysp-mapper :KT [application lang submitted-application krysp-version output-dir begin-of-link]
  (save-application-as-krysp application lang submitted-application krysp-version output-dir begin-of-link))
