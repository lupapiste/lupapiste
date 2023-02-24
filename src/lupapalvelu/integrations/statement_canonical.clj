(ns lupapalvelu.integrations.statement-canonical
  (:require [lupapalvelu.user :as usr]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn statement-as-canonical [requester statement lang]
  (util/strip-nils
    {:LausuntoTunnus (get statement :id)
     :Saateteksti (get statement :saateText)
     :Pyytaja     (ss/trim
                    (str (get-in requester [:organization :name (keyword lang)])
                         " (" (usr/full-name requester) ")"))
     :PyyntoPvm   (date/xml-date (get statement :requested))
     :Maaraaika   (date/xml-date (get statement :dueDate))
     :AsianTunnus nil                                         ; TODO where is this saved, when we receive statement response?
     :Lausunnonantaja (ss/trim
                        (str (get-in statement [:person :name])
                             " / " (get-in statement [:person :text])))
     :LausuntoPvm (date/xml-date (get statement :given))
     :Puolto      (get statement :status)
     :LausuntoTeksti (get statement :text)}))
