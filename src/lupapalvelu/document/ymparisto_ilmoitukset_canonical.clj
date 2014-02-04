(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]))

(defn meluilmoitus-canonical [application lang]
  {:Ilmoitukset {:toimutuksenTiedot (toimituksen-tiedot application lang)
                 :melutarina {:kasittelytietotieto (get-kasittelytieto application)
                              :luvanTunnistetiedot  (lupatunnus (:id application))
                              :lausuntotieto (get-statements (:statements application))}}}
  )