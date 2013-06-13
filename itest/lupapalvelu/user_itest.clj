(ns lupapalvelu.user-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(fact "changing user info"
  (apply-remote-minimal)
  (let [resp (query teppo :user)]
    resp => ok?
    resp => (contains
              {:user
               (just
                 {:city "Tampere"
                  :email "teppo@example.com"
                  :enabled true
                  :firstName "Teppo"
                  :id "5073c0a1c2e6c470aef589a5"
                  :lastName "Nieminen"
                  :personId "210281-0001"
                  :phone "0505503171"
                  :postalCode "33200"
                  :role "applicant"
                  :street "Mutakatu 7"
                  :username "teppo@example.com"
                  :zip "33560"})}))

  (command teppo :save-user-info
    :firstName "Seppo"
    :lastName "Sieninen"
    :street "Sutakatu 7"
    :city "Sampere"
    :zip "33200"
    :phone "0505503171") => ok?

  (query teppo :user) => (contains
                           {:user
                            (contains
                              {:firstName "Seppo"
                               :lastName "Sieninen"
                               :street "Sutakatu 7"
                               :city "Sampere"
                               :zip "33200"
                               :phone "0505503171"})}))
