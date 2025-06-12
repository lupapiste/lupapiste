(ns lupapalvelu.backing-system.krysp.ymparistolupa-mapping
  (:require [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.backing-system.krysp.dmcity :as krysp-dm]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.ymparistolupa-canonical :as ymparistolupa-canonical]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

(def toiminta-aika-children [{:tag :alkuHetki} ; time
                             {:tag :loppuHetki} ; time
                             {:tag :vuorokausivaihtelu}]) ; string

(def tuotanto-children [{:tag :yhteistuotanto} ; string
                        {:tag :erillistuotanto}]) ; string

(def Varastointipaikka {:tag :Varastointipaikka
                        :child [mapping-common/yksilointitieto
                                mapping-common/alkuHetki
                                (mapping-common/sijaintitieto "yht")
                                {:tag :kuvaus}]}); string

(def ymparistolupaType
  [{:tag :kasittelytietotieto :child [{:tag :Kasittelytieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :luvanTunnistetiedot :child [mapping-common/ymp-lupatunnus]}
   ; 0-n {:tag :valvontatapahtumattieto :child []}
   {:tag :lausuntotieto :child [mapping-common/lausunto_213]}
   {:tag :maksajatieto :child [{:tag :Maksaja :child mapping-common/maksajatype-children_213}]}
   {:tag :hakija :child mapping-common/yhteystietotype-children_213}
   {:tag :toiminta
    :child [{:tag :peruste} ; string
            {:tag :kuvaus} ;string
            {:tag :luvanHakemisenSyy}]} ;optional string
   {:tag :laitoksentiedot :child [{:tag :Laitos :child [mapping-common/yksilointitieto
                                                        mapping-common/alkuHetki
                                                        {:tag :laitoksenNimi}
                                                        {:tag :osoite :child mapping-common/postiosoite-children-ns-yht}
                                                        (mapping-common/sijaintitieto "yht")
                                                        {:tag :toimialatunnus}
                                                        {:tag :toimiala}
                                                        {:tag :yhteyshenkilo :child mapping-common/henkilo-child-ns-yht}
                                                        {:tag :toimintaAika :child [{:tag :aloitusPvm} {:tag :lopetusPvm}]}
                                                        {:tag :tyontekijamaara}
                                                        {:tag :henkilotyovuodet}
                                                        {:tag :kiinttun}
                                                        {:tag :rakennustunnustieto, :child [{:tag :kiinttun :ns "yht"} {:tag :rakennusnro :ns "yht"}]}]}]}
   {:tag :voimassaOlevatLuvat
    :child [{:tag :luvat :child [{:tag :lupa :child [{:tag :tunnistetieto} ; string
                                                     {:tag :kuvaus} ; string
                                                     {:tag :liite :child mapping-common/liite-children_213}]}]}
            {:tag :vakuutukset :child [{:tag :vakuutus :child [{:tag :vakuutusyhtio} ; string
                                                               {:tag :vakuutusnumero}]}]}]} ; string
;   {:tag :alueJaYmparisto :child [{:tag :kiinteistonLaitokset :child [{:tag :kiinteistorekisteritunnus} ; string
;                                                                      {:tag :laitos}]}]} ; string
    {:tag :toiminnanSijaintitieto :child [{:tag :ToiminnanSijainti :child [mapping-common/yksilointitieto
                                                                           mapping-common/alkuHetki
                                                                           (mapping-common/sijaintitieto "yht")
                                                                           ;{:tag :ymparistoolosuhteet :child mapping-common/liite-children_213}
                                                                           ;{:tag :ymparistonLaatu :child mapping-common/liite-children_213}
                                                                           ;{:tag :asutus :child mapping-common/liite-children_213}
                                                                           ;{:tag :kaavoitustilanne :child mapping-common/liite-children_213}
                                                                           ;{:tag :rajanaapurit :child [{:tag :luettelo :child mapping-common/liite-children_213}]}
                                                                           ]
                                               }]}

   {:tag :referenssiPiste :child [mapping-common/gml-point]}
   {:tag :koontiKentta} ; String
   {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]}
   {:tag :asianKuvaus}])

(def ymparistolupa_to_krysp_212
  {:tag :Ymparistoluvat
   :ns "ymy"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :YL "2.1.2")
                 :xmlns:ymy "http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat"}
                (mapping-common/common-namespaces :YL "2.1.2"))
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :ymparistolupatieto :child [{:tag :Ymparistolupa :child ymparistolupaType}]}]})

(defn- update-ymparistolupa [path ymparistolupa-to-krysp-mapping child]
  (update-in ymparistolupa-to-krysp-mapping
             [:child]
             mapping-common/update-child-element
             (concat [:ymparistolupatieto :Ymparistolupa] path)
             {:tag   (last path)
              :child child}))

(defn- update-maksaja [ymparistolupa-to-krysp-mapping maksajatype-children]
  (update-ymparistolupa [:maksajatieto]
                        ymparistolupa-to-krysp-mapping
                        [{:tag :Maksaja :child maksajatype-children}]))

(def update-hakija
  (partial update-ymparistolupa [:hakija]))

(def update-yhteyshenkilo
  (partial update-ymparistolupa [:laitoksentiedot :Laitos :yhteyshenkilo]))

(def ymparistolupa_to_krysp_221
  (-> ymparistolupa_to_krysp_212
      (assoc-in [:attr :xsi:schemaLocation]
                (mapping-common/schemalocation :YL "2.2.1"))

      ; Uses LausuntoYmpType where attachments have not changed

      (update-maksaja mapping-common/maksajatype-children_215)

      (update-hakija mapping-common/yhteystietotype-children_215)

      (update-in [:child] mapping-common/update-child-element
                 [:ymparistolupatieto :Ymparistolupa :laitoksentiedot :Laitos :osoite]
                 {:tag :osoite :child mapping-common/postiosoite-children-ns-yht-215}) ; Not in canonical or form schemas

      (update-yhteyshenkilo mapping-common/henkilo-child-ns-yht-215) ; Not in canonical or form schemas

      ; No change to attachments
      ))

(def ymparistolupa_to_krysp_223
  (-> ymparistolupa_to_krysp_212
      (update :attr merge
              {:xsi:schemaLocation (mapping-common/schemalocation :YL "2.2.3")
               :xmlns:ymy "http://www.kuntatietopalvelu.fi/gml/ymparisto/ymparistoluvat"}
              (mapping-common/common-namespaces :YL "2.2.3"))))

(def ymparistolupa_to_krysp_224
  (-> ymparistolupa_to_krysp_223
      (update :attr merge
              {:xsi:schemaLocation (mapping-common/schemalocation :YL "2.2.4")}
              (mapping-common/common-namespaces :YL "2.2.4"))

      ;; Support for ulkomainen hetu
      (update-maksaja mapping-common/maksajatype-children_219)
      (update-hakija mapping-common/yhteystietotype-children_219)
      ;; Not in canonical or form schemas
      (update-yhteyshenkilo mapping-common/henkilo-child-ns-yht-219)))

(def ymparistolupa_to_krysp_224-dmcity
  (-> ymparistolupa_to_krysp_224
      krysp-dm/add-dmcity-ns
      (mapping-common/insert-elements
        [:ymparistolupatieto :Ymparistolupa]
        {:lausuntotieto {:replace-with [krysp-dm/dmcity-viitelupatieto
                                        krysp-dm/dmcity-toimenpiteet
                                        krysp-dm/dmcity-kohteet
                                        krysp-dm/dmcity-lausuntotieto]}
         :liitetieto    {:replace-with [krysp-dm/dmcity-liitetieto]}})))

(def ymparistolupa_to_krysp_226
  (-> ymparistolupa_to_krysp_224
      (update :attr merge
              {:xsi:schemaLocation (mapping-common/schemalocation :YL "2.2.6")}
              (mapping-common/common-namespaces :YL "2.2.6"))))

(defn- get-mapping [krysp-version]
  {:pre [krysp-version]}
  (case (name krysp-version)
    "2.1.2"           ymparistolupa_to_krysp_212
    "2.2.1"           ymparistolupa_to_krysp_221
    "2.2.3"           ymparistolupa_to_krysp_223
    "2.2.4"           ymparistolupa_to_krysp_224
    "2.2.4-dmcity-lp" ymparistolupa_to_krysp_224-dmcity
    "2.2.6"           ymparistolupa_to_krysp_226
    (throw (IllegalArgumentException. (str "Unsupported KRYSP version " krysp-version)))))

(defn- common-map-enums [canonical krysp-version]
  (-> canonical
      (update-in [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa :lausuntotieto] mapping-common/lausuntotieto-map-enum :YL krysp-version)))

(defn ymparistolupa-element-to-xml [canonical krysp-version]
  (element-to-xml (common-map-enums canonical krysp-version) (get-mapping krysp-version)))

(defn- common-wrapper [application organization lang krysp-version begin-of-link]
  (let [krysp-polku-lausuntoon  [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa :lausuntotieto]
        krysp-polku-liitteeseen [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa :liitetieto]
        canonical-without-atts  (ymparistolupa-canonical/ymparistolupa-canonical application lang)
        statement-given-ids     (-> canonical-without-atts
                                    (get-in krysp-polku-lausuntoon)
                                    common/statements-ids-with-status)
        statement-atts          (attachments-canon/get-statement-attachments-as-canonical
                                  application organization begin-of-link statement-given-ids)
        atts-canonical          (attachments-canon/get-attachments-as-canonical application organization begin-of-link)
        atts-with-pdf-atts      (mapping-common/add-generated-pdf-attachments application begin-of-link atts-canonical lang)
        canonical               (-> canonical-without-atts
                                    (attachments-canon/add-statement-attachments statement-atts krysp-polku-lausuntoon)
                                    (assoc-in krysp-polku-liitteeseen atts-with-pdf-atts))
        xml                     (ymparistolupa-element-to-xml canonical krysp-version)
        all-canonical-atts      (into atts-canonical (attachments-canon/flatten-statement-attachments statement-atts))
        atts-for-write          (mapping-common/attachment-details-from-canonical all-canonical-atts)]
    {:xml         xml
     :attachments atts-for-write}))

(def operation->kuntagml-permit-type
  "Here \"pima\" apps are originally under the YL permit type and the others are under YM.
  Consider migrating these apps directly to the YI and MAL permit types if problems arise"
  {"koeluontoinen-toiminta"                        "YI"
   "ilmoitus-poikkeuksellisesta-tilanteesta"       "YI"
   "pima"                                          "YI"
   "maa-ainesten-kotitarveotto"                    "MAL"
   "yhteiskasittely-maa-aines-ja-ymparistoluvalle" "MAL"})

(defn- misc-krysp-mapper
  "Some YM and YL applications should be turned into KuntaGML using the YI or MAL schema.
  They have been scattered across different permit types for historical reasons
  (those reasons most likely being irregular and uneven KuntaGML implementations)"
  [application organization lang krysp-version begin-of-link]
  (if-let [kuntagml-permit-type (-> application :primaryOperation :name operation->kuntagml-permit-type)]
    (-> application
        (assoc :permitType kuntagml-permit-type)
        (permit/application-krysp-mapper organization lang krysp-version begin-of-link))
    ;; Most YM and YL use the generic ymparistolupa schema
    (common-wrapper application organization lang krysp-version begin-of-link)))

(defmethod permit/application-krysp-mapper :YL
  [application organization lang krysp-version begin-of-link]
  (misc-krysp-mapper application organization lang krysp-version begin-of-link))

(defmethod permit/application-krysp-mapper :YM
  [application organization lang krysp-version begin-of-link]
  (misc-krysp-mapper application organization lang krysp-version begin-of-link))
