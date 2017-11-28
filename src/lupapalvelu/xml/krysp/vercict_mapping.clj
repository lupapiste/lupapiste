(ns lupapalvelu.xml.krysp.vercict-mapping
  (:require [lupapalvelu.document.attachments-canonical :as att-canonical]
            [lupapalvelu.pate.verdict-canonical :as canonical]
            [lupapalvelu.document.rakennuslupa-canonical :as r-canonical]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as xml-emit]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as r-mapping]))

(def kayttotapaus "Uusi paatos")

(defmethod permit/verdict-krysp-mapper :R [application verdict user lang krysp-version begin-of-link]
  (let [attachments-canonical (att-canonical/get-attachments-as-canonical application begin-of-link (comp #{(:id verdict)} :id :target))]

    {:attachments (mapping-common/attachment-details-from-canonical attachments-canonical)
     :xml (-> (r-canonical/application-to-canonical application lang)
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :kayttotapaus]
                        kayttotapaus)
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto]
                        (canonical/verdict-canonical application lang verdict))
              #_(assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :paatostieto :Paatos :poytakirja :liite]
                          (:Liite verdict-attachment-canonical)) ; TODO: generate verdict pdf
              (assoc-in [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :liitetieto]
                        attachments-canonical)
              (xml-emit/element-to-xml (r-mapping/get-rakennuslupa-mapping krysp-version)))}))
