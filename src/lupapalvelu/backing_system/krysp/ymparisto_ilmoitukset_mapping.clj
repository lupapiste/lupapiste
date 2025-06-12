(ns lupapalvelu.backing-system.krysp.ymparisto-ilmoitukset-mapping
  (:require [lupapalvelu.backing-system.krysp.dmcity :as krysp-dm]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as ct]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [sade.util :as util]))


(def ilmoitus-kohde-elements
  "Fields derived from ymi:AbstractIlmoitusKohde and shared by all ilmoitus types"
  [{:tag :yksilointitieto :ns "yht"}
   {:tag :alkuHetki :ns "yht"}
   {:tag :kasittelytietotieto
    :child [{:tag :Kasittelytieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :luvanTunnistetiedot
    :child [mapping-common/ymp-lupatunnus]}
   {:tag :lausuntotieto
    :child [mapping-common/lausunto_213]}
   {:tag :toiminnanSijaintitieto
    :child [{:tag :ToiminnanSijainti :child [{:tag :Osoite :child mapping-common/postiosoite-children-ns-yht}
                                             {:tag :Kunta}
                                             mapping-common/sijantiType
                                             {:tag :Kiinteistorekisterinumero}]}]}
   {:tag :ilmoittaja :child mapping-common/yhteystietotype-children_213}
   {:tag :jatkoIlmoitusKytkin} ; boolean
   {:tag :asianKuvaus}])

(defn ilmoitus [tag1 tag2 & children]
  {:tag   tag1
   :child [{:tag   tag2
            :child (into ilmoitus-kohde-elements children)}]})

(def liitetieto_213
  {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]})

(def ilmoitus_to_krysp_212
  {:tag :Ilmoitukset
   :ns "ymi"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :YI "2.1.2")
                 :xmlns:ymi "http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset"}
                (mapping-common/common-namespaces :YI "2.1.2"))
   :child [{:tag :toimituksenTiedot
            :child mapping-common/toimituksenTiedot}
           ;; The four following elements are mutually exclusive and determined by application operation
           {:tag   :melutarina
            :child [{:tag   :Melutarina
                     :child (into ilmoitus-kohde-elements
                                  [{:tag   :toimintatieto
                                    :child [{:tag   :Toiminta
                                             :child [{:tag :yksilointitieto :ns "yht"}
                                                     {:tag :alkuHetki :ns "yht"}
                                                     {:tag :rakentaminen :child [{:tag :louhinta}
                                                                                 {:tag :murskaus}
                                                                                 {:tag :paalutus}
                                                                                 {:tag :muu}]}
                                                     {:tag :tapahtuma :child [{:tag :ulkoilmakonsertti}
                                                                              {:tag :muu}]}]}]}
                                   {:tag   :toiminnanKesto
                                    :child [{:tag :alkuPvm}
                                            {:tag :loppuPvm}
                                            {:tag :arkiAlkuAika}
                                            {:tag :arkiLoppuAika}
                                            {:tag :lauantaiAlkuAika}
                                            {:tag :lauantaiLoppuAika}
                                            {:tag :sunnuntaiAlkuAika}
                                            {:tag :sunnuntaiLoppuAika}]}
                                   {:tag   :melutiedot
                                    :child [{:tag :koneidenLkm}
                                            {:tag :melutaso :child [{:tag :db}
                                                                    {:tag :paiva}
                                                                    {:tag :yo}
                                                                    {:tag :mittaaja}]}]}
                                   {:tag :koontiKentta}
                                   liitetieto_213])}]}
           {:tag   :koeluontoinentoiminta
            :child [{:tag   :Koeluontoinentoiminta
                     :child (into ilmoitus-kohde-elements
                                  [{:tag   :kuvausKoeluonteisestaToiminnasta
                                    ;; The children are actually not support; see canonical
                                    :child [{:tag :kuvaus}
                                            {:tag :raaka_aineet}
                                            {:tag :paastot}
                                            {:tag :selvitys}]}
                                   {:tag :koontiKentta}
                                   liitetieto_213])}]}
           {:tag   :poikkeus
            :child [{:tag   :Poikkeus
                     :child (into ilmoitus-kohde-elements
                                  [{:tag :kuvaus}
                                   {:tag :koontiKentta}
                                   liitetieto_213])}]}
           {:tag   :pimapuhdistus
            :child [{:tag   :Pimapuhdistus
                     :child (into ilmoitus-kohde-elements
                                  [{:tag :koontiKentta}
                                   liitetieto_213])}]}]})

(defn- update-ilmoitus-types
  "Utility function for updating the same element on all types of ilmoitus at once"
  [ilmoitus-to-krysp-mapping path val]
  (reduce (fn [mapping path-prefix]
            (update-in mapping [:child] mapping-common/update-child-element (into path-prefix path) val))
          ilmoitus-to-krysp-mapping
          [[:melutarina :Melutarina]
           [:koeluontoinentoiminta :Koeluontoinentoiminta]
           [:poikkeus :Poikkeus]
           [:pimapuhdistus :Pimapuhdistus]]))

(def ilmoitus_to_krysp_221
  (-> ilmoitus_to_krysp_212
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :YI "2.2.1"))

      ; Uses LausuntoYmpType where attachments have not changed

      ; Address is read from application, no actual changes
      (update-ilmoitus-types [:toiminnanSijaintitieto :ToiminnanSijainti :Osoite]
                             {:tag :Osoite :child mapping-common/postiosoite-children-ns-yht-215})

      ; Support for foreign addresses
      (update-ilmoitus-types [:ilmoittaja] {:tag :ilmoittaja :child mapping-common/yhteystietotype-children_215})

      ; No changes to attachments
      ))

(def ilmoitus_to_krysp_223
  (-> ilmoitus_to_krysp_221
      (update :attr merge
              {:xsi:schemaLocation (mapping-common/schemalocation :YI "2.2.3")
               :xmlns:ymi "http://www.kuntatietopalvelu.fi/gml/ymparisto/ilmoitukset"}
              (mapping-common/common-namespaces :YI "2.2.3"))))

(def ilmoitus_to_krysp_224
  (-> ilmoitus_to_krysp_223
      (update :attr merge
              {:xsi:schemaLocation (mapping-common/schemalocation :YI "2.2.4")}
              (mapping-common/common-namespaces :YI "2.2.4"))
      (update-ilmoitus-types [:ilmoittaja] {:tag :ilmoittaja :child mapping-common/yhteystietotype-children_219})))

(defn ilmoitus_to_krysp_224-dmcity [payer?]
  (reduce (fn [mapping path]
            (-> mapping (mapping-common/insert-elements
                         path
                         {:lausuntotieto {:replace-with [krysp-dm/dmcity-toimenpiteet
                                                         krysp-dm/dmcity-kohteet
                                                         krysp-dm/dmcity-viitelupatieto
                                                         krysp-dm/dmcity-lausuntotieto]}
                          :ilmoittaja    {:replace-with  (if payer?
                                                           [{:tag :ilmoittaja :child mapping-common/yhteystietotype-children_213}
                                                            {:tag :maksajatieto :child [{:tag :Maksaja :child mapping-common/maksajatype-children_219}]}]
                                                           [{:tag :ilmoittaja :child mapping-common/yhteystietotype-children_213}])}
                          :liitetieto    {:replace-with [krysp-dm/dmcity-liitetieto]}})))
          (krysp-dm/add-dmcity-ns ilmoitus_to_krysp_224)
          [[:melutarina :Melutarina]
           [:koeluontoinentoiminta :Koeluontoinentoiminta]
           [:poikkeus :Poikkeus]
           [:pimapuhdistus :Pimapuhdistus]]))

(def ilmoitus_to_krysp_226
  (-> ilmoitus_to_krysp_224
      (update :attr merge
              {:xsi:schemaLocation (mapping-common/schemalocation :YI "2.2.6")}
              (mapping-common/common-namespaces :YI "2.2.6"))))

(defn- get-mapping [krysp-version payer?]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2"           ilmoitus_to_krysp_212
    "2.2.1"           ilmoitus_to_krysp_221
    "2.2.3"           ilmoitus_to_krysp_223
    "2.2.4"           ilmoitus_to_krysp_224
    "2.2.4-dmcity-lp" (ilmoitus_to_krysp_224-dmcity payer?)
    "2.2.6"           ilmoitus_to_krysp_226
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums
  "Need to be careful here not to inadvertently create an element like melutarina unless it already exists;
  otherwise the mapping will get confused and try to add it to the XML"
  [canonical krysp-version]
  (let [update-statements (fn [canonical-model [step-1 step-2] permit-type-kw]
                            (util/update-in-when canonical-model
                                                 [:Ilmoitukset step-1 step-2 :lausuntotieto]
                                                 mapping-common/lausuntotieto-map-enum
                                                 permit-type-kw
                                                 krysp-version))]
    (-> canonical
        (update-statements [:melutarina :Melutarina] :YI)
        (update-statements [:koeluontoinentoiminta :Koeluontoinentoiminta] :YM)
        (update-statements [:poikkeus :Poikkeus] :YM)
        (update-statements [:pimapuhdistus :Pimapuhdistus] :YL))))

(defn ymparistoilmoitus-element-to-xml [canonical krysp-version & {:keys [payer?]}]
  (element-to-xml (common-map-enums canonical krysp-version) (get-mapping krysp-version payer?)))

(defn ymp-payer-enabled? [organization]
  (and (:dmcity-kuntagml-schemas-enabled organization)
       (:ymp-payer-enabled organization)))

(defn- ymparistoilmoitus-krysp-mapper [application organization lang krysp-version begin-of-link]
  (let [krysp-polku             (ct/canonical-path application)
        krysp-polku-lausuntoon  (conj krysp-polku :lausuntotieto)
        krysp-polku-liitteeseen (conj krysp-polku :liitetieto)
        canonical-without-atts  (common/application->canonical application lang)
        statement-atts          (->> (get-in canonical-without-atts krysp-polku-lausuntoon)
                                     common/statements-ids-with-status
                                     (attachments-canon/get-statement-attachments-as-canonical
                                      application organization begin-of-link))
        atts-canonical          (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        atts-with-pdf-atts      (mapping-common/add-generated-pdf-attachments application begin-of-link atts-canonical lang)
        canonical               (-> canonical-without-atts
                                    (attachments-canon/add-statement-attachments statement-atts krysp-polku-lausuntoon)
                                    (assoc-in krysp-polku-liitteeseen atts-with-pdf-atts))
        xml                     (ymparistoilmoitus-element-to-xml canonical krysp-version {:payer? (ymp-payer-enabled? organization)})
        all-canonical-atts      (into atts-canonical (attachments-canon/flatten-statement-attachments statement-atts))
        attachments-for-write   (mapping-common/attachment-details-from-canonical all-canonical-atts)]
    {:xml         xml
     :attachments attachments-for-write}))

(defmethod permit/application-krysp-mapper :YI
  [application organization lang krysp-version begin-of-link]
  (if (ct/YI-kuntagml-schema? application)
    (ymparistoilmoitus-krysp-mapper application organization lang krysp-version begin-of-link)
    ;; Use generic ymparistolupa schema if not specifically one of the four types of ilmoitus supported by the schema
    ;; e.g. rekisterointi-ilmoitus
    (-> application
        (assoc :permitType "YM")
        (permit/application-krysp-mapper organization lang krysp-version begin-of-link))))
