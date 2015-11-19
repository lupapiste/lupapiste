(ns lupapalvelu.document.document-field-validators
  (:require [clojure.string :as s]
            [lupapalvelu.document.validator :refer :all]))

;; Huom!
;;  Toisteisille kentille :fieldsiin pitaa antaa path muodossa [:rakennuksenOmistajat :0 :omistajalaji] eli numero mukaan.
;;

(defvalidator :rakennusvalitsin-muu
  {:doc "Jos rakennusvalitsimesta on valittu vaihtoehto Muu, on Manuaalinen rakennusnumero -kenttaan syotettava arvo"
   :schemas ["rakennuksen-muuttaminen"
             "rakennuksen-muuttaminen-ei-huoneistoja"
             "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
             "rakennuksen-laajentaminen"
             "rakennuksen-laajentaminen-ei-huoneistoja"
             "purkaminen"]
   :fields [buildingId [:buildingId]
            muu        [:manuaalinen_rakennusnro]]
   :facts {:ok   [["abc"   "building-num"]
                  ["other" "building-num"]]
           :fail [["other" nil]
                  ["other" ""]
                  ["other" "    "]]}}
  (and (= "other" buildingId) (s/blank? muu)))
