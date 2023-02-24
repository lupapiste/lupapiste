(ns lupapalvelu.ya-extension-itest
  (:require [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clj-time.local :as local]
            [clojure.java.io :as io]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.pate.verdict :as pate]
            [lupapalvelu.ya-extension :as yax]
            [lupapalvelu.ya-extension-api]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.core :as sade :refer [def-]]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(def- sipoo-YA-org-id "753-YA")

(apply-remote-minimal)

(defn err [msg]
  (partial expected-failure? msg))

(def today (fmt/unparse-local (fmt/formatter-local "dd.MM.yyyy") (time/today)))

(defn query-doc-id [apikey app-id doc-name]
  (-> (query-application apikey app-id)
      (domain/get-document-by-name doc-name)
      :id))

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
      (command sipoo-ya :create-statement-giver :organizationId sipoo-YA-org-id
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
      (command ronja :check-for-verdict :id r-id) => ok?
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
    (facts "Continuation period permit"
      (fact "Not supported by organization"
        (command pena :create-continuation-period-permit :id ya-id)
        => (err :error.jatkolupa-not-selected-for-organization))
      (fact "Add support to the organization"
        (command sipoo-ya :set-organization-selected-operations :organizationId sipoo-YA-org-id
                 :operations [:ya-katulupa-vesi-ja-viemarityot
                              :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
                              :ya-kayttolupa-mainostus-ja-viitoitus
                              :ya-kayttolupa-terassit
                              :ya-kayttolupa-vaihtolavat
                              :ya-kayttolupa-nostotyot
                              :ya-jatkoaika]) => ok?)
      (fact "Create continuation period permit"
        (let [ext-id (:id (command pena :create-continuation-period-permit :id ya-id))]
          (query pena :ya-extensions :id ya-id)
          => (contains {:extensions [{:id ext-id
                                      :startDate today
                                      :endDate nil
                                      :state "draft"}]})
          (fact "Edit extension and check again"
            (let [doc-id1 (query-doc-id pena ext-id :hankkeen-kuvaus-jatkoaika)
                  doc-id2 (query-doc-id pena ext-id :tyo-aika-for-jatkoaika)
                  doc-id3 (query-doc-id pena ext-id :hakija-ya)
                  doc-id4 (query-doc-id pena ext-id :yleiset-alueet-maksaja)]
              (command pena :update-doc :id ext-id
                       :doc doc-id1 :updates [[:kuvaus "Extension request"]])=> ok?
              (command pena :update-doc :id ext-id
                       :doc doc-id2 :updates [[:tyoaika-alkaa-pvm "20.09.2016"]
                                              [:tyoaika-paattyy-pvm "10.10.2016"]])=> ok?
              (command pena :update-doc :id ext-id
                       :doc doc-id3 :updates [[:_selected "henkilo"]])
              (command pena :set-user-to-document :id ext-id
                       :documentId doc-id3 :path "henkilo" :userId pena-id) => ok?
              (command pena :update-doc :id ext-id
                       :doc doc-id4 :updates [[:_selected "henkilo"]])
              (command pena :set-user-to-document :id ext-id
                       :documentId doc-id4 :path "henkilo" :userId pena-id) => ok?
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
            (command sipoo-ya :upsert-organization-user :organizationId sipoo-YA-org-id
                     :organizationId "753-YA"
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
          (fact "No verdict tab for extension permit"
            (query pena :pate-verdict-tab :id ext-id)
            => (err :ya-extension-application))
          (fact "No contract tab for extension permit"
            (query pena :pate-contract-tab :id ext-id)
            => (err :error.verdict.not-contract))
          (fact "Pate commands disabled"
            (query sonja :pate-verdicts :id ext-id)
            => (err :ya-extension-application)
            (command sonja :new-legacy-verdict-draft :id ext-id)
            => (err :ya-extension-application))
          (fact "Approve extension pseudo query"
            (query sonja :approve-ya-extension :id ext-id) => ok?)
          (fact "Approve application"
            (command sonja :approve-application :id ext-id :lang :fi) => ok?)
          (facts "Application state is finished with matching history"
            (let [{:keys [state history]} (query-application sonja ext-id)]
              (fact "State is finished" state => "finished")
              (fact "History is correct"
                (map :state history) => ["draft" "open" "submitted" "finished"]))))))))

(def local-db-name (str "test_ya_extension_itest_" (sade/now)))

(defn update-krysp [krysp new-reason new-start-date new-end-date]
  (enlive/at krysp
             [:lisaaikatieto :Lisaaika]
             (enlive/content (filter identity
                                     [{:tag :alkuPvm :content [new-start-date]}
                                      (when new-end-date
                                        {:tag :loppuPvm :content [new-end-date]})
                                      {:tag :perustelu :content [new-reason]}]))))

(mount/start #'mongo/connection)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))
(facts "Application and link permit extensions"
  (mongo/with-db local-db-name
    (fact "Add support to the organization"
      (local-command sipoo-ya :set-organization-selected-operations :organizationId sipoo-YA-org-id
                     :operations ["ya-katulupa-vesi-ja-viemarityot"
                                  "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"
                                  "ya-kayttolupa-mainostus-ja-viitoitus"
                                  "ya-kayttolupa-terassit"
                                  "ya-kayttolupa-vaihtolavat"
                                  "ya-kayttolupa-nostotyot"
                                  "ya-jatkoaika"]) => ok?)

    (let [{app-id :id} (create-and-submit-local-application pena
                                                            :propertyId sipoo-property-id
                                                            :operation "ya-katulupa-vesi-ja-viemarityot")
          krysp            (-> (io/resource "krysp/dev/jatkoaika-ya.xml")
                               slurp
                               (ss/replace "[YEAR]" (str (time/year (local/local-now))))
                               xml/parse
                               strip-xml-namespaces)
          krysp-start-date "04.10.2016"
          krysp-end-date   "22.11.2016"]
      (give-local-legacy-verdict sonja app-id)
      ;; Local command does not work with :check-for-verdict on CI
      (fact "No extensions"
        (local-query pena :ya-extensions :id app-id)
        => (err :error.no-extensions))
      (fact "Update application extension from KRYSP"
        (yax/update-application-extensions krysp)
        (-> (local-query pena :application :id app-id) :application :jatkoaika)
        => (contains {:alkuPvm   krysp-start-date
                      :loppuPvm  krysp-end-date
                      :perustelu "Jatkoajan perustelu"}))
      (fact "Extensions only include application extension"
        (local-query pena :ya-extensions :id app-id)
        => (contains {:extensions [{:startDate krysp-start-date
                                    :endDate   krysp-end-date
                                    :reason    "Jatkoajan perustelu"}]}))

      (facts "Extension link permits"
        (let [link-permit (fn [start-date end-date]
                            (let [ext-id (:id (local-command pena :create-continuation-period-permit :id app-id))
                                  doc-id (-> (local-query pena :application :id ext-id)
                                             :application
                                             (domain/get-document-by-name :tyo-aika-for-jatkoaika)
                                             :id)]
                              (mongo/update :applications
                                            {:_id       ext-id
                                             :documents {$elemMatch {:id doc-id}}}
                                            {$set {:documents.$.data.tyoaika-alkaa-pvm.value   start-date
                                                   :documents.$.data.tyoaika-paattyy-pvm.value end-date
                                                   }})
                              ext-id))
              ext-id1     (link-permit "7.7.2016" "8.8.2016")]
          (fact "One link permit and app extension"
            (:extensions (local-query pena :ya-extensions :id app-id))
            => [{:startDate "7.7.2016"
                 :endDate   "8.8.2016"
                 :id        ext-id1
                 :state     "draft"}
                {:startDate krysp-start-date
                 :endDate   krysp-end-date
                 :reason    "Jatkoajan perustelu"}])
          (let [ext-id2 (link-permit "4.10.2016" krysp-end-date)]
            (fact "Two link permits, latter matches app extension"
              (:extensions (local-query pena :ya-extensions :id app-id))
              => [{:startDate "7.7.2016"
                   :endDate   "8.8.2016"
                   :id        ext-id1
                   :state     "draft"}
                  {:id        ext-id2
                   :startDate "4.10.2016"
                   :endDate   krysp-end-date
                   :reason    "Jatkoajan perustelu"
                   :state     "draft"}])
            (fact "KRYSP does not contain end-date, does not match anymore"
              (yax/update-application-extensions (update-krysp krysp
                                                               "New reason"
                                                               "2016-10-04"
                                                               nil))
              (:extensions (local-query pena :ya-extensions :id app-id))
              => [{:startDate "7.7.2016"
                   :endDate   "8.8.2016"
                   :id        ext-id1
                   :state     "draft"}
                  {:id        ext-id2
                   :startDate "4.10.2016"
                   :endDate   krysp-end-date
                   :state     "draft"}
                  {:startDate krysp-start-date
                   :endDate   nil
                   :reason    "New reason"}]
              )
            (fact "Link permit and matching app extension, both without end-date"
              (let [ext-id3 (link-permit krysp-start-date nil)]
                (:extensions (local-query pena :ya-extensions :id app-id))
                => [{:startDate "7.7.2016"
                     :endDate   "8.8.2016"
                     :id        ext-id1
                     :state     "draft"}
                    {:id        ext-id2
                     :startDate "4.10.2016"
                     :endDate   krysp-end-date
                     :state     "draft"}
                    {:id        ext-id3
                     :state     "draft"
                     :startDate krysp-start-date
                     :endDate   nil
                     :reason    "New reason"}]))))))))
