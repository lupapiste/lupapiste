(ns lupapalvelu.document.yleinen-ilmoitus-schemas
  (:require [lupapalvelu.document.schemas :refer :all]
            [lupapalvelu.document.tools :refer :all]))

(def kohde->body
  "The actual forms are (at least for now) kept as separate attachments but the body of a form cannot be empty.
  See LPK-5818 for details"
  {"elainsuojat"                              [{:name "kuvaus" :type :text :max-len 4000}]
   "sahat-varikot-elaintarhat-ja-huvipuistot" [{:name "kuvaus" :type :text :max-len 4000}]
   "kemikaalivarastot"                        [{:name "kuvaus" :type :text :max-len 4000}]
   "pienimuotoinen-koneellinen-kullankaivuu"  [{:name "kuvaus" :type :text :max-len 4000}]
   "elintarvike-ja-rehuteollisuus"            [{:name "kuvaus" :type :text :max-len 4000}]
   "vahaiset-ampumaradat"                     [{:name "kuvaus" :type :text :max-len 4000}]})

(defn yleisen-ilmoituksen-kohde [value]
  {:name       value
   :type       :group
   :group-help (format "yi-yleinen-ilmoitus.%s.groupHelpText" value)
   :show-when  {:path   "ilmoituksen-kohde"
                :values #{value}}
   :body       (get kohde->body value)})

(def yleinen-ilmoitus
  "The body for the form :yleinen-ilmoitus, divided into several sections according to the type of notice"
  (let [kohteet (keys kohde->body)]
    (->> kohteet
         (map yleisen-ilmoituksen-kohde)
         (apply body {:name     "ilmoituksen-kohde"
                      :type     :select
                      :sortBy   :displayname
                      :required true
                      :body     (mapv #(hash-map :name %) kohteet)}))))
