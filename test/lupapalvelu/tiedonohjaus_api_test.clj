(ns lupapalvelu.tiedonohjaus-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [lupapalvelu.tiedonohjaus-api :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer [execute]])
  (:import (java.util Date)
           (java.time LocalDate ZoneId)))

(testable-privates lupapalvelu.tiedonohjaus-api store-function-code update-application-child-metadata!)

(facts "about tiedonohjaus api"
  (fact "a valid function code can be stored for an operation selected by the organization"
    (store-function-code "vapaa-ajan-asuinrakennus" "10 03 00 01" {:orgAuthz {:753-R #{:authorityAdmin}}}) => {:ok true}
    (provided
      (lupapalvelu.organization/get-organization "753-R") => {:selected-operations ["vapaa-ajan-asuinrakennus"]}
      (lupapalvelu.tiedonohjaus/available-tos-functions "753-R") => [{:code "10 03 00 01"}]
      (mongo/update-by-id :organizations "753-R" {"$set" {"operations-tos-functions.vapaa-ajan-asuinrakennus" "10 03 00 01"}}) => nil))

  (fact "a function code can not be stored for an operation not selected by the organization"
    (store-function-code "vapaa-ajan-asuinrakennus" "10 03 00 01" {:orgAuthz {:753-R #{:authorityAdmin}}}) => {:ok false :text "Invalid organization or operation"}
    (provided
      (lupapalvelu.organization/get-organization "753-R") => {:selected-operations ["foobar"]}
      (lupapalvelu.tiedonohjaus/available-tos-functions "753-R") => [{:code "10 03 00 01"}]))

  (fact "an invalid function code can not be stored for an operation"
    (store-function-code "vapaa-ajan-asuinrakennus" "10 03 00 01" {:orgAuthz {:753-R #{:authorityAdmin}}}) => {:ok false :text "Invalid organization or operation"}
    (provided
      (lupapalvelu.organization/get-organization "753-R") => {:selected-operations ["vapaa-ajan-asuinrakennus"]}
      (lupapalvelu.tiedonohjaus/available-tos-functions "753-R") => [{:code "55 55 55 55"}]))

  (fact "attachment metadata is updated correctly"
    (let [command {:application {:organization "753-R"
                                 :attachments  [{:id 1 :metadata {"julkisuusluokka" "julkinen"
                                                                  "henkilotiedot"   "sisaltaa"
                                                                  "sailytysaika"    {"arkistointi" "ei"
                                                                                     "perustelu"   "foo"}
                                                                  "myyntipalvelu"   false
                                                                  "nakyvyys"        "julkinen"}}]}
                   :created     1000
                   :user        {:orgAuthz {:753-R #{:authority :archivist}}}}]
      (update-application-child-metadata!
        command
        :attachments
        1
        {"julkisuusluokka" "julkinen"
         "henkilotiedot"   "ei-sisalla"
         "sailytysaika"    {"arkistointi" "ikuisesti"
                            "perustelu"   "foo"}
         "myyntipalvelu"   false
         "nakyvyys"        "julkinen"
         "kieli"           "fi"}) => {:ok true}
      (provided
        (lupapalvelu.action/update-application command {$set {:modified 1000 :attachments [{:id 1 :metadata {:julkisuusluokka :julkinen
                                                                                                             :henkilotiedot   :ei-sisalla
                                                                                                             :sailytysaika    {:arkistointi :ikuisesti
                                                                                                                               :perustelu   "foo"}
                                                                                                             :myyntipalvelu   false
                                                                                                             :nakyvyys        :julkinen
                                                                                                             :tila            :luonnos
                                                                                                             :kieli           :fi}}]}}) => nil)))

  (fact "user with insufficient rights cannot update retention metadata"
    (let [command {:application {:organization "753-R"
                                 :attachments  [{:id 1 :metadata {"julkisuusluokka" "julkinen"
                                                                  "henkilotiedot"   "sisaltaa"
                                                                  "sailytysaika"    {"arkistointi" "ikuisesti"
                                                                                     "perustelu"   "foo"}
                                                                  "myyntipalvelu"   false
                                                                  "nakyvyys"        "julkinen"}}]}
                   :created     1000
                   :user        {:orgAuthz {:753-R #{:authority}}}}]
      (update-application-child-metadata!
        command
        :attachments
        1
        {"julkisuusluokka" "julkinen"
         "henkilotiedot"   "ei-sisalla"
         "sailytysaika"    {"arkistointi" "ei"
                            "perustelu"   "foo"}
         "myyntipalvelu"   false
         "nakyvyys"        "julkinen"
         "kieli"           "fi"}) => {:ok true}
      (provided
        (lupapalvelu.action/update-application command {$set {:modified 1000 :attachments [{:id 1 :metadata {:julkisuusluokka :julkinen
                                                                                                             :henkilotiedot   :ei-sisalla
                                                                                                             :sailytysaika    {:arkistointi :ikuisesti
                                                                                                                               :perustelu   "foo"}
                                                                                                             :myyntipalvelu   false
                                                                                                             :nakyvyys        :julkinen
                                                                                                             :tila            :luonnos

                                                                                                             :kieli           :fi}}]}}) => nil)))
  (fact "process metadata is updated correctly"
    (let [command {:application {:organization    "753-R"
                                 :processMetadata {}
                                 :id              "ABC123"
                                 :state           "submitted"}
                   :created     1000
                   :user        {:orgAuthz      {:753-R #{:authority :archivist}}
                                 :organizations ["753-R"]
                                 :role          :authority}
                   :action      "store-tos-metadata-for-process"
                   :data        {:metadata {"julkisuusluokka" "julkinen"
                                            "henkilotiedot"   "ei-sisalla"
                                            "sailytysaika"    {"arkistointi" "ikuisesti"
                                                               "perustelu"   "foo"}
                                            "kieli"           "fi"}
                                 :id       "ABC123"}}]
      (execute command) => {:ok true}
      (provided
        (lupapalvelu.action/update-application command {$set {:modified        1000
                                                              :processMetadata {:julkisuusluokka :julkinen
                                                                                :henkilotiedot   :ei-sisalla
                                                                                :sailytysaika    {:arkistointi :ikuisesti
                                                                                                  :perustelu   "foo"}
                                                                                :kieli           :fi}}}) => nil)))

  (fact "retention and security end dates are calculated when required and verdict has been given"
    (let [metadata {"julkisuusluokka" "salainen"
                    "henkilotiedot"   "sisaltaa"
                    "sailytysaika"    {"arkistointi" "m\u00E4\u00E4r\u00E4ajan"
                                       "pituus" 10
                                       "perustelu"   "foo"}
                    "myyntipalvelu"   false
                    "nakyvyys"        "julkinen"
                    "salassapitoaika" 5
                    "suojaustaso"     "ei-luokiteltu"
                    "kayttajaryhma"   "viranomaisryhma"
                    "kayttajaryhmakuvaus" "muokkausoikeus"
                    "kieli" "fi"
                    "turvallisuusluokka" "ei-turvallisuusluokkaluokiteltu"
                    "salassapitoperuste" "peruste"}
          command {:application {:organization "753-R"
                                 :attachments  [{:id 1 :metadata metadata}]
                                 ;; Verdict date 2016-1-29
                                 :verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1456696800000}]}]}]}
                   :created     1000
                   :user        {:orgAuthz {:753-R #{:authority :archivist}}}}]
      (update-application-child-metadata!
        command
        :attachments
        1
        metadata) => {:ok true}
      (provided
        (lupapalvelu.action/update-application command
                                               {"$set" {:modified 1000, :attachments [{:id 1
                                                                                       :metadata {:tila :luonnos
                                                                                                  :salassapitoaika 5
                                                                                                  :nakyvyys :julkinen
                                                                                                  :sailytysaika {:arkistointi (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                                                 :pituus 10
                                                                                                                 :perustelu "foo"
                                                                                                                 :retention-period-end #inst "2026-02-28T22:00:00.000-00:00"}
                                                                                                  :myyntipalvelu false
                                                                                                  :suojaustaso :ei-luokiteltu
                                                                                                  :security-period-end #inst "2021-02-28T22:00:00.000-00:00"
                                                                                                  :kayttajaryhma :viranomaisryhma
                                                                                                  :kieli :fi
                                                                                                  :turvallisuusluokka :ei-turvallisuusluokkaluokiteltu
                                                                                                  :salassapitoperuste "peruste"
                                                                                                  :henkilotiedot :sisaltaa
                                                                                                  :julkisuusluokka :salainen
                                                                                                  :kayttajaryhmakuvaus :muokkausoikeus}}]}}) => nil)))

  (fact "retention and security end dates are not set when they are not required"
    (let [metadata {"julkisuusluokka" "julkinen"
                    "henkilotiedot"   "sisaltaa"
                    "sailytysaika"    {"arkistointi" "ikuisesti"
                                       "perustelu"   "foo"}
                    "myyntipalvelu"   false
                    "nakyvyys"        "julkinen"
                    "kieli" "fi"}
          command {:application {:organization "753-R"
                                 :attachments  [{:id 1 :metadata metadata}]
                                 ;; Verdict date 2016-1-29
                                 :verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1456696800000}]}]}]}
                   :created     1000
                   :user        {:orgAuthz {:753-R #{:authority :archivist}}}}]
      (update-application-child-metadata!
        command
        :attachments
        1
        metadata) => {:ok true}
      (provided
        (lupapalvelu.action/update-application command
                                               {"$set" {:modified 1000, :attachments [{:id 1
                                                                                       :metadata {:julkisuusluokka :julkinen
                                                                                                  :tila :luonnos
                                                                                                  :nakyvyys :julkinen
                                                                                                  :sailytysaika {:arkistointi :ikuisesti
                                                                                                                 :perustelu "foo"}
                                                                                                  :myyntipalvelu false
                                                                                                  :kieli :fi
                                                                                                  :henkilotiedot :sisaltaa}}]}}) => nil))))
