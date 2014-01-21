(ns lupapalvelu.link-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Link permit creation and removal"
  (apply-remote-minimal)

  (let [apikey                 sonja
        municipality           sonja-muni

        ;; App 1 - with same permit type, approved
        application-1            (create-and-submit-application apikey
                                   :municipality municipality
                                   :address "Paatoskuja 13"
                                   :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id-1         (:id application-1)
        _                        (generate-documents application-1 apikey)
        _                        (command apikey :approve-application :id application-id-1 :lang "fi") => ok?

        ;; App 2 - with same permit type, verdict given
        application-2            (create-and-submit-application apikey
                                   :municipality municipality
                                   :address "Paatoskuja 13"
                                   :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id-2         (:id application-2)
        _                        (generate-documents application-2 apikey)
        _                        (command apikey :approve-application :id application-id-2 :lang "fi") => ok?
        _                        (command apikey :give-verdict :id application-id-2 :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

        ;; App 3 - with same permit type, verdict given, of operation "ya-jatkoaika"
        application-id-resp      (command apikey :create-continuation-period-permit :id application-id-2) => ok?
        application-id-3         (:id application-id-resp)
        application-3            (query-application apikey application-id-3) => truthy
        _                        (command apikey :submit-application :id application-id-3) => ok?
        _                        (generate-documents application-3 apikey)
        _                        (command apikey :approve-application :id application-id-3 :lang "fi") => ok?

        ;; App that gets a link permit attached to it
        application-4             (create-and-submit-application apikey
                                   :municipality municipality
                                   :address "Paatoskuja 13"
                                   :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id-4         (:id application-4)]


    ;; The dropdown selection component in the link-permit dialog is allowed to have only these kind of applications:
    ;;    - different id than current application has, but same permit-type
    ;;    - is in "verdictGiven"- or "constructionStarted" state
    ;;    - whose operation is not of type "ya-jatkoaika"
    ;;
    (let [matches-resp (query apikey :app-matches-for-link-permits :id application-id-4) => ok?
          matches (:app-links matches-resp)]
      (count matches) => 1
      (-> matches first :id)   => application-id-2)

    (command apikey :add-link-permit :id application-id-4 :linkPermitId application-id-2) => ok?
    (generate-documents application-4 apikey)
    (command apikey :approve-application :id application-id-4 :lang "fi") => ok?
    (command apikey :add-link-permit :id application-id-4 :linkPermitId application-id-2) => (partial expected-failure? "error.command-illegal-state")

    (let [app (query-application apikey application-id-4) => truthy]
      (-> app first :appsLinkingToUs) => nil?
      (count (:linkPermitData app)) => 1
      (let [link-permit-data (-> app :linkPermitData first)]
        (:id link-permit-data) => "LP-753-2014-00002"
        (:type link-permit-data) => "lupapistetunnus"
        (:operation link-permit-data) => "ya-katulupa-vesi-ja-viemarityot"
        ))))
