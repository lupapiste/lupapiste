(ns lupapalvelu.document.ymparistolupa-canonical
  (require [lupapalvelu.document.canonical-common :as canonical-common]
           [lupapalvelu.document.tools :as tools]
           ))

(defn ymparistolupa-canonical [application lang]
  (let [documents (canonical-common/documents-by-type-without-blanks application)
        kuvaus    (-> documents :yl-hankkeen-kuvaus first :data tools/unwrapped)]
    {:Ymparistoluvat {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
                     :ymparistolupatieto
                      {:Ymparistolupa
                       {:kasittelytietotieto (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
                        :luvanTunnistetiedot (canonical-common/lupatunnus (:id application))
                        :lausuntotieto (canonical-common/get-statements (:statements application))
                        ;:hakija nil
                        :toiminta (select-keys kuvaus [:kuvaus :peruste])
                        ; TODO:
                        ; - viiteluvat
                        ; - maksaja, kun saadaa skeemaan paikka
                        }}

                     }}))

