(ns lupapalvelu.document.rekisterointi-ilmoitus-schemas
  (:require [lupapalvelu.document.schemas :refer :all]
            [lupapalvelu.document.tools :refer :all]))

(defn ->rekisterointi-ilmoituksen-kohde
  "The actual forms are kept as separate attachments but the body of a form cannot be empty.
   See LPK-6245 for details. Rekisterointi-ilmoitus is similar to yleinen-ilmoitus."
  [value]
  {:name       value
   :type       :group
   :group-help (format "rekisterointi-ilmoitus.%s.group-help-text" value)
   :show-when  {:path   "ilmoituksen-kohde"
                :values #{value}}
   :body       [{:name "kuvaus" :type :text :max-len 4000}]})

(def rekisterointi-ilmoitus
  "The body for the form :yleinen-ilmoitus, divided into several sections according to the type of notice"
  (let [kohteet ["asfalttiasema"
                 "betoniasema-tai-betonituotetehdas"
                 "keskisuuri-energiantuotantolaitos"
                 "polttonesteiden-jakeluasema"
                 "orgaanisia-liuottimia-kayttava-toiminta" ]]
    (->> kohteet
         (map ->rekisterointi-ilmoituksen-kohde)
         (apply body {:name     "ilmoituksen-kohde"
                      :type     :select
                      :size     :l
                      :sortBy   :displayname
                      :required true
                      :body     (mapv (partial assoc {} :name) kohteet)}))))
