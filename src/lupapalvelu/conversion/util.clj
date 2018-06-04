(ns lupapalvelu.conversion.util
  (:require [sade.strings :as ss]))

(defn db-format->permit-id
  "Viitelupien tunnukset on Factassa tallennettu 'tietokantaformaatissa', josta ne on tunnuksella
  hakemista varten muunnettava yleiseen formaattiin.
  Esimerkki: 12-0477-A 63 -> 63-0447-12-A"
  [id]
  (let [parts (zipmap '(:vuosi :no :tyyppi :kauposa) (ss/split id #"[- ]"))]
    (ss/join "-" ((juxt :kauposa :no :vuosi :tyyppi) parts))))
