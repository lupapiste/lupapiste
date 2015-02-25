(ns lupapalvelu.xml.asianhallinta.asianhallinta-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(fl/facts* "Asianhallinta itest"
  (facts "UusiAsia from application"
    (let [app-id (create-app-id
                    pena
                    :municipality velho-muni
                    :operation "poikkeamis"
                    :propertyId "29703401070010"
                    :y 6965051.2333374 :x 535179.5
                    :address "Suusaarenkierto 44") => truthy])))
