(ns lupapalvelu.document.parties-canonical
  (:require [sade.core :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.rakennuslupa-canonical :as rl-canonical]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]))

(defmulti party-canonical-info
  {:arglists '([party-doc])}
  doc-tools/doc-subtype)

(defmethod party-canonical-info :hakija
  [{doc-data :data {party-type :name doc-subtype :subtype} :schema-info :as party-doc}]
  {:osapuolitieto {:Osapuoli (canonical-common/get-osapuoli-data doc-data party-type doc-subtype)}})

(defmethod party-canonical-info :suunnittelija
  [{doc-data :data {party-type :name doc-subtype :subtype} :schema-info :as party-doc}]
  {:suunnittelijatieto {:Suunnittelija (canonical-common/get-suunnittelija-data doc-data party-type doc-subtype)}})

(defn- party-doc-to-canonical [application lang party-doc]
  {:Rakennusvalvonta
   {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
    :rakennusvalvontaAsiatieto
    {:RakennusvalvontaAsia
     {:kasittelynTilatieto (canonical-common/get-state application)
      :luvanTunnisteTiedot (canonical-common/lupatunnus (:id party-doc) (:submitted application) nil)
      :viitelupatieto (canonical-common/lupatunnus application)
      :osapuolettieto {:Osapuolet (party-canonical-info party-doc)}
      :kayttotapaus "Uuden suunnittelijan nime\u00e4minen"
      :asianTiedot {:Asiantiedot {:rakennusvalvontaasianKuvaus ""}}
      :lisatiedot (rl-canonical/get-lisatiedot lang)}}}})

(defn- write-party-krysp [application lang krysp-version output-dir {doc-id :id :as party-doc}]
  (as-> party-doc $
    (party-doc-to-canonical application lang $)
    (rl-mapping/rakennuslupa-element-to-xml $ krysp-version)
    (writer/write-to-disk application nil $ krysp-version output-dir nil nil doc-id)))

(defmethod permit/parties-krysp-mapper :R [application doc-subtype lang krysp-version output-dir]
  (let [application (doc-tools/unwrapped application)]
    (->> (domain/get-documents-by-subtype (:documents application) doc-subtype)
         (run! (partial write-party-krysp application lang krysp-version output-dir)))
    true))
