(ns lupapalvelu.tiedonohjaus-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.tiedonohjaus-api :refer :all]
            [lupapalvelu.mongo :as mongo]))

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
                                 :attachments [{:id 1 :metadata {"julkisuusluokka" "julkinen"
                                                                 "henkilotiedot" "sisaltaa"
                                                                 "sailytysaika" {"arkistointi" "ei"
                                                                                "perustelu" "foo"}
                                                                 "myyntipalvelu" false
                                                                 "näkyvyys" "julkinen"}}]}
                   :created 1000
                   :user {:orgAuthz {:753-R #{:authority :archivist}}}}]
      (update-application-child-metadata!
        command
        :attachments
        1
        {"julkisuusluokka" "julkinen"
         "henkilotiedot" "ei-sisalla"
         "sailytysaika" {"arkistointi" "ikuisesti"
                         "perustelu" "foo"}
         "myyntipalvelu" false
         "näkyvyys" "julkinen"}) => nil
      (provided
        (lupapalvelu.action/update-application command {$set {:modified 1000 :attachments [{:id 1 :metadata {:julkisuusluokka :julkinen
                                                                                                             :henkilotiedot :ei-sisalla
                                                                                                             :sailytysaika {:arkistointi :ikuisesti
                                                                                                                            :perustelu "foo"}
                                                                                                             :myyntipalvelu false
                                                                                                             :näkyvyys :julkinen
                                                                                                             :tila "Valmis"}}]}}) => nil)))

  (fact "user with insufficient rights cannot update retention metadata"
    (let [command {:application {:organization "753-R"
                                 :attachments [{:id 1 :metadata {"julkisuusluokka" "julkinen"
                                                                 "henkilotiedot" "sisaltaa"
                                                                 "sailytysaika" {"arkistointi" "ikuisesti"
                                                                                 "perustelu" "foo"}
                                                                 "myyntipalvelu" false
                                                                 "näkyvyys" "julkinen"}}]}
                   :created 1000
                   :user {:orgAuthz {:753-R #{:authority}}}}]
      (update-application-child-metadata!
        command
        :attachments
        1
        {"julkisuusluokka" "julkinen"
         "henkilotiedot" "ei-sisalla"
         "sailytysaika" {"arkistointi" "ei"
                         "perustelu" "foo"}
         "myyntipalvelu" false
         "näkyvyys" "julkinen"}) => nil
      (provided
        (lupapalvelu.action/update-application command {$set {:modified 1000 :attachments [{:id 1 :metadata {:julkisuusluokka :julkinen
                                                                                                             :henkilotiedot :ei-sisalla
                                                                                                             :sailytysaika {:arkistointi :ikuisesti
                                                                                                                            :perustelu "foo"}
                                                                                                             :myyntipalvelu false
                                                                                                             :näkyvyys :julkinen
                                                                                                             :tila "Valmis"}}]}}) => nil))))
