(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]))

(defn meluilmoitus-canonical [application lang]
  {:Ilmoitukset {:toimutuksenTiedot (toimituksen-tiedot application lang)}
   }
  )