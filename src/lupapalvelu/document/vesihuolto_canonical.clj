(ns lupapalvelu.document.vesihuolto-canonical
  (require [lupapalvelu.document.vesihuolto-schemas :as vh-schemas]
           [lupapalvelu.document.canonical-common :refer :all]))

(defn vapautus-canonical [application lang]
  {:Vesihuoltolaki
   {:toimituksenTiedot :d}}
  )