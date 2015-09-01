(ns lupapalvelu.document.document-field-validators
  (:require [clojure.string :as s]
            [lupapalvelu.document.validator :refer :all]))


(defvalidator :rakennusvalitsin-muu
  {:doc "Jos rakennusvalitsimesta on valittu vaihtoehto Muu, on Manuaalinen rakennusnumero -kenttaan syotettava arvo"
   :schemas ["rakennuksen-muuttaminen"]
   :fields [buildingId [:buildingId]
            muu        [:manuaalinen_rakennusnro]]
   :facts {:ok   []
           :fail [["other" nil]
                  ["other" ""]
                  ["other" "    "]]}}
  (and (= "other" buildingId) (s/blank? muu)))
