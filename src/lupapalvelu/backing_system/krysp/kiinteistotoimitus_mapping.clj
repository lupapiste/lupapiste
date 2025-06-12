(ns lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping
  (:require [lupapalvelu.backing-system.krysp.dmcity :as krysp-dm]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.kiinteistotoimitus-canonical :as kt-canon :refer [kiinteistotoimitus-canonical]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [sade.util :as util]))

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

(def sijaintitieto_102
  {:sijaintitieto {:Sijainti/yht [{:osoite [:yksilointitieto :alkuHetki {:osoitenimi :teksti}]}
                                  {:piste/gml {:Point :pos}}
                                  {:alue/gml {:Polygon {:exterior {:LinearRing :pos}}}}
                                  {:viiva/gml {:LineString :pos}}]}})

(def toimitus-hakemus_102
  {:Toimitushakemus
   [{:hakemustunnustieto {:Hakemustunnus/yht [:tunnus :sovellus]}}
    osapuolitieto_102
    sijaintitieto_102
    {:liitetieto yht-liite}
    {:kiinteistotieto {:Kiinteisto :kiinteistotunnus}}
    {:maaraAlatieto {:MaaraAla :maaraAlatunnus}}
    {:tilatieto {:Tila [:pvm :kasittelija :hakemuksenTila]}}]})

(def toimitus-hakemus_106
  (assoc-in toimitus-hakemus_102 [:Toimitushakemus 1] osapuolitieto_106))

(def toimitus-hakemus_106-dmcity
  "Special extensions added for dmCity integration"
  {:Toimitushakemus
   [{:hakemustunnustieto {:Hakemustunnus/yht [:tunnus :sovellus]}}
    krysp-dm/dmcity-viitelupatieto
    osapuolitieto_106
    sijaintitieto_102
    krysp-dm/dmcity-toimenpiteet
    krysp-dm/dmcity-kohteet
    krysp-dm/dmcity-lausuntotieto
    krysp-dm/dmcity-liitetieto
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

(def toimitus_106-dmcity
  (vec (mapcat insert-kayttotapaus (toimitus-feature toimitus-hakemus_106-dmcity))))

(def toimitus_108 toimitus_106)

(def toimitus_108-lp
  (vec (mapcat insert-kayttotapaus (toimitus-feature toimitus-hakemus_106-dmcity))))

(defn kiito-mapping-with-toimitus [version toimitus-types]
  {:tag :Kiinteistotoimitus :ns "kiito"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :KT version)
                 :xmlns:kiito "http://www.paikkatietopalvelu.fi/gml/kiinteistotoimitus"}
                (mapping-common/common-namespaces :KT version))
   :child [(mapping-common/mapper toimitus-types "gml")]})

(defn toimitus-types
  "Generates the toimitus types structure. When primary-xml-element is provided,
   that element's entry is placed first in the sequence."
  [toimitus primary-xml-element]
  (let [toimitus-with-kuvaus (conj toimitus :kuvaus)
        all-types            [{:Lohkominen (concat toimitus [:lohkomisenTyyppi :kuvaus])}
                              {:YhtAlueenOsuuksienSiirto toimitus-with-kuvaus}
                              {:Rasitetoimitus (conj toimitus
                                                     {:kayttooikeustieto
                                                      {:KayttoOikeus [:kayttooikeuslaji :kayttaja :antaja
                                                                      :valiaikainenKytkin :paattymispvm]}})}
                              {:KiinteistolajinMuutos toimitus-with-kuvaus}
                              {:YleisenAlueenLohkominen toimitus-with-kuvaus}
                              {:KiinteistojenYhdistaminen toimitus-with-kuvaus}
                              {:Halkominen toimitus-with-kuvaus}
                              {:TilanLohkominen toimitus-with-kuvaus}
                              {:TontinosanLunastus toimitus-with-kuvaus}
                              {:KiinteistonMaaritys (concat toimitus [:selvitettavaAsia :kuvaus])}
                              {:Tilusvaihto toimitus-with-kuvaus}]
        sorted-types         (if primary-xml-element
                               (sort-by #(not= primary-xml-element (ffirst %)) all-types)
                               all-types)]
    {:featureMembers/kiito sorted-types}))

(defn- update-kiito-ns
  "The namespace was changed after 1.0.2"
  [kiito-mapping]
  (update kiito-mapping :attr assoc :xmlns:kiito "http://www.kuntatietopalvelu.fi/gml/kiinteistotoimitus"))

(defn primary-operation-name
  "Given an application returns the primary operation name, based on :primaryOperation id.
   Falls back to :primaryOperation :name if the primary document is not found."
  [application]
  (let [primary-op    (:primaryOperation application)
        primary-op-id (:id primary-op)
        docs          (-> application :documents tools/unwrapped)
        primary-doc   (util/find-first #(= primary-op-id (-> % :schema-info :op :id)) docs)]
    (cond
      (nil? primary-doc)
      (-> primary-op :name keyword)

      ;; Rasitetoimitus is special which has no operation-details method
      (util/=as-kw (-> primary-doc :schema-info :name) :rasitetoimitus)
      :Rasitetoimitus

      :else
      (:name (kt-canon/operation-details primary-doc)))))

(defn get-mapping
  "Creates the mapping for the given KuntaGML version with primary operation first."
  ([krysp-version]
   (get-mapping krysp-version nil))
  ([krysp-version primary-xml-element]
   {:pre [krysp-version]}
   (case (name krysp-version)
     "1.0.2"           (kiito-mapping-with-toimitus "1.0.2" (toimitus-types toimitus_102 primary-xml-element))
     "1.0.5"           (-> (kiito-mapping-with-toimitus "1.0.5" (toimitus-types toimitus_105 primary-xml-element))
                           update-kiito-ns)
     "1.0.6"           (-> (kiito-mapping-with-toimitus "1.0.6" (toimitus-types toimitus_106 primary-xml-element))
                           update-kiito-ns)
     "1.0.6-dmcity-lp" (-> (kiito-mapping-with-toimitus "1.0.6" (toimitus-types toimitus_106-dmcity primary-xml-element))
                           update-kiito-ns
                           krysp-dm/add-dmcity-ns)
     "1.0.8"           (-> (kiito-mapping-with-toimitus "1.0.8" (toimitus-types toimitus_108 primary-xml-element))
                           update-kiito-ns)
     "1.0.8-lp"        (-> (kiito-mapping-with-toimitus "1.0.8" (toimitus-types toimitus_108-lp primary-xml-element))
                           update-kiito-ns
                           krysp-dm/add-dmcity-ns)
     (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version))))))

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
  (let [canonical-without-atts                (kiinteistotoimitus-canonical application lang)
        {:keys [canonical-with-statement-atts
                statement-atts]}              (attachments-canon/walk-statement-attachments canonical-without-atts application organization begin-of-link)
        ;; For non-dmCity schemas add attachments to common list since they can't be included with statement info
        statement-atts-supported?             (validator/dmcity-version? krysp-version)
        atts-canonical                        (cond-> (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
                                                (not statement-atts-supported?)
                                                (into (->> statement-atts vals (reduce into))))
        canonical                             (bind-attachments
                                                canonical-with-statement-atts
                                                (mapping-common/add-generated-pdf-attachments application begin-of-link atts-canonical lang))
        primary-xml-element                   (primary-operation-name application)
        mapping                               (get-mapping krysp-version primary-xml-element)
        xml                                   (emit/element-to-xml canonical mapping)
        all-canonical-atts                    (concat atts-canonical (attachments-canon/flatten-statement-attachments statement-atts))
        atts-for-write                        (mapping-common/attachment-details-from-canonical all-canonical-atts)]
    {:xml         xml
     :attachments atts-for-write}))
