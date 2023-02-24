(ns lupapalvelu.reports.archival-test
  (:require [lupapalvelu.reports.archival :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]))

(testable-privates lupapalvelu.reports.archival
                   parse-command)

(def runeberg (date/timestamp "5.2.2021"))


(facts "add-application-options"
  (against-background
    (lupapalvelu.tiedonohjaus/tos-function-with-name "12 34 56" "753-R")
    => {:name "Cool"})
  (let [app-r           {:id               "LP-12345"
                         :tosFunction      "12 34 56"
                         :organization     "753-R"
                         :archived         {:completed runeberg}
                         :primaryOperation {:name "pientalo"}
                         :state            "closed"
                         :permitType       "R"
                         :propertyId       "75342300020057"
                         :creator          {:firstName "Bob"
                                            :lastName  "Builder"}
                         :history          (shuffle [{:state "draft" :ts 100}
                                                     {:foo "bar" :ts 110}
                                                     {:state "open" :ts 200}
                                                     {:hii 10 :ts 220}
                                                     {:state "submitted" :ts 300}
                                                     {:state "verdictGiven" :ts 400}
                                                     {:state "constructionStarted" :ts 500}
                                                     {:state "inUse" :ts 600}
                                                     {:state "closed" :ts 700}
                                                     {:state "archived" :ts 10000
                                                      :user  {:firstName "Annie"
                                                              :lastName  "Archivist"}}])}
        {:keys [ts->state]
         :as   options} (add-application-options {:lang "fi" :organization-id "753-R"}
                                                 app-r)]

    (fact "Permit type is R"
      options => (just {:lang                "fi"
                        :organization-id     "753-R"
                        :ts->state           fn?
                        :tos-function        "Cool"
                        :primary-operation   "Asuinpientalon rakentaminen (enintään kaksiasuntoinen erillispientalo)"
                        :archiving-completed runeberg
                        :application-id      "LP-12345"
                        :backend-id          nil
                        :property-id         "753-423-2-57"
                        :application-state   "Valmistunut"}))

    (fact "With backend id"
      (add-application-options {:lang "fi" :organization-id "753-R"}
                               (assoc app-r :verdicts [{:kuntalupatunnus "99-88 R"}]))
      => (just (assoc options
                      :backend-id "99-88 R"
                      :ts->state fn?)))

    (facts "ts->state"
      (ts->state nil) => nil
      (ts->state 1) => nil
      (ts->state 100) => "Luonnos"
      (ts->state 150) => "Luonnos"
      (ts->state 250) => "Näkyy viranomaiselle"
      (ts->state 99999999999999) => "Arkistoitu")

    (fact "Digitized application"
      (add-application-options {:lang "fi" :organization-id "753-R"}
                               (assoc app-r :permitType "ARK"))
      => (contains {:archiver  "Annie Archivist"
                    :digitizer "Bob Builder"}))

    (facts "add-attachment-options, not archived"
      (add-attachment-options options
                              {:latestVersion {:filename "blueprint.pdf"}
                               :contents      "Grand design"
                               :type          {:type-group "paapiirustus"
                                               :type-id    "pohjapiirustus"}
                               :metadata      {:tila "valmis"}
                               :modified      444})
      => (merge options {:contents        "Grand design"
                         :filename        "blueprint.pdf"
                         :attachment-type "Pohjapiirustus"
                         :archived?       "Ei"
                         :modified        444
                         :modified-state  "Päätös annettu"}))

    (facts "add-attachment-options, archived"
      (add-attachment-options options
                              {:latestVersion {:filename "resume.pdf"}
                               :contents      "Curriculum vitae"
                               :type          {:type-group "osapuolet"
                                               :type-id    "cv"}
                               :metadata      {:tila "arkistoitu"}
                               :modified      555})
      => (merge options {:contents        "Curriculum vitae"
                         :filename        "resume.pdf"
                         :attachment-type "CV"
                         :archived?       "Kyllä"
                         :modified        555
                         :modified-state  "Rakennustyöt aloitettu"}))))

(facts "parse-command"
  (parse-command {:data {:organizationId "FOO-R" :startTs "1200" :endTs "2400"}})
  => {:organization-id "FOO-R" :start-ts 1200 :end-ts 2400 :lang :fi}
  (parse-command {:data {:organizationId "FOO-R" :startTs "1200" :endTs "2400"}
                  :lang "cn"})
  => {:organization-id "FOO-R" :start-ts 1200 :end-ts 2400 :lang :fi}
  (parse-command {:data {:organizationId "FOO-R" :startTs "1200" :endTs "2400"}
                  :lang "sv"})
  => {:organization-id "FOO-R" :start-ts 1200 :end-ts 2400 :lang :sv})
