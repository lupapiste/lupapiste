(ns lupapalvelu.suti-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(facts "Suti integration administration"
       (fact "Initially empty configuration"
             (query sipoo :suti-admin-details) => (contains {:suti {:server {}}}))
       (fact "No Suti operations"
             (query sipoo :suti-operations) => (contains {:operations nil}))
       (fact "Enable Suti"
             (command sipoo :suti-toggle-enabled :flag true) => ok?)
       (fact "Set bad www address"
             (command sipoo :suti-www :www "bad") => (contains {:text "error.invalid.url"}))
       (fact "Set good www addresses"
             (command sipoo :suti-www :www "") => ok?
             (command sipoo :suti-www :www "  http://example.com/$$/suti  ") => ok?)
       (fact "Set bad server details"
             (command sipoo :update-suti-server-details :url "bu hao" :username "" :password "")
             => (contains {:text "error.invalid.url"}))
       (fact "Set good server details"
             (command sipoo :update-suti-server-details :url "" :username "" :password "") => ok?
             (command sipoo :update-suti-server-details :url "   https://suti.org/   "
                      :username "sutiuser" :password "sutipassword") => ok?)
       (fact "Filled configuration"
             (query sipoo :suti-admin-details)
             =>  {:ok true
                  :suti {:server {:url "https://suti.org/" :username "sutiuser"}
                         :enabled true
                         :www "http://example.com/$$/suti"}})
       (facts "Bad operations"
              (command sipoo :suti-toggle-operation :operationId "ronaldos-moth" :flag true)
              => (contains {:text "error.operations.not-found"})
              (command sipoo :suti-toggle-operation :operationId "tyonjohtajan-nimeaminen" :flag false)
              =>  (contains {:text "error.operations.hidden"}))
       (facts "Add good operations"
              (command sipoo :suti-toggle-operation :operationId "  kaivuu  " :flag true) => ok?
              (command sipoo :suti-toggle-operation :operationId " purkaminen  " :flag true) => ok?
              (query sipoo :suti-operations) => (contains {:operations ["kaivuu" "purkaminen"]}))
       (facts "Remove operation"
              (command sipoo :suti-toggle-operation :operationId "  kaivuu  " :flag false) => ok?
              (query sipoo :suti-operations) => (contains {:operations ["purkaminen"]})))

(defn data-contains [check]
  (fn [{data :data}]
    (fact "data check" data => (contains check))))

(facts "Suti and application"
       (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
         (fact "Submit application"
               (command pena :submit-application :id application-id))
         (fact "No Suti since the primary operation is not suti-toggled"
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled false}))
         (fact "Toggle operation, but disable Suti for the organization"
               (command sipoo :suti-toggle-operation :operationId "kerrostalo-rivitalo" :flag true) => ok?
               (command sipoo :update-suti-server-details :url "http://localhost:8000/dev/suti/"
                        :username "suti" :password "wrong") => ok?
               (command sipoo :suti-toggle-enabled :flag false) => ok?)
         (fact "No Suti for application, since Suti disabled"
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled false}))
         (fact "Enable Suti"
               (command sipoo :suti-toggle-enabled :flag true) => ok?)
         (fact "Application Suti data is empty"
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled true
                                                                                         :products nil
                                                                                         :www nil
                                                                                         :suti {:id nil :added false}}))
         ;; Development (mockup) Suti server (/dev/suti) treats suti-ids semantically:
         ;; empty: no products
         ;; bad: 501
         ;; auth: requires username (suti) and password (secret)
         ;; all the other ids return products. See web.clj for details.
         (fact "Use empty as suti-id"
               (command pena :suti-update-id :id application-id :sutiId " empty  ") => ok?
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled true
                                                                                         :products nil
                                                                                         :www "http://example.com/empty/suti"
                                                                                         :suti {:id "empty" :added false}}))
         (fact "Use bad as suti-id, products contains an error ltext"
               (command pena :suti-update-id :id application-id :sutiId "  bad   ") => ok?
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled true
                                                                                         :products "suti.products-error"
                                                                                         :www "http://example.com/bad/suti"
                                                                                         :suti {:id "bad" :added false}}))
         (fact "Use auth as suti-id with wrong credentials"
               (command pena :suti-update-id :id application-id :sutiId "  auth  ") => ok?
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled true
                                                                                         :products "suti.products-error"
                                                                                         :www "http://example.com/auth/suti"
                                                                                         :suti {:id "auth" :added false}}))
         (fact "Use auth as suti-id with correct credentials"
               (command sipoo :update-suti-server-details :url "http://localhost:8000/dev/suti/"
                        :username "suti" :password "secret") => ok?
               (command pena :suti-update-id :id application-id :sutiId "auth") => ok?
               (query pena :suti-application-data :id application-id)
               => (data-contains {:enabled true
                                  :products [{:name "Four" :expired true :expirydate 1467883327899 :downloaded 1467019327022}
                                             {:name "Five" :expired true :expirydate 1468056127124 :downloaded nil}
                                             {:name "Six" :expired false :expirydate nil :downloaded nil}]
                                  :www "http://example.com/auth/suti"
                                  :suti {:id "auth" :added false}}))
         (fact "Finally, default good results"
               (command pena :suti-update-id :id application-id :sutiId "  good  ") => ok?
               (query pena :suti-application-data :id application-id)
               => (data-contains {:enabled true
                                  :products [{:name "One" :expired false :expirydate nil :downloaded nil}
                                             {:name "Two" :expired true :expirydate 1467710527123 :downloaded 1467364927456}
                                             {:name "Three" :expired false :expirydate nil :downloaded nil}]
                                  :www "http://example.com/good/suti"
                                  :suti {:id "good" :added false}}))
         (fact "Suti-id can be empty"
               (command pena :suti-update-id :id application-id :sutiId "") => ok?
               (query pena :suti-application-data :id application-id) => (data-contains {:enabled true
                                                                                         :products nil
                                                                                         :www nil
                                                                                         :suti {:id "" :added false}}))
         (fact "Suti added property -> no products"
               (command pena :suti-update-id :id application-id :sutiId "foobar") => ok?
               (command pena :suti-update-added :id application-id :added true) => ok?
               (query pena :suti-application-data :id application-id)
               => (data-contains {:enabled true
                                  :products nil
                                  :www "http://example.com/foobar/suti"
                                  :suti {:id "foobar" :added true}}))
         (fact "Authority has the needed access rights, too"
               (command sonja :suti-update-id :id application-id :sutiId "12345") => ok?
               (query sonja :suti-application-data :id application-id) => ok?)
         (fact "Authority invites statement giver"
               (command sonja :request-for-statement :id application-id
                        :functionCode nil
                        :selectedPersons [{:email (email-for-key mikko)
                                           :name "Mikko"
                                           :text "Ni hao!"}]) => ok?)
         (fact "Statement giver can access Suti data"
               (query mikko :suti-application-data :id application-id) => ok?)))
