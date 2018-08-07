(ns lupapalvelu.xml.krysp.verdict-mapping
  (:require [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.document.attachments-canonical :as att-canonical]
            [lupapalvelu.pate.verdict-canonical :as canonical]
            [lupapalvelu.document.rakennuslupa-canonical :as r-canonical]
            [lupapalvelu.document.poikkeamis-canonical :as p-canoncial]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as xml-emit]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as r-mapping]
            [lupapalvelu.xml.krysp.poikkeamis-mapping :as p-mapping]))

(def kayttotapaus "Uusi paatos")

(defmethod permit/verdict-krysp-mapper :R [application verdict lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application begin-of-link (comp #{(:id verdict)} :id :target))
        verdict-canonical (canonical/verdict-canonical lang verdict)
        verdict-link (att-canonical/pate-verdict-attachment-link application verdict)
        kayttotapaus (or (:usage verdict) kayttotapaus)]
    {:attachments (mapping-common/attachment-details-from-canonical attachments-canonical)
     :xml (-> (meta-fields/enrich-with-link-permit-data application)
              (r-canonical/application-to-canonical lang)
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :kayttotapaus]
                        kayttotapaus)
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto]
                        verdict-canonical)
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto :Paatos :poytakirja :liite]
                        verdict-link)
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                        attachments-canonical)
              (xml-emit/element-to-xml (r-mapping/get-rakennuslupa-mapping krysp-version)))}))

(defmethod permit/verdict-krysp-mapper :P [application verdict lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application begin-of-link (comp #{(:id verdict)} :id :target))
        verdict-link          (att-canonical/pate-verdict-attachment-link application verdict)
        raw-verdict           (canonical/verdict-canonical lang verdict)
        verdict               (assoc raw-verdict :Paatos (dissoc (:Paatos raw-verdict) :lupamaaraykset))]
    {:attachment attachments-canonical
     :xml        (-> (p-canoncial/poikkeus-application-to-canonical application lang)
                     (assoc-in [:Popast :poikkeamisasiatieto :Poikkeamisasia :paatostieto] verdict)
                     (assoc-in [:Popast :poikkeamisasiatieto :Poikkeamisasia :paatostieto :Paatos :poytakirja :liite]
                               verdict-link)
                     (xml-emit/element-to-xml (p-mapping/get-mapping krysp-version)))}))
