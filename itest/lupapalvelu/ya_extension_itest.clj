(ns lupapalvelu.ya-extension-itest
  (:require [midje.sweet :refer :all]
            [sade.core :as sade]
            [sade.xml :as xml]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.ya-extension :as yax]
            [clj-time.core :as time]
            [clj-time.format :as fmt]))

(apply-remote-minimal)

(defn err [msg]
  (partial expected-failure? msg))

(def today (fmt/unparse-local (fmt/formatter-local "dd.MM.yyyy") (time/today)))

(facts "YA extension applications"
       (let [r-id (create-app-id pena
                                 :propertyId sipoo-property-id
                                 :operation "pientalo")
             ya-id (create-app-id pena
                                  :propertyId sipoo-property-id
                                  :operation "ya-katulupa-vesi-ja-viemarityot")
             link-id (create-app-id pena
                                    :propertyId sipoo-property-id
                                    :operation "ya-katulupa-vesi-ja-viemarityot")]
         (fact "Submit"
               (command pena :submit-application :id r-id) => ok?
               (command pena :submit-application :id ya-id) => ok?)
         (fact "Extension pseudo query fails"
               (query sonja :approve-ya-extension :id ya-id) => (err :error.unsupported-primary-operation)
               (query sonja :approve-ya-extension :id r-id) => (err :error.unsupported-primary-operation))
         (fact "Create and invite statement givers for YA application"
               (command sipoo-ya :create-statement-giver
                        :email "ronja.sibbo@sipoo.fi"
                        :text "Ronja") => ok?
               (command sonja :request-for-statement
                        :id ya-id
                        :functionCode nil
                        :selectedPersons [{:email "teppo@example.com"
                                           :text "User statement giver"
                                           :name "Teppo"}
                                          {:email "ronja.sibbo@sipoo.fi"
                                           :text "Authority statement giver"
                                           :name "Ronja"}]) => ok?)
         (fact "Fetch verdicts"
               (command sonja :check-for-verdict :id r-id) => ok?
               (command sonja :check-for-verdict :id ya-id) => ok?)
         (fact "No extensions for R application"
               (query pena :ya-extensions :id r-id) => fail?)
         (fact "No extensions yet established"
               (query pena :ya-extensions :id ya-id)
               => (err :error.no-extensions))
         (fact "Add non-extension link permit. Still no extensions."
               (command pena :add-link-permit :id ya-id :linkPermitId link-id) => ok?
               (query pena :ya-extensions :id ya-id)
               => (err :error.no-extensions))
         (fact "Create continuation period permit"
               (let [ext-id (:id (command pena :create-continuation-period-permit :id ya-id))]
                 (query pena :ya-extensions :id ya-id)
                 => (contains {:extensions [{:id ext-id
                                             :startDate today
                                             :endDate nil
                                             :state "draft"}]})
                 (fact "Edit extension and check again"
                       (let [doc-id (-> (query-application pena ext-id)
                                        (domain/get-document-by-name :tyo-aika-for-jatkoaika)
                                        :id)]
                         (command pena :update-doc :id ext-id :collection "documents"
                                  :doc doc-id :updates [[:tyoaika-alkaa-pvm "20.09.2016"]
                                                        [:tyoaika-paattyy-pvm "10.10.2016"]])=> ok?
                         (command pena :add-comment :id ext-id :text "Zao!"
                                  :roles ["applicant" "authority"]
                                  :target {:type "application"}
                                  :openApplication true) => ok?
                         (query pena :ya-extensions :id ya-id)
                         => (contains {:extensions [{:id ext-id
                                                     :startDate "20.09.2016"
                                                     :endDate "10.10.2016"
                                                     :state "open"}]})))
                 (fact "Reader can call query"
                       (command sipoo-ya :update-user-organization
                                :email "luukas.lukija@sipoo.fi"
                                :firstName "Luukas" :lastName "Lukija"
                                :roles ["reader"]) => ok?
                       (query luukas :ya-extensions :id ya-id) => ok?)
                 (fact "Statement giver authority can call query"
                       (query ronja :ya-extensions :id ya-id) => ok?)
                 (fact "Statement giver can call query"
                       (query teppo :ya-extensions :id ya-id) => ok?)
                 (fact "Submit extension application"
                       (command pena :submit-application :id ext-id) => ok?)
                 (fact "Approve extension pseudo query"
                       (query sonja :approve-ya-extension :id ext-id) => ok?)))))

(def local-db-name (str "test_ya_extension_itest_" (sade/now)))

(mongo/connect!)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

(facts "Application and link permit extensions"
       (mongo/with-db local-db-name
         (let [{app-id :id} (create-and-submit-local-application pena
                                                                 :propertyId sipoo-property-id
                                                                 :operation "ya-katulupa-vesi-ja-viemarityot")
               krysp (-> "resources/krysp/dev/jatkoaika-ya.xml"
                         xml/parse
                         strip-xml-namespaces)]

           (fact "Fetch verdict"
                 (local-command sonja :check-for-verdict :id app-id) => ok?)
           (fact "No extensions"
                 (local-query pena :ya-extensions :id app-id)
                 => (err :error.no-extensions))
           (fact "Update application extension from KRYSP"
                 (yax/update-application-extensions krysp)
                 (-> (local-query pena :application :id app-id) :application :jatkoaika)
                 => (contains {:alkuPvm "04.10.2016"
                               :loppuPvm "22.11.2016"
                               :perustelu "Jatkoajan perustelu"}))
           (fact "Extensions only include application extension"
                 (local-query pena :ya-extensions :id app-id)
                 => (contains {:extensions  [{:startDate "04.10.2016"
                                              :endDate "22.11.2016"
                                              :reason "Jatkoajan perustelu"}]}))
           (defn link-permit [start-date end-date]
             (let [ext-id (:id (local-command pena :create-continuation-period-permit :id app-id))
                   doc-id (-> (local-query pena :application :id ext-id)
                              :application
                              (domain/get-document-by-name :tyo-aika-for-jatkoaika)
                              :id)]
               (mongo/update :applications
                             {:_id ext-id
                              :documents {$elemMatch {:id doc-id}}}
                             {$set {:documents.$.data.tyoaika-alkaa-pvm.value start-date
                                    :documents.$.data.tyoaika-paattyy-pvm.value end-date
                                    }})
               ext-id))
           (fact "Extension link permits"
                 (let [ext-id1 (link-permit "7.7.2016" "8.8.2016")]
                   (:extensions (local-query pena :ya-extensions :id app-id))
                   => [{:startDate "7.7.2016"
                        :endDate "8.8.2016"
                        :id ext-id1
                        :state "draft"}
                       {:startDate "04.10.2016"
                        :endDate "22.11.2016"
                        :reason "Jatkoajan perustelu"}])))))
