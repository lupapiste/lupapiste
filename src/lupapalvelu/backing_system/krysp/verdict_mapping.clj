(ns lupapalvelu.backing-system.krysp.verdict-mapping
  (:require [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.backing-system.krysp.poikkeamis-mapping :as p-mapping]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :as r-mapping]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping :as ya-mapping]
            [lupapalvelu.document.attachments-canonical :as att-canonical]
            [lupapalvelu.document.canonical-common :as common] ;; ensure canonical multimethods are required
            [lupapalvelu.document.poikkeamis-canonical]
            [lupapalvelu.document.rakennuslupa-canonical]
            [lupapalvelu.document.yleiset-alueet-canonical]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.pate.verdict-canonical :as verdict-canonical]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.permit :as permit]
            [sade.date :as date]
            [sade.util :as util]))

(def ^:private default-kayttotapaus "Uusi päätös")

(defn- verdict-attachment-pred [application verdict]
  (if (foreman-util/foreman-app? application)
    (fn [{source :source}]
      (not= (:id source) (:id verdict)))
    (let [selected-ids (-> (:attachments application) (vc/selected-attachment-ids verdict) set)]
      (fn [{att-id :id target :target source :source}]
        (and
          (not= (:id source) (:id verdict))
          (or (= (:id target) (:id verdict))
              (selected-ids att-id)))))))

(defn add-vastuiden-alkamispvm-in-foreman-verdicts
  [canonical-application alkamis-pvm]
  (mapping-common/assoc-canonical-foreman-field canonical-application :alkamisPvm alkamis-pvm))

(defn application-primary-paloluokka
  [application]
  (let [operation-ids     (->> (cons (:primaryOperation application)
                                     (:secondaryOperations application))
                               (map :id))
        documents-for-op  #(->> application
                                :documents
                                (filter (util/fn-> :schema-info :op :id (= %))))]
    (some #(->> % documents-for-op (some (util/fn-> :data :luokitus :paloluokka :value)))
          operation-ids)))

(defmethod permit/verdict-krysp-mapper :YA [application organization verdict lang krysp-version begin-of-link]
  (let [lupa-name-key                 (ya-mapping/resolve-lupa-name-key application)
        verdict-attachments-canonical (att-canonical/get-attachments-as-canonical application
                                                                                  organization
                                                                                  begin-of-link
                                                                                  (verdict-attachment-pred application verdict))
        verdict-link                  (att-canonical/verdict-attachment-link application organization verdict begin-of-link)
        verdict-canonical             (cond-> (verdict-canonical/verdict-canonical lang verdict application)
                                        (seq verdict-attachments-canonical) (assoc-in [:Paatos :liitetieto]
                                                                                      verdict-attachments-canonical)
                                        (some? verdict-link) (assoc-in [:Paatos :paatoslinkki]
                                                                       (:linkkiliitteeseen verdict-link)))
        paatostieto-path              [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :paatostieto]
        katselmustieto                (get-in verdict-canonical [:Paatos :lupamaaraykset :vaaditutKatselmukset])]

    {:attachments (mapping-common/attachment-details-from-canonical (conj verdict-attachments-canonical {:Liite verdict-link}))
     :canonical   verdict-canonical
     :attachment  verdict-link
     :paatostieto paatostieto-path
     :xml         (-> application
                      meta-fields/enrich-with-link-permit-data
                      (common/application->canonical lang)
                      (assoc-in [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :katselmustieto] katselmustieto)
                      (assoc-in paatostieto-path verdict-canonical)
                      ;; LPK-4859: Creating/sending of agreements (sopimukset) to dmCity is commented out for now. Take into use if decided otherwise.
                      #_(update-in [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lupakohtainenLisatietotieto] conj
                                   {:LupakohtainenLisatieto {:selitysteksti "LUVAN_TYYPPI"
                                                             :arvo          (if (ya/agreement-subtype? application) "sopimus" "lupa")}})
                      (ya-mapping/yleisetalueet-element-to-xml lupa-name-key krysp-version))}))

(defn- rakl-krysp-mapper [application organization verdict lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application organization begin-of-link
                                                                          (verdict-attachment-pred application
                                                                                                   verdict))
        verdict-link          (att-canonical/verdict-attachment-link application organization verdict begin-of-link)
        verdict-canonical     (-> (verdict-canonical/verdict-canonical lang verdict application)
                                  (assoc-in [:Paatos :poytakirja :liite] verdict-link))
        paatostieto-path      [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto]
        paatos-avaimet-path   [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :avainArvoTieto]
        asiantiedot-path      [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :asianTiedot :Asiantiedot]
        vastuiden-alkamis-pvm (date/xml-date (get-in verdict [:data :responsibilities-start-date]))
        paloluokka-path       [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia
                               :lisatiedot :Lisatiedot :rakentamisentiedot :paloluokka]
        primary-paloluokka    (application-primary-paloluokka application)]
    {:attachments (mapping-common/attachment-details-from-canonical (conj attachments-canonical {:Liite verdict-link}))
     :canonical   verdict-canonical
     :attachment  verdict-link
     :paatostieto paatostieto-path
     :xml         (-> (meta-fields/enrich-with-link-permit-data application)
                      (common/application->canonical lang)
                      (verdict-canonical/add-pate-verdict-pairs verdict paatos-avaimet-path)
                      (verdict-canonical/update-asiantiedot-with-pate-verdict-data verdict
                                                                                   (conj asiantiedot-path
                                                                                         :rakennusvalvontaasianKuvaus)
                                                                                   (conj asiantiedot-path
                                                                                         :vahainenPoikkeaminen))
                      (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :kayttotapaus]
                                default-kayttotapaus)
                      (assoc-in paatostieto-path verdict-canonical)
                      (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                                attachments-canonical)
                      (assoc-in paloluokka-path primary-paloluokka)
                      (add-vastuiden-alkamispvm-in-foreman-verdicts vastuiden-alkamis-pvm)
                      (r-mapping/add-non-attachment-links application begin-of-link)
                      (r-mapping/rakennuslupa-element-to-xml krysp-version))}))

(defmethod permit/verdict-krysp-mapper :R [application organization verdict lang krysp-version begin-of-link]
  (rakl-krysp-mapper application organization verdict lang krysp-version begin-of-link))

(defmethod permit/verdict-krysp-mapper :TJ-V3 [application organization verdict lang krysp-version begin-of-link]
  (rakl-krysp-mapper application organization verdict lang krysp-version begin-of-link))

(defmethod permit/verdict-krysp-mapper :P [application organization verdict lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application organization begin-of-link
                                                                          (verdict-attachment-pred application
                                                                                                   verdict))
        verdict-link          (att-canonical/verdict-attachment-link application organization verdict begin-of-link)
        verdict-canonical     (-> (verdict-canonical/verdict-canonical lang verdict application)
                                  (update :Paatos dissoc :lupamaaraykset :paatosdokumentinPvm)
                                  (assoc-in [:Paatos :poytakirja :liite] verdict-link))
        suunnittelutarve?     (util/=as-kw :suunnittelutarveratkaisu (:permitSubtype application))
        xml-path              (p-mapping/permit-subtype-xml-path application)
        paatostieto-path      (conj xml-path :paatostieto)
        paatos-avaimet-path   (conj xml-path :avainArvoTieto)
        asiantiedot-path      (conj xml-path :asianTiedot :Asiantiedot)]
    {:attachments (mapping-common/attachment-details-from-canonical (conj attachments-canonical {:Liite verdict-link}))
     :canonical   verdict-canonical
     :attachment  verdict-link
     :paatostieto paatostieto-path
     :xml         (-> (common/application->canonical application lang)
                      (verdict-canonical/add-pate-verdict-pairs verdict paatos-avaimet-path)
                      (verdict-canonical/update-asiantiedot-with-pate-verdict-data verdict
                                                                                   (concat asiantiedot-path
                                                                                           [(if suunnittelutarve?
                                                                                              :suunnittelutarveasianKuvaus
                                                                                              :poikkeamisasianKuvaus)])
                                                                                   (concat asiantiedot-path
                                                                                           [:vahainenPoikkeaminen]))
                      (assoc-in (conj xml-path :luvanTunnistetiedot :LupaTunnus :muuTunnustieto :MuuTunnus)
                                {:tunnus (:id application) :sovellus "Lupapiste"})
                      (assoc-in paatostieto-path verdict-canonical)
                      (assoc-in (conj xml-path :liitetieto) attachments-canonical)
                      (assoc-in (conj xml-path :kayttotapaus) default-kayttotapaus)
                      (p-mapping/poikkeamis-element-to-xml application krysp-version))}))
