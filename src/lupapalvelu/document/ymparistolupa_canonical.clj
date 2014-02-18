(ns lupapalvelu.document.ymparistolupa-canonical
  (require [lupapalvelu.document.canonical-common :as canonical-common]
           [lupapalvelu.document.tools :as tools]
           ))

(defn ymparistolupa-canonical [application lang]

  {:Ymparistoluvat {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
                    :ymparistolupatieto
                     {:Ymparistolupa
                      {:kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
                       :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))}}

                    }})

