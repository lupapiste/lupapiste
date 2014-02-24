(ns lupapalvelu.document.maa-aines-canonical
  (require [sade.util :as util]
           [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
           [lupapalvelu.document.tools :as tools]           ))

(defn maa-aines-canonical [application lang]
  (let [documents (tools/unwrapped (canonical-common/documents-by-type-without-blanks application))
        kuvaus    nil]
    {}))
