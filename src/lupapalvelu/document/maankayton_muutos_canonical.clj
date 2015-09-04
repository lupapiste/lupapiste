(ns lupapalvelu.document.maankayton-muutos-canonical
  (:require [lupapalvelu.document.tools :as tools ]
            [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.permit :as permit]))

(defn maankayton-muutos-canonical [application lang]
  (let [documents  (tools/unwrapped (canonical-common/documents))
        kuvaus     (-> documents :maankayton-muutos-kuvaus first :data :kuvaus)
        hakija-key (keyword (permit/get-applicant-doc-schema (permit/permit-type application)))]
    {:Maankaytonmuutos
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}}))
