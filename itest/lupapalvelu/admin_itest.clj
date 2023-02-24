(ns lupapalvelu.admin-itest
  "Some tests for admin-specific functionality"
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.util :as util]))

(def db-name (str "test_admin_" (sade.core/now)))
(def sipoo-org-id "753-R")

(defn find-role-id [handler-roles en-name]
  (:id (util/find-first (util/fn-> :name :en (= en-name)) handler-roles)))

(defn change-app-state [app-id new-state]
  (mongo/update-by-id :applications app-id {$set {:state new-state}}))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (facts "Add new handler role and automatic assignment filter to Sipoo"
      (let [{role-id :id} (command sipoo :upsert-handler-role
                                   :organizationId sipoo-org-id
                                   :name {:fi "Uusi"
                                          :sv "Ny"
                                          :en "New"})]
        role-id => truthy
        (fact "Add filter"
          (command sipoo :upsert-automatic-assignment-filter
                         :organizationId sipoo-org-id
                         :filter {:name "Map check"
                                  :rank 0
                                  :criteria {:attachment-types ["rakennuspaikka.karttaote"]}
                                  :target {:handler-role-id role-id}}) => ok?)))
    (facts "Organization authorities"
      (fact "Disable Ronja temporarily"
        (mongo/update-by-id :users ronja-id {$set {:enabled false}}) => nil)
      (fact "Enabled authorities for Sipoo"
        (:authorities (query admin :organization-authorities :organizationId sipoo-org-id))
        => (just (contains {:firstName "Sonja"})))
      (fact "All authorities for Sipoo"
        (:authorities (query admin :organization-authorities :organizationId sipoo-org-id
                                   :includeDisabled true))
        => (just [(contains {:firstName "Sonja"}) (contains {:firstName "Ronja"})] :in-any-order))
      (fact "Enable Ronja again"
        (mongo/update-by-id :users ronja-id {$set {:enabled true}}) => nil)
      (fact "(Re)enabled authorities for Sipoo"
        (:authorities (query admin :organization-authorities :organizationId sipoo-org-id))
        => (just (contains {:firstName "Sonja"}) (contains {:firstName "Ronja"}) :in-any-order))
      (fact "Organization must exist"
        (query admin :organization-authorities :organizationId "bad")
        => (err :error.organization-not-found)))

    (facts "Bulk change handlers"
      (let [{:keys [handler-roles]} (:data (query admin :organization-by-id
                                                  :organizationId sipoo-org-id))
            general-role-id         (find-role-id handler-roles "Handler")
            kvv-role-id             (find-role-id handler-roles "KVV-Handler")
            new-role-id             (find-role-id handler-roles "New")
            app-id1                 (:id (create-app pena :propertyId sipoo-property-id
                                                     :operation "pientalo"
                                                     :address "One"))
            app-id2                 (:id (create-app pena :propertyId sipoo-property-id
                                                     :operation "sisatila-muutos"
                                                     :address "Two"))
            app-id3                 (:id (create-app pena :propertyId sipoo-property-id
                                                     :operation "varasto-tms"
                                                     :address "Three"))]
        (fact "Sanity checks"
          general-role-id => truthy
          kvv-role-id => truthy
          new-role-id => truthy
          app-id1 => truthy
          app-id2 => truthy
          app-id3 => truthy)
        (change-app-state app-id1 :open)
        (change-app-state app-id2 :submitted)
        (change-app-state app-id3 :complementNeeded)
        (fact "Laura as general handler for app1 fails because Laura is not authority"
          (command sonja :upsert-application-handler :id app-id1
                         :roleId general-role-id
                         :userId laura-id) => fail?)
        (fact "Make Laura a proper authority"
          (command sipoo :update-user-roles
                   :email (email-for-key laura)
                   :organizationId sipoo-org-id
                   :roles ["biller" "authority"]) => ok?)
        (fact "Laura as general handler for app1 now succeeds"
          (command sonja :upsert-application-handler :id app-id1
                         :roleId general-role-id
                         :userId laura-id) => ok?)
        (fact "Laura also as New handler for app1"
          (command sonja :upsert-application-handler :id app-id1
                         :roleId new-role-id
                         :userId laura-id) => ok?)
        (fact "Laura as New handler for app2"
          (command sonja :upsert-application-handler :id app-id2
                         :roleId new-role-id
                         :userId laura-id) => ok?)
        (fact "Ronja as New handler for app3"
          (command sonja :upsert-application-handler :id app-id3
                         :roleId new-role-id
                         :userId ronja-id) => ok?)
        (fact "Add general handler trigger attachment to app1"
          (upload-file-and-bind pena app-id1 {:type     {:type-group "paapiirustus"
                                                         :type-id    "aitapiirustus"}
                                              :filename "dev-resources/test-pdf.pdf"
                                              :contents "Fences"
                                              :group    {}})
          => truthy)
        (fact "Add New handler trigger attachment to app1"
          (upload-file-and-bind pena app-id1 {:type     {:type-group "rakennuspaikka"
                                                         :type-id    "karttaote"}
                                              :filename "dev-resources/test-pdf.pdf"
                                              :contents "Map"
                                              :group    {}})
          => truthy)
        (fact "Add New handler trigger attachment to app2"
          (upload-file-and-bind pena app-id2 {:type     {:type-group "rakennuspaikka"
                                                         :type-id    "karttaote"}
                                              :filename "dev-resources/test-pdf.pdf"
                                              :contents "Map"
                                              :group    {}})
          => truthy)
        (fact "Add New handler trigger attachment to app3"
          (upload-file-and-bind pena app-id3 {:type     {:type-group "rakennuspaikka"
                                                         :type-id    "karttaote"}
                                              :filename "dev-resources/test-pdf.pdf"
                                              :contents "Map"
                                              :group    {}})
          => truthy)

        (fact "There are four assignments"
          (mongo/select :assignments {})
          => (just [(contains {:application (contains {:id app-id1})
                               :recipient   (contains {:id laura-id})})
                    (contains {:application (contains {:id app-id1})
                               :recipient   (contains {:id laura-id})})
                    (contains {:application (contains {:id app-id2})
                               :recipient   (contains {:id laura-id})})
                    (contains {:application (contains {:id app-id3})
                               :recipient   (contains {:id ronja-id})})]
                   :in-any-order))

        (facts "Bad bulk change calls"
          (fact "Organization must exist"
            (command admin :change-handler-applications :organizationId "bad"
                     :roleId kvv-role-id
                     :oldUserId laura-id
                     :newUserId ronja-id
                     :states ["submitted" "sent"])
            => (err :error.organization-not-found))
          (fact "New user cannot be the old user"
            (command admin :change-handler-applications :organizationId sipoo-org-id
                     :roleId kvv-role-id
                     :oldUserId laura-id
                     :newUserId laura-id
                     :states ["submitted" "sent"])
            => (err :error.same-user))
          (fact "New user must exist"
            (command admin :change-handler-applications :organizationId sipoo-org-id
                     :roleId kvv-role-id
                     :oldUserId laura-id
                     :newUserId "bad"
                     :states ["submitted" "sent"])
            => (err :error.user-not-found))
          (fact "Role must exist"
            (command admin :change-handler-applications :organizationId sipoo-org-id
                     :roleId "bad"
                     :oldUserId laura-id
                     :newUserId ronja-id
                     :states ["submitted" "sent"])
            => (err :error.handler-role-not-found))
          (fact "Every state must be valid"
            (command admin :change-handler-applications :organizationId sipoo-org-id
                     :roleId kvv-role-id
                     :oldUserId laura-id
                     :newUserId ronja-id
                     :states ["submitted" "bad" "sent"])
            => fail?))

        ;; Situation before the bulk change
        ;; App1 (open): Laura is both general and New handler. Active assignments for both.
        ;; App2 (submitted): Laura is New handler with active assignment
        ;; App3 (complementNeeded): Ronja is New handler with active assignment

        (facts "No changes"
          (fact "Laura is not a KVV-handler in Sipoo"
            (:count (command admin :change-handler-applications :organizationId sipoo-org-id
                             :roleId kvv-role-id
                             :oldUserId laura-id
                             :newUserId ronja-id
                             :states ["submitted" "open"]))
            => 0)
          (fact "Laura is a New handler in Sipoo, but in different states"
            (:count (command admin :change-handler-applications :organizationId sipoo-org-id
                             :roleId kvv-role-id
                             :oldUserId laura-id
                             :newUserId ronja-id
                             :states ["verdictGiven" "appealed"]))
            => 0))

        (facts "Successful changes"
          (fact "New handler Laura -> Sonja in open and closed applications"
            (:count (command admin :change-handler-applications :organizationId sipoo-org-id
                             :roleId new-role-id
                             :oldUserId laura-id
                             :newUserId sonja-id
                             :states ["open" "closed"]))
            => 1)

          (fact "Application handlers have changed for App1"
            (mongo/select :applications {} [:handlers :state :history])
            => (just [(contains {:id       app-id1
                                 :handlers (just [(contains {:roleId general-role-id
                                                             :userId laura-id})
                                                  (contains {:roleId new-role-id
                                                             :userId sonja-id})])
                                 :state    "open"
                                 :history  (just [(contains {:state "draft"})
                                                  (contains {:handler (contains {:userId      laura-id
                                                                                 :roleId general-role-id})
                                                             :user    (contains {:id sonja-id})})
                                                  (contains {:handler (contains {:userId      laura-id
                                                                                 :roleId new-role-id})
                                                             :user    (contains {:id sonja-id})})
                                                  (contains {:handler (contains {:userId      sonja-id
                                                                                 :roleId new-role-id})
                                                             :user    (contains {:id "batchrun-user"})})])})
                      (contains {:id       app-id2
                                 :handlers (just [(contains {:roleId new-role-id
                                                             :userId laura-id})])
                                 :state    "submitted"})
                      (contains {:id       app-id3
                                 :handlers (just [(contains {:roleId new-role-id
                                                             :userId ronja-id})])
                                 :state    "complementNeeded"})]))

          (fact "Assignments have changed"
            (mongo/select :assignments {})
            => (just [(contains {:application (contains {:id app-id1})
                                 :recipient   (contains {:id laura-id})})
                      (contains {:application (contains {:id app-id1})
                                 :recipient   (contains {:id sonja-id})})
                      (contains {:application (contains {:id app-id2})
                                 :recipient   (contains {:id laura-id})})
                      (contains {:application (contains {:id app-id3})
                                 :recipient   (contains {:id ronja-id})})]
                     :in-any-order))

          (fact "Complete Ronja's only assignment"
            (let [assignment-id (:id (mongo/select-one :assignments {:recipient.id ronja-id}))]
              (command ronja :complete-assignment :assignmentId assignment-id) => ok?))

          (fact "New handler Ronja -> Laura in complementNeeded applications"
            (:count (command admin :change-handler-applications :organizationId sipoo-org-id
                             :roleId new-role-id
                             :oldUserId ronja-id
                             :newUserId laura-id
                             :states ["complementNeeded"]))
            => 1)

          (fact "Application handlers have changed for App3"
            (mongo/select :applications {} [:handlers :state])
            => (just [(contains {:id       app-id1
                                 :handlers (just [(contains {:roleId general-role-id
                                                             :userId laura-id})
                                                  (contains {:roleId new-role-id
                                                             :userId sonja-id})])
                                 :state    "open"})
                      (contains {:id       app-id2
                                 :handlers (just [(contains {:roleId new-role-id
                                                             :userId laura-id})])
                                 :state    "submitted"})
                      (contains {:id       app-id3
                                 :handlers (just [(contains {:roleId new-role-id
                                                             :userId laura-id})])
                                 :state    "complementNeeded"})]))

          (fact "Assignments have not changed"
            (mongo/select :assignments {})
            => (just [(contains {:application (contains {:id app-id1})
                                 :recipient   (contains {:id laura-id})})
                      (contains {:application (contains {:id app-id1})
                                 :recipient   (contains {:id sonja-id})})
                      (contains {:application (contains {:id app-id2})
                                 :recipient   (contains {:id laura-id})})
                      (contains {:application (contains {:id app-id3})
                                 :recipient   (contains {:id ronja-id})})]
                     :in-any-order))

          (fact "New handler Laura -> Sonja in open, submitted and complementNeeded applications"
            (:count (command admin :change-handler-applications :organizationId sipoo-org-id
                             :roleId new-role-id
                             :oldUserId laura-id
                             :newUserId sonja-id
                             :states ["open" "submitted" "complementNeeded"]))
            => 2)

          (fact "Application handlers have changed for App2 and App3"
            (mongo/select :applications {} [:handlers :state])
            => (just [(contains {:id       app-id1
                                 :handlers (just [(contains {:roleId general-role-id
                                                             :userId laura-id})
                                                  (contains {:roleId new-role-id
                                                             :userId sonja-id})])
                                 :state    "open"})
                      (contains {:id       app-id2
                                 :handlers (just [(contains {:roleId new-role-id
                                                             :userId sonja-id})])
                                 :state    "submitted"})
                      (contains {:id       app-id3
                                 :handlers (just [(contains {:roleId new-role-id
                                                             :userId sonja-id})])
                                 :state    "complementNeeded"})]))

          (fact "One assignment has changed"
            (mongo/select :assignments {})
            => (just [(contains {:application (contains {:id app-id1})
                                 :recipient   (contains {:id laura-id})})
                      (contains {:application (contains {:id app-id1})
                                 :recipient   (contains {:id sonja-id})})
                      (contains {:application (contains {:id app-id2})
                                 :recipient   (contains {:id sonja-id})})
                      (contains {:application (contains {:id app-id3})
                                 :recipient   (contains {:id ronja-id})})]
                     :in-any-order)))))))
