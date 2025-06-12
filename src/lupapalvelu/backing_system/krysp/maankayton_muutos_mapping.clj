(ns lupapalvelu.backing-system.krysp.maankayton-muutos-mapping
  (:require [lupapalvelu.backing-system.krysp.dmcity :as krysp-dm]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.maankayton-muutos-canonical :as maankayton-muutos-canonical]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]))


(def maankayton-muutos-mapping-101
  (let [osoite [:valtioSuomeksi :valtioKansainvalinen {:osoitenimi :teksti} :ulkomainenLahiosoite
                :postinumero :postitoimipaikannimi :ulkomainenPostitoimipaikka]]
    [{:toimituksenTiedottieto
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
                                                                {:piste/gml {:Point :pos}}
                                                                {:alue/gml {:Polygon {:exterior {:LinearRing :pos}}}}
                                                                {:viiva/gml {:LineString :pos}}]}}
                                :kohdekiinteisto :maaraAla {:tilatieto {:Tila [:pvm :kasittelija :hakemuksenTila]}}]}]}
     :toimituksenTila {:liitetieto {:Liite/yht [:kuvaus :linkkiliitteeseen :muokkausHetki :versionumero]}}
     :uusiKytkin
     :kuvaus]))

(defn- ->mapping-101 [muutos]
  {:tag :Maankaytonmuutos :ns "mkmu"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MM "1.0.1")
                 :xmlns:mkmu "http://www.paikkatietopalvelu.fi/gml/maankaytonmuutos"}
                (mapping-common/common-namespaces :MM "1.0.1"))
   :child [(mapping-common/mapper {:maankayttomuutosTieto {muutos maankayton-muutos-mapping-101}})]})

(defn- ->mapping-103 [muutos]
  {:tag :Maankaytonmuutos :ns "mkmu"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MM "1.0.3")
                 :xmlns:mkmu "http://www.kuntatietopalvelu.fi/gml/maankaytonmuutos"}
                (mapping-common/common-namespaces :MM "1.0.3"))
   :child [(mapping-common/mapper {:maankayttomuutosTieto {muutos maankayton-muutos-mapping-101}})]})

(defn- ->mapping-103-dmcity [muutos]
  (-> muutos
      ->mapping-103
      krysp-dm/add-dmcity-ns
      (mapping-common/insert-elements
        [:maankayttomuutosTieto muutos]
        {:liitetieto {:replace-with [krysp-dm/dmcity-viitelupatieto
                                     krysp-dm/dmcity-toimenpiteet
                                     krysp-dm/dmcity-kohteet
                                     krysp-dm/dmcity-lausuntotieto
                                     krysp-dm/dmcity-liitetieto]}})))

(defn- ->mapping-106 [muutos]
  {:tag :Maankaytonmuutos :ns "mkmu"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MM "1.0.6")
                 :xmlns:mkmu "http://www.kuntatietopalvelu.fi/gml/maankaytonmuutos"}
                (mapping-common/common-namespaces :MM "1.0.6"))
   :child [(mapping-common/mapper {:maankayttomuutosTieto {muutos maankayton-muutos-mapping-101}})]})

(defn get-mapping [krysp-version muutos]
  (case (name krysp-version)
    "1.0.1"           (->mapping-101 muutos)
    "1.0.3"           (->mapping-103 muutos)
    "1.0.3-dmcity-lp" (->mapping-103-dmcity muutos)
    "1.0.6"           (->mapping-106 muutos)
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defmethod permit/application-krysp-mapper :MM
  [application organization lang krysp-version begin-of-link]
  (let [canonical-without-atts    (maankayton-muutos-canonical/maankayton-muutos-canonical application lang)
        {:keys [canonical-with-statement-atts
                statement-atts]}  (attachments-canon/walk-statement-attachments canonical-without-atts application organization begin-of-link)
        ;; For non-dmCity schemas add attachments to common list since they can't be included with statement info
        statement-atts-supported? (validator/dmcity-version? krysp-version)
        atts-canonical            (cond-> (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
                                    (not statement-atts-supported?)
                                    (into (->> statement-atts vals (reduce into))))
        muutos                    (-> canonical-without-atts :Maankaytonmuutos :maankayttomuutosTieto first key)
        canonical                 (assoc-in
                                    canonical-with-statement-atts
                                    [:Maankaytonmuutos :maankayttomuutosTieto muutos :liitetieto]
                                    (mapping-common/add-generated-pdf-attachments application begin-of-link atts-canonical lang))
        mapping                   (get-mapping krysp-version muutos)
        xml                       (emit/element-to-xml canonical mapping)
        atts-for-write            (mapping-common/attachment-details-from-canonical atts-canonical)]
    {:xml xml
     :attachments atts-for-write}))
