(ns lupapalvelu.backing-system.krysp.poikkeamis-mapping
  (:require [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

(def kerrosalatieto {:tag :kerrosalatieto :child [{:tag :kerrosala :child [{:tag :pintaAla}
                                                  {:tag :paakayttotarkoitusKoodi}]}]})

(def tavoitetila-212 {:tag :Tavoitetila :child [{:tag :paakayttotarkoitusKoodi}
                                                {:tag :asuinhuoneistojenLkm}
                                                {:tag :rakennuksenKerrosluku}
                                                {:tag :kokonaisala}
                                                kerrosalatieto]})

(def tavoitetila-213 {:tag :Tavoitetila :child [{:tag :paakayttotarkoitusKoodi}
                                                {:tag :asuinhuoneistojenLkm}
                                                {:tag :rakennuksenKerrosluku}
                                                {:tag :kokonaisala}
                                                {:tag :kerrosala}]})

(def avainsanatieto_223
  {:tag :avainsanaTieto :child [{:tag :Avainsana}]})

(def menettely-tos_223
  {:tag :menettelyTOS})

(def abstract-poikkeamistype-212 [{:tag :kasittelynTilatieto :child [mapping-common/tilamuutos]}
                                  {:tag :luvanTunnistetiedot :child [mapping-common/lupatunnus]}
                                  {:tag :osapuolettieto :child [mapping-common/osapuolet_210]}
                                  {:tag :rakennuspaikkatieto :child [mapping-common/rakennuspaikka]}
                                  {:tag :toimenpidetieto :child [{:tag :Toimenpide :child [{:tag :kuvausKoodi }
                                                                                           kerrosalatieto
                                                                                           {:tag :tavoitetilatieto :child [tavoitetila-212]}]}]}
                                  {:tag :lausuntotieto :child [mapping-common/lausunto_211]}
                                  {:tag :paatostieto :child [mapping-common/paatokset_218]}
                                  {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_211}]}
                                  {:tag :lisatietotieto :child [{:tag :Lisatieto :child [{:tag :asioimiskieli}]}]}
                                  {:tag :asianTiedot :child [{:tag :Asiantiedot :child [{:tag :vahainenPoikkeaminen}
                                                                                        {:tag :poikkeamisasianKuvaus}
                                                                                        {:tag :suunnittelutarveasianKuvaus}]}]}
                                  {:tag :kayttotapaus}])

(def abstract-poikkeamistype-213
  (-> abstract-poikkeamistype-212
   (mapping-common/update-child-element [:rakennuspaikkatieto] {:tag :rakennuspaikkatieto :child [mapping-common/rakennuspaikka_211]})
   (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_211]})
   (mapping-common/update-child-element [:toimenpidetieto :Toimenpide :tavoitetilatieto] {:tag :tavoitetilatieto :child [tavoitetila-213]})))

(def abstract-poikkeamistype-214
  (-> abstract-poikkeamistype-213
    (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_212]})))

; 2.1.5: update lausunto
(def abstract-poikkeamistype-215
  (-> abstract-poikkeamistype-214
    (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_213]})
    (mapping-common/update-child-element [:lausuntotieto] {:tag :lausuntotieto :child [mapping-common/lausunto_213]})
    (mapping-common/update-child-element [:liitetieto :Liite] {:tag :Liite :child mapping-common/liite-children_213})))

(def abstract-poikkeamistype-220
  (-> abstract-poikkeamistype-215
    (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_215]})))

(def abstract-poikkeamistype-221
  (-> abstract-poikkeamistype-220
    (mapping-common/update-child-element [:rakennuspaikkatieto] {:tag :rakennuspaikkatieto :child [mapping-common/rakennuspaikka_216]})
    (mapping-common/update-child-element [:lausuntotieto] {:tag :lausuntotieto :child [mapping-common/lausunto_216]})
    (mapping-common/update-child-element [:liitetieto :Liite] {:tag :Liite :child mapping-common/liite-children_216})
    (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_216]})))

(def abstract-poikkeamistype-223
  (-> abstract-poikkeamistype-221
      (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_218]})))


(def abstract-poikkeamistype-224
  (-> abstract-poikkeamistype-223
      (mapping-common/update-child-element [:liitetieto :Liite] {:tag :Liite :child mapping-common/liite-children_219})
      (mapping-common/update-child-element [:lausuntotieto] {:tag :lausuntotieto :child [mapping-common/lausunto_219]})
      (mapping-common/update-child-element [:paatostieto] {:tag :paatostieto :child [mapping-common/paatokset_219]})
      (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_219]})))

(def poikkeamisasia-223
  (-> abstract-poikkeamistype-221
    (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_218]})
    (conj avainsanatieto_223 menettely-tos_223)))

(def poikkeamisasia-224
  (-> abstract-poikkeamistype-224
      (conj avainsanatieto_223 menettely-tos_223)))

(def suunnittelutarveasia-223
  (-> abstract-poikkeamistype-221
    (mapping-common/update-child-element [:osapuolettieto] {:tag :osapuolettieto :child [mapping-common/osapuolet_218]})
    (conj menettely-tos_223)))

(def suunnittelutarveasia-224
  (-> abstract-poikkeamistype-224
      (conj menettely-tos_223)))

(def poikkeamis_to_krysp_212
  {:tag :Popast
   :ns "ppst"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :P "2.1.2")
                 :xmlns:ppst "http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu"}
                (mapping-common/common-namespaces :P "2.1.2"))
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstract-poikkeamistype-212}]}
           {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child abstract-poikkeamistype-212}]}]})

(def poikkeamis_to_krysp_213
  (-> poikkeamis_to_krysp_212
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :P "2.1.3"))
    (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                   {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstract-poikkeamistype-213}]}
                   {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child abstract-poikkeamistype-213}]}])))

(def poikkeamis_to_krysp_214
  (-> poikkeamis_to_krysp_213
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :P "2.1.4"))
    (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                   {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstract-poikkeamistype-214}]}
                   {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child abstract-poikkeamistype-214}]}])))

(def poikkeamis_to_krysp_215
  (-> poikkeamis_to_krysp_214
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :P "2.1.5"))
    (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                   {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstract-poikkeamistype-215}]}
                   {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child abstract-poikkeamistype-215}]}])))

(def poikkeamis_to_krysp_220
  (-> poikkeamis_to_krysp_215
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :P "2.2.0"))
    (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                   {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstract-poikkeamistype-220}]}
                   {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child abstract-poikkeamistype-220}]}])))

(def poikkeamis_to_krysp_221
  (-> poikkeamis_to_krysp_220
    (assoc-in [:attr :xsi:schemaLocation] (mapping-common/schemalocation :P "2.2.1"))
    (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                   {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstract-poikkeamistype-221}]}
                   {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child abstract-poikkeamistype-221}]}])))

(def poikkeamis_to_krysp_223
  (-> poikkeamis_to_krysp_221
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :P "2.2.3")
                  :xmlns:ppst "http://www.kuntatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu"}
                 (mapping-common/common-namespaces :P "2.2.3"))
      (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                     {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child poikkeamisasia-223}]}
                     {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child suunnittelutarveasia-223}]}])))

(def poikkeamis_to_krysp_224
  (-> poikkeamis_to_krysp_223
      (update-in [:attr] merge
                 {:xsi:schemaLocation (mapping-common/schemalocation :P "2.2.4")
                  :xmlns:ppst "http://www.kuntatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu"}
                 (mapping-common/common-namespaces :P "2.2.4"))
      (assoc :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                     {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child poikkeamisasia-224}]}
                     {:tag :suunnittelutarveasiatieto :child [{:tag :Suunnittelutarveasia :child suunnittelutarveasia-224}]}])))

(defn get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2" poikkeamis_to_krysp_212
    "2.1.3" poikkeamis_to_krysp_213
    "2.1.4" poikkeamis_to_krysp_214
    "2.1.5" poikkeamis_to_krysp_215
    "2.2.0" poikkeamis_to_krysp_220
    "2.2.1" poikkeamis_to_krysp_221
    "2.2.3" poikkeamis_to_krysp_223
    "2.2.4" poikkeamis_to_krysp_224
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums [canonical krysp-path krysp-version]
  (cond-> canonical
          (get-in canonical (conj krysp-path :lausuntotieto)) (update-in (conj krysp-path :lausuntotieto) mapping-common/lausuntotieto-map-enum :P krysp-version)
          (get-in canonical (conj krysp-path :rakennuspaikkatieto)) (update-in (conj krysp-path :rakennuspaikkatieto) mapping-common/rakennuspaikkatieto-map-enum :P krysp-version)))


(defmethod permit/application-krysp-mapper :P
  [application organization lang krysp-version begin-of-link]
  (let [subtype    (keyword (:permitSubtype application))
        krysp-path (condp = subtype
                      permit/poikkeamislupa [:Popast :poikkeamisasiatieto :Poikkeamisasia]
                      permit/suunnittelutarveratkaisu [:Popast :suunnittelutarveasiatieto :Suunnittelutarveasia]
                      nil)
        krysp-path-statement (conj krysp-path :lausuntotieto)
        canonical-without-attachments  (common/application->canonical application lang)
        statement-given-ids (common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-path-statement))
        statement-attachments (attachments-canon/get-statement-attachments-as-canonical application organization begin-of-link statement-given-ids)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        canonical-with-statement-attachments  (attachments-canon/add-statement-attachments canonical-without-attachments statement-attachments krysp-path-statement)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    (conj krysp-path :liitetieto)
                    (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang))
        xml (element-to-xml (common-map-enums canonical krysp-path krysp-version) (get-mapping krysp-version))
        all-canonical-attachments (concat attachments-canonical (attachments-canon/flatten-statement-attachments statement-attachments))
        attachments-for-write (mapping-common/attachment-details-from-canonical all-canonical-attachments)]
    {:xml xml
     :attachments attachments-for-write}))
