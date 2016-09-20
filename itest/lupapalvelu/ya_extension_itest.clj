(ns lupapalvelu.ya-extension-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(defn err [msg]
  (partial expected-failure? msg))

(facts "YA extension applications"
       (let [r-id (create-app-id pena :propertyId sipoo-property-id
                                 :operation "pientalo")
             ya-id (create-app-id pena
                                        :propertyId sipoo-property-id
                                        :operation "ya-katulupa-vesi-ja-viemarityot")
             link-id (create-app-id pena
                                    :propertyId sipoo-property-id
                                    :operation "ya-katulupa-vesi-ja-viemarityot")
             ext-id (create-app-id pena
                                   :propertyId sipoo-property-id
                                   :operation "ya-jatkoaika")]
         (fact "No extensions for R application"
               (query pena :ya-extensions :id r-id) => fail?)
         (fact "Has backend, no extensions"
               (query pena :ya-extensions :id ya-id)
               => (err :error.has-ya-backend))
         (fact "Clear YA backend"
               (command sipoo-ya :set-krysp-endpoint
                        :url "" :permitType "YA"
                        :username "" :password ""
                        :version ""))
         (fact "No extensions yet established"
               (query pena :ya-extensions :id ya-id)
               => (err :error.no-extension-applications))
         (fact "Add non-extension link permit. Still no extensions."
               (command pena :add-link-permit :id ya-id :linkPermitId link-id) => ok?
               (query pena :ya-extensions :id ya-id)
               => (err :error.no-extension-applications))
         (fact "Add extension link permit"
               (command pena :add-link-permit :id ya-id :linkPermitId ext-id) => ok?
               (query pena :ya-extensions :id ya-id)
               => (contains {:extensions [{:id ext-id
                                           :startDate nil
                                           :endDate nil
                                           :state "draft"}]}))
         (fact "Edit extension and check again"
               (let [doc-id (-> (query-application pena ext-id)
                                (domain/get-document-by-name :tyo-aika-for-jatkoaika)
                                :id)]
                 (command pena :update-doc :id ext-id :collection "documents"
                          :doc doc-id :updates [[:tyoaika-alkaa-pvm "20.09.2016"]
                                                [:tyoaika-paattyy-pvm "10.10.2016"]]) => ok?
                 (command pena :add-comment :id ext-id :text "Zao!"
                          :roles ["applicant" "authority"]
                          :target {:type "application"}
                          :openApplication true) => ok?
                 (query pena :ya-extensions :id ya-id)
                 => (contains {:extensions [{:id ext-id
                                             :startDate "20.09.2016"
                                             :endDate "10.10.2016"
                                             :state "open"}]})))))
