(ns lupapalvelu.backing-system.krysp.verdict-mapping
  (:require [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.backing-system.krysp.poikkeamis-mapping :as p-mapping]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :as r-mapping]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping :as ya-mapping]
            [lupapalvelu.document.attachments-canonical :as att-canonical]
            [lupapalvelu.document.canonical-common :as common]
            ;; ensure canonical multimethods are required
            [lupapalvelu.document.poikkeamis-canonical]
            [lupapalvelu.document.rakennuslupa-canonical]
            [lupapalvelu.document.yleiset-alueet-canonical]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.pate.schema-util :refer [application->category]]
            [lupapalvelu.pate.verdict-canonical :as canonical]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as xml-emit]
            [sade.date :as date]
            [sade.util :as util]))

(def kayttotapaus {:r "Uusi päätös" :tj "Uuden työnjohtajan nimeäminen"})

(defn- verdict-attachment-pred [application verdict]
  (if (foreman/foreman-app? application)
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

(defmethod permit/verdict-krysp-mapper :YA [application organization verdict lang krysp-version begin-of-link]
  (let [lupa-name-key                 (ya-mapping/resolve-lupa-name-key application)
        verdict-attachments-canonical (att-canonical/get-attachments-as-canonical application
                                                                                  organization
                                                                                  begin-of-link
                                                                                  (verdict-attachment-pred application verdict))
        verdict-link                  (att-canonical/verdict-attachment-link application organization verdict begin-of-link)
        verdict-canonical             (cond-> (canonical/verdict-canonical lang verdict application)
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
                      ;; LPK-4859: Creating/sending of agreements (sopimukset) to Matti is commented out for now. Take into use if decided otherwise.
                      #_(update-in [:YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lupakohtainenLisatietotieto] conj
                                   {:LupakohtainenLisatieto {:selitysteksti "LUVAN_TYYPPI"
                                                             :arvo          (if (ya/agreement-subtype? application) "sopimus" "lupa")}})
                      (ya-mapping/yleisetalueet-element-to-xml lupa-name-key krysp-version))}))

(defmethod permit/verdict-krysp-mapper :R [application organization verdict lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application organization begin-of-link
                                                                          (verdict-attachment-pred application
                                                                                                   verdict))
        verdict-link          (att-canonical/verdict-attachment-link application organization verdict begin-of-link)
        verdict-canonical     (-> (canonical/verdict-canonical lang verdict application)
                                  (assoc-in [:Paatos :poytakirja :liite] verdict-link))
        paatostieto-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto]
        vastuiden-alkamis-pvm (date/xml-date (get-in verdict [:data :responsibilities-start-date]))
        kayttotapaus          (or (:usage verdict)
                                  (get kayttotapaus (application->category application))
                                  (kayttotapaus :r))]
    {:attachments (mapping-common/attachment-details-from-canonical (conj attachments-canonical {:Liite verdict-link}))
     :canonical   verdict-canonical
     :attachment  verdict-link
     :paatostieto paatostieto-path
     :xml         (-> (meta-fields/enrich-with-link-permit-data application)
                      (common/application->canonical lang)
                      (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :kayttotapaus]
                                kayttotapaus)
                      (assoc-in paatostieto-path verdict-canonical)
                      (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                                attachments-canonical)
                      (add-vastuiden-alkamispvm-in-foreman-verdicts vastuiden-alkamis-pvm)
                      (r-mapping/rakennuslupa-element-to-xml krysp-version))}))

(defmethod permit/verdict-krysp-mapper :P [application organization verdict lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application organization begin-of-link
                                                                          (verdict-attachment-pred application
                                                                                                   verdict))
        verdict-link          (att-canonical/verdict-attachment-link application organization verdict begin-of-link)
        raw-verdict           (canonical/verdict-canonical lang verdict application)
        verdict               (-> raw-verdict
                                  (update :Paatos dissoc :lupamaaraykset :paatosdokumentinPvm)
                                  (assoc-in [:Paatos :poytakirja :liite] verdict-link))
        subpath               (if (util/=as-kw :suunnittelutarveratkaisu (:permitSubtype application))
                                [:suunnittelutarveasiatieto :Suunnittelutarveasia]
                                [:poikkeamisasiatieto :Poikkeamisasia])
        paatostieto-path      (concat [:Popast] subpath [:paatostieto])]

    {:attachments (mapping-common/attachment-details-from-canonical (conj attachments-canonical {:Liite verdict-link}))
     :canonical   verdict
     :attachment  verdict-link
     :paatostieto paatostieto-path
     :xml         (-> (common/application->canonical application lang)
                      (assoc-in (concat [:Popast] subpath [:luvanTunnistetiedot :LupaTunnus :muuTunnustieto :MuuTunnus])
                                {:tunnus (:id application) :sovellus "Lupapiste"})
                      (assoc-in paatostieto-path verdict)
                      (assoc-in (concat [:Popast] subpath [:liitetieto]) attachments-canonical)
                      (xml-emit/element-to-xml (p-mapping/get-mapping krysp-version)))}))
