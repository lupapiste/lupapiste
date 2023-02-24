(ns lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping
  (:require [lupapalvelu.document.kiinteistotoimitus-canonical :refer [kiinteistotoimitus-canonical]]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]))

(def yht-liite {:Liite/yht [:kuvaus :linkkiliitteeseen :muokkausHetki :versionumero]})

(def osapuolitieto_102
  {:osapuolitieto
   {:Osapuoli
    [:roolikoodi :turvakieltokytkin :asioimiskieli
     {:henkilotieto
      {:Henkilo/yht mapping-common/henkilo-child-ns-yht-215}}
     {:yritystieto
      {:Yritys/yht mapping-common/yritys-child-ns-yht_215}}
     :vainsahkoinenAsiointiKytkin]}})

(def osapuolitieto_106
  {:osapuolitieto
   {:Osapuoli
    [:roolikoodi :turvakieltokytkin :asioimiskieli
     {:henkilotieto
      {:Henkilo/yht mapping-common/henkilo-child-ns-yht-219}}
     {:yritystieto
      {:Yritys/yht mapping-common/yritys-child_219}}
     :vainsahkoinenAsiointiKytkin]}})

(def toimitus-hakemus_102
  {:Toimitushakemus
   [{:hakemustunnustieto {:Hakemustunnus/yht [:tunnus :sovellus]}}
    osapuolitieto_102
    {:sijaintitieto {:Sijainti/yht [{:osoite [:yksilointitieto :alkuHetki {:osoitenimi :teksti}]}
                                    {:piste/gml {:Point :pos}}
                                    {:alue/gml {:Polygon {:exterior {:LinearRing :pos}}}}
                                    {:viiva/gml {:LineString :pos}}]}}
    {:liitetieto yht-liite}
    {:kiinteistotieto {:Kiinteisto :kiinteistotunnus}}
    {:maaraAlatieto {:MaaraAla :maaraAlatunnus}}
    {:tilatieto {:Tila [:pvm :kasittelija :hakemuksenTila]}}]})

(def toimitus-hakemus_106
  (assoc-in toimitus-hakemus_102 [:Toimitushakemus 1] osapuolitieto_106))

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

(def toimitus_106
  (vec (mapcat insert-kayttotapaus (toimitus-feature toimitus-hakemus_106))))

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

(defn- update-ns [kiito-mapping]
  (update kiito-mapping :attr assoc :xmlns:kiito "http://www.kuntatietopalvelu.fi/gml/kiinteistotoimitus"))

(def kiinteistotoimitus_to_krysp_105
  (-> (kiito-mapping-with-toimitus "1.0.5" (toimitus-types toimitus_105))
      update-ns))

(def kiinteistotoimitus_to_krysp_106
  (-> (kiito-mapping-with-toimitus "1.0.6" (toimitus-types toimitus_106))
      update-ns))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "1.0.2" kiinteistotoimitus_to_krysp_102
    "1.0.5" kiinteistotoimitus_to_krysp_105
    "1.0.6" kiinteistotoimitus_to_krysp_106
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

(defmethod permit/application-krysp-mapper :KT
  [application organization lang krysp-version begin-of-link]
  (let [canonical-without-attachments (kiinteistotoimitus-canonical application lang)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        canonical (bind-attachments canonical-without-attachments
                                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        mapping (get-mapping krysp-version)
        xml (emit/element-to-xml canonical mapping)
        attachments-for-write (mapping-common/attachment-details-from-canonical attachments-canonical)]
    {:xml xml
     :attachments attachments-for-write}))
