(ns lupapalvelu.automatic-assignment-itest
  (:require [lupapalvelu.automatic-assignment.core :as automatic]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer [err]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.util :as util]))


(def db-name (str "test_automatic_assignment_" (sade.core/now)))
(def sipoo-org-id "753-R")
(def sipoo-kvv-handler-role-id "abba1111111111111112acdc")

(defn check-filter [filter-id checker]
  (fact {:midje/description (str "Check filter " filter-id)}
    (->> (query sipoo :organization-by-user
                :organizationId sipoo-org-id)
         :organization
         :automatic-assignment-filters
         (util/find-by-id filter-id)) => checker))

(defn check-result [filter-id result check]
  (fact "edit-filter-and-check"
    result => (just {:ok     true
                     :filter check})
    (check-filter filter-id check)))

(defn upsert-filter [params result]
  (fact "upsert-filter"
    (:filter (command sipoo :upsert-automatic-assignment-filter
                      :organizationId sipoo-org-id
                      :filter params)) => result))

(defn delete-filter [filter-id]
  (command sipoo :delete-automatic-assignment-filter
           :organizationId sipoo-org-id
           :filter-id filter-id))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (facts "Remove minimal filters"
      (delete-filter ely-filter-id) => ok?
      (delete-filter aita-filter-id) => ok?

      (some-> (query sipoo :organization-by-user
                     :organizationId sipoo-org-id)
              :organization
              :automatic-assignment-filters) => empty?)
    (fact "Upsert filter"
      (let [filter-id    (-> (command sipoo :upsert-automatic-assignment-filter
                                   :organizationId sipoo-org-id
                                   :filter {:name "My filter"
                                            :rank 0})
                          :filter :id)
            edit-fn      (fn [path value]
                        (command sipoo :upsert-automatic-assignment-filter
                                 :organizationId sipoo-org-id
                                 :filter (assoc-in {:id   filter-id
                                                    :name "Foo"
                                                    :rank 0} path value)))
            edit-ok      (fn [path value check]
                        (check-result filter-id (edit-fn path value) check))
            empty-filter (just {:id filter-id :name truthy :modified truthy :rank 0})]
        filter-id => truthy
        (fact "Rename filter"
          (edit-ok [:name] "   New name   "
                   #(= (:name %) "New name")))
        (fact "Name cannot be blank"
          (edit-fn [:name] "  ") => schema-error?)
        (fact "Name cannot be nil"
          (edit-fn [:name] nil) => schema-error?)
        (fact "Change rank"
          (edit-ok [:rank] 8 #(= (:rank %) 8)))
        (fact "Rank must be number"
          (edit-fn [:rank] "10") => fail?)
        (fact "Rank cannot be nil"
          (edit-fn [:rank] nil) => fail?)
        (facts "Areas"
          (fact "Add area criteria"
            (edit-ok [:criteria :areas] ["sipoo_keskusta"]
                     (contains {:criteria {:areas ["sipoo_keskusta"]}})))
          (fact "Bad areas are omitted. No empty areas"
            (edit-ok [:criteria :areas] ["area51"]
                     (just {:name truthy :modified truthy :id truthy :rank 0})))
          (fact "Clear area criteria"
            (edit-ok [:criteria :areas] ["sipoo_keskusta"]
                     (contains {:criteria {:areas ["sipoo_keskusta"]}}))
            (edit-ok [:criteria :areas] [] empty-filter))
          (fact "Delete area criteria"
            (edit-ok [:criteria :areas] nil empty-filter)))

        (facts "Operations"
          (fact "Add operation criteria"
            (edit-ok [:criteria :operations] ["pientalo"]
                     (contains {:criteria {:operations ["pientalo"]}})))
          (fact "Add multiple operations"
            (edit-ok [:criteria :operations] ["purkaminen" "kerrostalo-rivitalo" "mainoslaite"]
                     (contains {:criteria {:operations ["purkaminen" "kerrostalo-rivitalo" "mainoslaite"]}})))
          (fact "Bad operations are omitted"
            (edit-ok [:criteria :operations] ["purkaminen" "noop" "mainoslaite"]
                     (contains {:criteria {:operations ["purkaminen" "mainoslaite"]}})))
          (fact "Clear operations criteria"
            (edit-ok [:criteria :operations] [] empty-filter))
          (fact "Delete operations criteria"
            (edit-ok [:criteria :operations] nil empty-filter)))

        (facts "Attachment types"
          (fact "Add attachment types criteria"
            (edit-ok [:criteria :attachment-types] ["hakija.valtakirja"]
                     (contains {:criteria {:attachment-types ["hakija.valtakirja"]}})))
          (fact "Add multiple attachment types"
            (edit-ok [:criteria :attachment-types] ["paatoksenteko.ilmoitus" "erityissuunnitelmat.pohjavesiselvitys" "kartat.liitekartta"]
                     (contains {:criteria {:attachment-types ["paatoksenteko.ilmoitus" "erityissuunnitelmat.pohjavesiselvitys" "kartat.liitekartta"]}})))
          (fact "Bad attachment-types are omitted"
            (edit-ok [:criteria :attachment-types] ["paatoksenteko.ilmoitus" "erityissuunnitelmat.pohjavesiselvitys" "onpahan.vaan"]
                     (contains {:criteria {:attachment-types ["paatoksenteko.ilmoitus" "erityissuunnitelmat.pohjavesiselvitys"]}})))
          (fact "Clear attachment types criteria"
            (edit-ok [:criteria :attachment-types] [] empty-filter))
          (fact "Delete attachment types criteria"
            (edit-ok [:criteria :attachment-types] nil empty-filter)))

        (facts "Notice forms"
          (facts "Initially no forms enabled in the minimal organization. Thus, every form is omitted."
            (edit-ok [:criteria :notice-forms] ["construction"] empty-filter)
            (edit-ok [:criteria :notice-forms] ["terrain"] empty-filter)
            (edit-ok [:criteria :notice-forms] ["location"] empty-filter)
            (edit-ok [:criteria :notice-forms] ["construction" "terrain" "location"] empty-filter))
          (fact "Enable construction notice form"
            (command sipoo :toggle-organization-notice-form
                     :organizationId sipoo-org-id
                     :type "construction"
                     :enabled true) => ok?)
          (fact "Add notice forms criteria"
            (edit-ok [:criteria :notice-forms] ["construction"]
                     (contains {:criteria {:notice-forms ["construction"]}})))
          (fact "Enable terrain notice form"
            (command sipoo :toggle-organization-notice-form
                     :organizationId sipoo-org-id
                     :type "terrain"
                     :enabled true) => ok?)
          (fact "Add multiple notice forms"
            (edit-ok [:criteria :notice-forms] ["construction" "terrain"]
                     (contains {:criteria {:notice-forms ["construction" "terrain"]}})))
          (fact "Bad notice forms are not allowed"
            (edit-fn [:criteria :notice-forms] ["construction" "bad"]) => schema-error?)
          (fact "Clear notice forms criteria"
            (edit-ok [:criteria :notice-forms] [] empty-filter))
          (fact "Delete notice forms criteria"
            (edit-ok [:criteria :notice-forms] nil empty-filter)))

        (facts "Handler role criteria"
          (fact "Add handler role criteria"
            (edit-ok [:criteria :handler-role-id] sipoo-general-handler-id
                     (contains {:criteria {:handler-role-id sipoo-general-handler-id}})))
          (fact "Bad handler role criteria"
            (edit-fn [:criteria :handler-role-id]  sipoo-ya-general-handler-id)
            => (err :error.automatic-assignment-filter.handler-role-id))
          (fact "Disabled handler role in the organisation"
            (command sipoo :toggle-handler-role
                     :organizationId sipoo-org-id
                     :roleId sipoo-kvv-handler-role-id
                     :enabled false) => ok?
            (edit-fn [:criteria :handler-role-id]  sipoo-kvv-handler-role-id)
            => (err :error.automatic-assignment-filter.handler-role-id)
            (command sipoo :toggle-handler-role
                     :organizationId sipoo-org-id
                     :roleId sipoo-kvv-handler-role-id
                     :enabled true) => ok?
            (edit-ok [:criteria :handler-role-id]  sipoo-kvv-handler-role-id
                     (contains {:criteria {:handler-role-id sipoo-kvv-handler-role-id}}))))

        (facts "Handler role target"
          (fact "Add handler role target"
            (edit-ok [:target :handler-role-id] sipoo-general-handler-id
                     (contains {:target {:handler-role-id sipoo-general-handler-id}})))
          (fact "Bad handler role target"
            (edit-fn [:target :handler-role-id]  sipoo-ya-general-handler-id)
            => (err :error.automatic-assignment-filter.handler-role-id))
          (fact "Disabled handler role in the organisation"
            (command sipoo :toggle-handler-role
                     :organizationId sipoo-org-id
                     :roleId sipoo-kvv-handler-role-id
                     :enabled false) => ok?
            (edit-fn [:target :handler-role-id]  sipoo-kvv-handler-role-id)
            => (err :error.automatic-assignment-filter.handler-role-id)
            (command sipoo :toggle-handler-role
                     :organizationId sipoo-org-id
                     :roleId sipoo-kvv-handler-role-id
                     :enabled true) => ok?
            (edit-ok [:target :handler-role-id]  sipoo-kvv-handler-role-id
                     (contains {:target {:handler-role-id sipoo-kvv-handler-role-id}}))))

        (facts "Authority user target"
          (fact "Add authority user target"
            (edit-ok [:target :user-id] sonja-id
                     (contains {:target {:user-id sonja-id}})))
          (fact "Bad authority user target"
            (edit-fn [:target :user-id] pena)
            => (err :error.automatic-assignment-filter.user-id)
            (edit-fn [:target :user-id] "no-such-user")
            => (err :error.automatic-assignment-filter.user-id)
            (edit-fn [:target :user-id] olli-id)
            => (err :error.automatic-assignment-filter.user-id))
          (fact "Disable user"
            (mongo/update-by-id :users ronja-id {$set {:enabled false}})
            (edit-fn [:target :user-id] ronja-id)
            => (err :error.automatic-assignment-filter.user-id)
            (mongo/update-by-id :users ronja-id {$set {:enabled true}})
            (edit-ok [:target :user-id] ronja-id
                     (contains {:target {:user-id ronja-id}}))))

        (facts "Foreman roles"
          (fact "Set correct roles"
            (edit-ok [:criteria :foreman-roles] ["vastaava työnjohtaja" "iv-työnjohtaja"]
                     (contains {:criteria {:foreman-roles ["vastaava työnjohtaja"
                                                           "iv-työnjohtaja"]}})))
          (fact "Bad subfield"
            (edit-fn [:criteria :foreman-roles :bad] 1) => schema-error?)
          (fact "Bad roles"
            (edit-fn [:criteria :foreman-roles] ["vastaava työnjohtaja" "bad" "iv-työnjohtaja"])
            => schema-error?)
          (fact "Roles can be emptied"
            (edit-ok [:criteria :foreman-roles] [] empty-filter)))
        (facts "Add another filter"
          (let [second-id (upsert-automatic-assignment-filter sipoo sipoo-org-id
                                                              {:name     "Second filter"
                                                               :criteria {:attachment-types ["paapiirustus.asemapiirros"]}})]
            (fact "There are now two filters"
              (automatic/FILTERS (mongo/by-id :organizations sipoo-org-id [automatic/FILTERS]))
              => (just [(contains {:id filter-id})
                        (contains {:id second-id})]
                       :in-any-order))

            (fact "Delete the first filter"
              (delete-filter filter-id) => ok?
              (some-> (query sipoo :organization-by-user
                             :organizationId sipoo-org-id)
                      :organization
                      :automatic-assignment-filters)
              => (just [(contains {:id second-id})]))))))

    (let [sipoo-center (create-application pena :propertyId "75341608760002" :operation "pientalo"
                                           :x 404622.5149375 :y 6693884.332)
          sipoo-other  (create-application pena :propertyId "75389500020011" :operation "purkaminen"
                                           :x 404115.68336401 :y 6693114.6920006)
          sipoo-org    (mongo/by-id :organizations sipoo-org-id)]

      ;; Area checks are implemented with mongo geospatial query.
      (facts "match-criteria: areas. Other `match-criteria` tests are in `automatic_assignment_test.clj`."
        sipoo-center => truthy
        sipoo-other => truthy
        (fact "sipoo-center matches"
          (automatic/match-criteria :areas ["sipoo_keskusta"] {:application  sipoo-center
                                                               :organization sipoo-org})
          => true)
        (fact "... but sipoo-other does not"
          (automatic/match-criteria :areas ["sipoo_keskusta"] {:application  sipoo-other
                                                               :organization sipoo-org})
          => false)
        (facts "Area not in the organization: ignored"
          (automatic/match-criteria :areas ["sipoo_missing"] {:application  sipoo-center
                                                              :organization sipoo-org})
          => nil)
        (facts "No areas criteria: ignored"
          (automatic/match-criteria :areas [] {:application  sipoo-center
                                               :organization sipoo-org})
          => nil
          (automatic/match-criteria :areas nil {:application  sipoo-center
                                                :organization sipoo-org})
          => nil)))

    (facts "upsert-filter"
      (let [{fid1 :id
             :as  fltr1} (:filter (command sipoo :upsert-automatic-assignment-filter
                                           :organizationId sipoo-org-id
                                           :filter {:name "   Hello  "
                                                    :rank 0}))
            {fid2 :id
             :as  fltr2} (:filter (command sipoo :upsert-automatic-assignment-filter
                                           :organizationId sipoo-org-id
                                           :filter {:name     "World  "
                                                    :rank     2
                                                    :criteria {:areas            ["sipoo_keskusta"]
                                                               :operations       ["pientalo" "kerrostalo-rivitalo"]
                                                               :attachment-types ["muut.muu"]
                                                               :notice-forms     ["construction"]
                                                               :foreman-roles    ["vastaava työnjohtaja" "työnjohtaja"]}
                                                    :target   {:handler-role-id sipoo-kvv-handler-role-id
                                                               :user-id         ronja-id}}))]
        fltr1 => (just {:id       fid1
                        :rank     0
                        :modified pos?
                        :name     "Hello"})
        fltr2 => (just {:id       fid2
                        :name     "World"
                        :rank     2
                        :modified pos?
                        :criteria {:areas            ["sipoo_keskusta"]
                                   :operations       ["pientalo" "kerrostalo-rivitalo"]
                                   :attachment-types ["muut.muu"]
                                   :notice-forms     ["construction"]
                                   :foreman-roles    ["vastaava työnjohtaja" "työnjohtaja"]}
                        :target   {:handler-role-id sipoo-kvv-handler-role-id
                                   :user-id         ronja-id}})
        (fact "Upsert existing"
          (:filter (command sipoo :upsert-automatic-assignment-filter
                            :organizationId sipoo-org-id
                            :filter {:id       fid1
                                     :name     "Howdy  "
                                     :rank     1
                                     :criteria {:operations ["aita"]}
                                     :target   {}}))
          => (just {:id       fid1
                    :modified pos?
                    :name     "Howdy"
                    :rank     1
                    :criteria {:operations ["aita"]}})
          (:filter (command sipoo :upsert-automatic-assignment-filter
                            :organizationId sipoo-org-id
                            :filter {:id       fid2
                                     :name     " Ni hao! "
                                     :rank     2
                                     :criteria {:operations    ["pientalo"]
                                                :foreman-roles ["työnjohtaja"]}}))
          => (just {:id       fid2
                    :modified pos?
                    :name     "Ni hao!"
                    :rank     2
                    :criteria {:operations    ["pientalo"]
                               :foreman-roles ["työnjohtaja"]}}))
        (fact "No such filter id"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:id       (mongo/create-id)
                            :name     "Id not found"
                            :rank     2
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}})
          => (err :error.automatic-assignment-filter-not-found))
        (fact "Schema error"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name     "Schema error"
                            :rank     "bad rank"
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}})
          => (err :error.illegal-value:schema-validation))
        (fact "Bad areas, operations and attachment types are ignored"
          (:filter (command sipoo :upsert-automatic-assignment-filter
                            :organizationId sipoo-org-id
                            :filter {:name     "Ignore"
                                     :rank     10
                                     :criteria {:areas            ["bad_area"]
                                                :operations       ["pientalo" "bad_operation"]
                                                :attachment-types ["bad.badder"]
                                                :foreman-roles    ["työnjohtaja"]}}))
          => (just {:id       truthy
                    :name     "Ignore"
                    :rank     10
                    :modified pos?
                    :criteria {:operations    ["pientalo"]
                               :foreman-roles ["työnjohtaja"]}}))
        (fact "Outdated notice forms are ignored"
          (:filter (command sipoo :upsert-automatic-assignment-filter
                            :organizationId sipoo-org-id
                            :filter {:name     "Outdated"
                                     :rank     9
                                     :criteria {:notice-forms ["construction" "terrain" "location"]}}))
          => (just {:id       truthy
                    :modified pos?
                    :name     "Outdated"
                    :rank     9
                    :criteria {:notice-forms ["construction" "terrain"]}}))
        (fact "Handler role id error: bad id"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name     "Bad handler role"
                            :rank     1
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}
                            :target   {:handler-role-id (mongo/create-id)}})
          => (err :error.automatic-assignment-filter.handler-role-id))
        (fact "Handler role id error: role disabled"
          (command sipoo :toggle-handler-role
                     :organizationId sipoo-org-id
                     :roleId sipoo-kvv-handler-role-id
                     :enabled false) => ok?
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name     "Bad handler role"
                            :rank     1
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}
                            :target   {:handler-role-id sipoo-kvv-handler-role-id}})
          => (err :error.automatic-assignment-filter.handler-role-id))
        (fact "User id error: bad id"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name     "Bad user"
                            :rank     1
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}
                            :target   {:user-id (mongo/create-id)}})
          => (err :error.automatic-assignment-filter.user-id))
        (fact "User id error: not an authority"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name     "Disable user"
                            :rank     1
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}
                            :target   {:user-id laura-id}})
          => (err :error.automatic-assignment-filter.user-id))

        (fact "User id error: user disabled"
          (mongo/update-by-id :users ronja-id {$set {:enabled false}})
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name     "Disable user"
                            :rank     1
                            :criteria {:operations    ["pientalo"]
                                       :foreman-roles ["työnjohtaja"]}
                            :target   {:user-id ronja-id}})
          => (err :error.automatic-assignment-filter.user-id)
          (mongo/update-by-id :users ronja-id {$set {:enabled true}}))

        (fact "Email: no emails, no message"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name  "Empty email"
                            :rank  1
                            :email {:emails []}})
          => ok?)
        (fact "Email: bad emails, no message"
          (command sipoo :upsert-automatic-assignment-filter
                   :organizationId sipoo-org-id
                   :filter {:name  "Bad email"
                            :rank  1
                            :email {:emails ["not-valid"]}})
          => (err :error.illegal-value:schema-validation))
        (fact "Email: addresses must be canonized"
          (command sipoo :upsert-automatic-assignment-filter
                       :organizationId sipoo-org-id
                       :filter {:name  "Good email"
                                :rank  1
                                :email {:emails  ["FoO@BaR.CoM" "HIIHOO@EXAMPLE.NET"]
                                        :message "     My message"}})
          => (err :error.illegal-value:schema-validation))
        (fact "Email: addresses and the message are trimmed"
          (-> (command sipoo :upsert-automatic-assignment-filter
                       :organizationId sipoo-org-id
                       :filter {:name  "Good email"
                                :rank  1
                                :email {:emails  [" foo@bar.com " "  hiihoo@example.net  "]
                                        :message "     My message    "}})
              :filter :email)
          => {:emails  ["foo@bar.com" "hiihoo@example.net"]
              :message "My message"})))))
