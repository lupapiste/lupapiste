(ns lupapalvelu.document.asianhallinta_canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]
           [clojure.string :as s]))


;; Uusi asia element

(def uusi-asia {:UusiAsia
                {:Tyyppi nil
                 :Kuvaus nil
                 :Kuntanumero nil
                 :Hakijat nil
                 :Maksaja nil
                 :HakemusTunnus nil
                 :VireilletuloPvm nil
                 :Liitteet nil
                 :Asiointikieli nil
                 :Toimenpiteet nil
                 :Viiteluvat {:Viitelupa nil}}})

(defn application-to-asianhallinta-canonical [application lang])

