(ns lupapalvelu.integrations.statement-canonical
  (:require [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.user :as usr]))


(defn statement-as-canonical [requester statement lang]
  (util/strip-nils
    {:LausuntoTunnus (get statement :id)
     :Saateteksti (get statement :saateText)
     :Pyytaja     (ss/trim
                    (str (get-in requester [:organization (keyword lang)])
                         " (" (usr/full-name requester) ")"))
     :PyyntoPvm   (util/to-xml-date (get statement :requested))
     :Maaraaika   (util/to-xml-date (get statement :dueDate))
     :AsianTunnus nil                                         ; TODO where is this saved, when we receive statement response?
     :Lausunnonantaja (ss/trim
                        (str (get-in statement [:person :name])
                             " / " (get-in statement [:person :text])))
     :LausuntoPvm (util/to-xml-date (get statement :given))
     :Puolto      (get statement :status)
     :LausuntoTeksti (get statement :text)}))
