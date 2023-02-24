(ns lupapalvelu.price-catalogues-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.integrations-api]
            [lupapalvelu.invoice-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.date :as date]
            [mount.core :as mount]
            [sade.util :as util]))

(defn timestamp-from-today
  "At midnight in the default timezone."
  [days]
  (-> (date/now)
      date/start-of-day
      (.plusDays days)
      date/timestamp))


(def last-week          (timestamp-from-today -7))
(def yesterday          (timestamp-from-today -1))
(def today              (timestamp-from-today 0))
(def tomorrow           (timestamp-from-today 1))
(def day-after-tomorrow (timestamp-from-today 2))
(def next-week          (timestamp-from-today 7))

(fact "Timestamp sanity check"
  (< last-week today tomorrow next-week) => true)

(mount/start #'mongo/connection)

(mongo/with-db test-db-name
  (fixture/apply-fixture "invoicing-enabled")

  (with-local-actions

    (fact "organization-price-catalogues query"

      (fact "should return unauthorized response when user is not an organization admin"
        (let [response (-> (query sonja :organization-price-catalogues
                                  :organizationId "753-R"))]
          response => fail?
          (:text response) => "error.unauthorized"))

      (fact "should return unauthorized response when user is an or organization admin or another org"
        (let [response (-> (query sipoo-ya :organization-price-catalogues
                                  :organizationId "753-R"))]
          response => fail?
          (:text response) => "error.unauthorized"))

      (fact "should return empty collection when no price catalogues found for org-id"
        (let [response (-> (query sipoo :organization-price-catalogues
                                  :organizationId "753-R"))]
          (:price-catalogues response) => [])))

    (facts "Price catalogue draft"
      (let [{draft-id :id :as draft} (:draft (command sipoo :new-price-catalogue-draft
                                                      :organizationId "753-R"))]
        (fact "Draft created"
          draft => (just {:id              draft-id
                          :organization-id "753-R"
                          :type            "R"
                          :state           "draft"
                          :name            "Taksa"
                          :rows            (just [(just {:discount-percent 0
                                                         :id               truthy})])
                          :meta            (just {:modified    pos?
                                                  :modified-by {:id        (id-for-key sipoo)
                                                                :firstName "Simo"
                                                                :lastName  "Suurvisiiri"
                                                                :role      "authority"
                                                                :username  "sipoo"}})}))
        (fact "Draft is included in the catalogue list"
          (:price-catalogues (query sipoo :organization-price-catalogues
                                    :organizationId "753-R"))
          => [{:id    draft-id
               :name  "Taksa"
               :state "draft"}])

        (fact "Delete the default row"
          (:draft (command sipoo :edit-price-catalogue-draft
                           :organizationId "753-R"
                           :price-catalogue-id draft-id
                           :edit {:delete-row (-> draft :rows first :id)}))
          => (just {:id              draft-id
                    :organization-id "753-R"
                    :type            "R"
                    :state           "draft"
                    :name            "Taksa"
                    :rows            []
                    :meta            (just {:modified    pos?
                                            :modified-by {:id        (id-for-key sipoo)
                                                          :firstName "Simo"
                                                          :lastName  "Suurvisiiri"
                                                          :role      "authority"
                                                          :username  "sipoo"}})}))

        (fact "Add row"
          (let [updated (:draft (command sipoo :edit-price-catalogue-draft
                                         :organizationId "753-R"
                                         :price-catalogue-id draft-id
                                         :edit {:row {:code           "  A  "
                                                      :text           "  First item  "
                                                      :unit           "kpl"
                                                      :price-per-unit 10}}))]
            updated => (just {:id              draft-id
                              :organization-id "753-R"
                              :type            "R"
                              :state           "draft"
                              :name            "Taksa"
                              :rows            (just [(just {:id             truthy
                                                             :code           "A"
                                                             :text           "First item"
                                                             :unit           "kpl"
                                                             :price-per-unit 10})])
                              :meta            (just {:modified    pos?
                                                      :modified-by {:id        (id-for-key sipoo)
                                                                    :firstName "Simo"
                                                                    :lastName  "Suurvisiiri"
                                                                    :role      "authority"
                                                                    :username  "sipoo"}})})
            (> (-> updated :meta :modified)
               (-> draft :meta :modified)) => true))

        (fact "Add another row"
          (let [{:keys [draft]} (command sipoo :edit-price-catalogue-draft
                                         :organizationId "753-R"
                                         :price-catalogue-id draft-id
                                         :edit {:row {:code           "B"
                                                      :text           "Second item"
                                                      :unit           "m2"
                                                      :price-per-unit 20}})
                first-id        (-> draft :rows second :id)]
            (:rows draft)
            => (just [(just {:id             truthy
                             :code           "B"
                             :text           "Second item"
                             :unit           "m2"
                             :price-per-unit 20})
                      (just {:id             first-id
                             :code           "A"
                             :text           "First item"
                             :unit           "kpl"
                             :price-per-unit 10})])
            (fact "Edit the first row"
              (-> (command sipoo :edit-price-catalogue-draft
                           :organizationId "753-R"
                           :price-catalogue-id draft-id
                           :edit {:row {:id                first-id
                                        :code              "  A1  "
                                        :text              " Updated first row  "
                                        :min-total-price   10
                                        :max-total-price   100
                                        :operations        ["  pientalo  " "  foobar  "]
                                        :product-constants {:projekti "  Porject  "
                                                            :kohde    " Target "}}})
                  :draft :rows)
              => (just [(just {:id             truthy
                               :code           "B"
                               :text           "Second item"
                               :unit           "m2"
                               :price-per-unit 20})
                        (just {:id                first-id
                               :code              "A1"
                               :text              "Updated first row"
                               :unit              "kpl"
                               :min-total-price   10
                               :max-total-price   100
                               :operations        ["pientalo" "foobar"]
                               :price-per-unit    10
                               :product-constants {:projekti "Porject"
                                                   :kohde    "Target"}})]))
            (fact "Edit the first row again"
              (-> (command sipoo :edit-price-catalogue-draft
                           :organizationId "753-R"
                           :price-catalogue-id draft-id
                           :edit {:row {:id                first-id
                                        :operations        ["pientalo"]
                                        :product-constants {:projekti        "Project"
                                                            :kustannuspaikka "Cost center"
                                                            :toiminto        "   Fun  "
                                                            :muu-tunniste    "Other id"}}})
                  :draft :rows)
              => (just [(just {:id             truthy
                               :code           "B"
                               :text           "Second item"
                               :unit           "m2"
                               :price-per-unit 20})
                        (just {:id                first-id
                               :code              "A1"
                               :text              "Updated first row"
                               :unit              "kpl"
                               :min-total-price   10
                               :max-total-price   100
                               :operations        ["pientalo"]
                               :price-per-unit    10
                               :product-constants {:projekti        "Project"
                                                   :kohde           "Target"
                                                   :kustannuspaikka "Cost center"
                                                   :toiminto        "Fun"
                                                   :muu-tunniste    "Other id"}})]))

            (fact "Add third row"
              (let [{:keys [draft]}        (command sipoo :edit-price-catalogue-draft
                                                    :organizationId "753-R"
                                                    :price-catalogue-id draft-id
                                                    :edit {:row {:code             "C"
                                                                 :text             "Third item"
                                                                 :discount-percent 25}})
                    [third-id second-id _] (map :id (:rows draft))]
                (facts "Top row up -> bottom"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id third-id :direction "up")
                       :updated-catalogue :rows
                       (map :id))
                  => [second-id first-id third-id])

                (facts "Bottom row down -> top"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id third-id :direction "down")
                       :updated-catalogue :rows
                       (map :id))
                  => [third-id second-id first-id])

                (facts "Top row down -> middle"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id third-id :direction "down")
                       :updated-catalogue :rows
                       (map :id))
                  => [second-id third-id first-id])

                (facts "Middle row down -> bottom"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id third-id :direction "down")
                       :updated-catalogue :rows
                       (map :id))
                  => [second-id first-id third-id])

                (facts "Bottom row up -> middle"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id third-id :direction "up")
                       :updated-catalogue :rows
                       (map :id))
                  => [second-id third-id first-id])

                (facts "Middle row up -> top"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id third-id :direction "up")
                       :updated-catalogue :rows
                       (map :id))
                  => [third-id second-id first-id])

                (facts "Move unknown row -> no changes"
                  (->> (command sipoo :move-price-catalogue-row
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :row-id (mongo/create-id) :direction "up")
                       :updated-catalogue :rows
                       (map :id))
                  => [third-id second-id first-id])

                (facts "Remove second row"
                  (->> (command sipoo :edit-price-catalogue-draft
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :edit {:delete-row second-id})
                       :draft :rows
                       (map :id))
                  => [third-id first-id])

                (facts "Removing unknown row does nothing"
                  (->> (command sipoo :edit-price-catalogue-draft
                                :organizationId "753-R"
                                :price-catalogue-id draft-id
                                :edit {:delete-row (mongo/create-id)})
                       :draft :rows
                       (map :id))
                  => [third-id first-id])))

            (facts "Set both valid-from and valid-until"
              (fact "Bad dates"
                (command sipoo :edit-price-catalogue-draft
                         :organizationId "753-R"
                         :price-catalogue-id draft-id
                         :edit {:valid {:from next-week :until last-week}})
                => (err :error.price-catalogue.bad-dates))
              (fact "Good dates"
                (:draft (command sipoo :edit-price-catalogue-draft
                                 :organizationId "753-R"
                                 :price-catalogue-id draft-id
                                 :edit {:valid {:from today :until tomorrow}}))
                => (just {:id              draft-id
                          :organization-id "753-R"
                          :type            "R"
                          :state           "draft"
                          :name            "Taksa"
                          :valid-from      today
                          :valid-until     (dec day-after-tomorrow)
                          :rows            (just [truthy truthy])
                          :meta            (just {:modified    pos?
                                                  :modified-by {:id        (id-for-key sipoo)
                                                                :firstName "Simo"
                                                                :lastName  "Suurvisiiri"
                                                                :role      "authority"
                                                                :username  "sipoo"}})}))
              (fact "Both can be the same date."
                (:draft (command sipoo :edit-price-catalogue-draft
                                 :organizationId "753-R"
                                 :price-catalogue-id draft-id
                                 :edit {:valid {:from today :until today}}))
                => (just {:id              draft-id
                          :organization-id "753-R"
                          :type            "R"
                          :state           "draft"
                          :name            "Taksa"
                          :valid-from      today
                          :valid-until     (dec tomorrow)
                          :rows            (just [truthy truthy])
                          :meta            (just {:modified    pos?
                                                  :modified-by {:id        (id-for-key sipoo)
                                                                :firstName "Simo"
                                                                :lastName  "Suurvisiiri"
                                                                :role      "authority"
                                                                :username  "sipoo"}})})))

            (fact "Set valid-from field. The timesamp is 'truncated' to the start of the day."
              (:draft (command sipoo :edit-price-catalogue-draft
                               :organizationId "753-R"
                               :price-catalogue-id draft-id
                               :edit {:valid {:from (+ last-week (* 1000 3600 8))}}))
              => (just {:id              draft-id
                        :organization-id "753-R"
                        :type            "R"
                        :state           "draft"
                        :name            "Taksa"
                        :valid-from      last-week
                        :rows            (just [truthy truthy])
                        :meta            (just {:modified    pos?
                                                :modified-by {:id        (id-for-key sipoo)
                                                              :firstName "Simo"
                                                              :lastName  "Suurvisiiri"
                                                              :role      "authority"
                                                              :username  "sipoo"}})}))

            (fact "Set valid-until field. The timesamp is 'extended' to the end of the day."
              (:draft (command sipoo :edit-price-catalogue-draft
                               :organizationId "753-R"
                               :price-catalogue-id draft-id
                               :edit {:valid {:until tomorrow}}))
              => (just {:id              draft-id
                        :organization-id "753-R"
                        :type            "R"
                        :state           "draft"
                        :name            "Taksa"
                        :valid-until     (dec day-after-tomorrow)
                        :rows            (just [truthy truthy])
                        :meta            (just {:modified    pos?
                                                :modified-by {:id        (id-for-key sipoo)
                                                              :firstName "Simo"
                                                              :lastName  "Suurvisiiri"
                                                              :role      "authority"
                                                              :username  "sipoo"}})}))

            (facts "Clear both valid-from and valid-until"
              (:draft (command sipoo :edit-price-catalogue-draft
                               :organizationId "753-R"
                               :price-catalogue-id draft-id
                               :edit {:valid {}}))
              => (just {:id              draft-id
                        :organization-id "753-R"
                        :type            "R"
                        :name            "Taksa"
                        :state           "draft"
                        :rows            (just [truthy truthy])
                        :meta            (just {:modified    pos?
                                                :modified-by {:id        (id-for-key sipoo)
                                                              :firstName "Simo"
                                                              :lastName  "Suurvisiiri"
                                                              :role      "authority"
                                                              :username  "sipoo"}})}))

            (facts "Good dates once more"
              (command sipoo :edit-price-catalogue-draft
                       :organizationId "753-R"
                       :price-catalogue-id draft-id
                       :edit {:valid {:from yesterday :until tomorrow}}) => ok?)

            (fact "No billing periods are not supported for R catalogues"
              (command sipoo :save-no-billing-periods
                       :organizationId "753-R"
                       :price-catalogue-id draft-id
                       :no-billing-periods {:1 {:start "1.2.2020" :end "2.2.2020"}})
              => (err :error.price-catalogue.wrong-type))

            (facts "Change catalogue name"
              (fact "Name cannot be blank"
                (command sipoo :edit-price-catalogue-draft
                         :organizationId "753-R"
                         :price-catalogue-id draft-id
                         :edit {:name "   "}))
              => fail?
              (fact "Changed name is trimmed"
                (:draft (command sipoo :edit-price-catalogue-draft
                                 :organizationId "753-R"
                         :price-catalogue-id draft-id
                         :edit {:name "  Show me the money!  "}))
                => (contains {:name "Show me the money!"})))))))

    (fact "publish-price-catalogue command"

      (let [{draft-id :id}      (->> (query sipoo :organization-price-catalogues
                                            :organizationId "753-R")
                                     :price-catalogues
                                     (util/find-by-key :state "draft"))
            [third-id first-id] (->> (query sipoo :organization-price-catalogue
                                            :organizationId "753-R"
                                            :price-catalogue-id draft-id)
                                     :price-catalogue
                                     :rows
                                     (map :id))]
        draft-id => truthy
        third-id => truthy
        first-id => truthy
        (fact "should return unauthorized response when user is not an organization admin"
          (command sonja :publish-price-catalogue
                   :organizationId "753-R"
                   :price-catalogue-id draft-id) => unauthorized?)

        (fact "should return unauthorized response when user is an or organization admin or another org"
          (command sipoo-ya :publish-price-catalogue
                   :organizationId "753-R"
                   :price-catalogue-id draft-id) => unauthorized?)

        (fact "Organization and draft must match"
          (command sipoo-ya :publish-price-catalogue
                   :organizationId "753-YA"
                   :price-catalogue-id draft-id) => (err :error.price-catalogue.not-found))

        (fact "should return invalid-price-catalogue response when request data is not valid"
          (command sipoo :publish-price-catalogue
                   :organizationId "753-R"
                   :price-catalogue-id draft-id) => {:ok      false
                                                     :text    "error.price-catalogue.bad-rows"
                                                     :row-ids [third-id]})

        (fact "Product constants can be cleared"
          (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                   :price-catalogue-id draft-id
                   :edit {:row {:id                first-id
                                :product-constants {:projekti        ""
                                                    :kohde           "  "
                                                    :kustannuspaikka ""
                                                    :toiminto        " "
                                                    :muu-tunniste    ""}}}) => ok?)

        (fact "Add missing mandatory data"
          (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                   :price-catalogue-id draft-id
                   :edit {:row {:id             third-id
                                :price-per-unit 30
                                :unit           "vk"}}) => ok?)

        (fact "And some other stuff as well"
          (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                   :price-catalogue-id draft-id
                   :edit {:row {:id                third-id
                                :product-constants {:muu-tunniste "My tag"}}}) => ok?)

        (fact "Publish is now successful"
          (command sipoo :publish-price-catalogue
                   :organizationId "753-R"
                   :price-catalogue-id draft-id) => {:ok                 true
                                                     :price-catalogue-id draft-id})

        (fact "The draft has been published"
          (:price-catalogue (query sipoo :organization-price-catalogue :organizationId "753-R"
                                   :price-catalogue-id draft-id))
          => (just {:id              draft-id
                    :organization-id "753-R"
                    :type            "R"
                    :name            "Show me the money!"
                    :state           "published"
                    :valid-from      yesterday
                    :valid-until     (dec day-after-tomorrow)
                    :rows            [{:id                third-id
                                       :code              "C"
                                       :text              "Third item"
                                       :unit              "vk"
                                       :price-per-unit    30
                                       :discount-percent  25
                                       :operations        []
                                       :product-constants {:kustannuspaikka  ""
                                                           :alv              ""
                                                           :laskentatunniste ""
                                                           :muu-tunniste     "My tag"}}
                                      {:id               first-id
                                       :code             "A1"
                                       :text             "Updated first row"
                                       :unit             "kpl"
                                       :price-per-unit   10
                                       :min-total-price  10
                                       :max-total-price  100
                                       :discount-percent 0
                                       :operations       ["pientalo"]}]
                    :meta            (just {:modified    pos?
                                            :modified-by {:id        (id-for-key sipoo)
                                                          :firstName "Simo"
                                                          :lastName  "Suurvisiiri"
                                                          :role      "authority"
                                                          :username  "sipoo"}})}))

        (fact "The catalogue can no longer be edited or republished"
          (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                   :price-catalogue-id draft-id
                   :edit {:row {:id                third-id
                                :product-constants {:muu-tunniste "My tag"}}})
          => (err :error.price-catalogue.wrong-state)
          (command sipoo :publish-price-catalogue
                   :organizationId "753-R"
                   :price-catalogue-id draft-id)
          => (err :error.price-catalogue.wrong-state))

        (fact "The catalogue rows can still be moved"
          (command sipoo :move-price-catalogue-row
                   :organizationId "753-R"
                   :price-catalogue-id draft-id
                   :row-id first-id
                   :direction "up") => ok?
          (->> (query sipoo :organization-price-catalogue
                      :organizationId "753-R"
                      :price-catalogue-id draft-id)
               :price-catalogue
               :rows
               (map :id))
          => [first-id third-id]
          (command sipoo :move-price-catalogue-row
                   :organizationId "753-R"
                   :price-catalogue-id draft-id
                   :row-id first-id
                   :direction "down") => ok?
          (->> (query sipoo :organization-price-catalogue
                      :organizationId "753-R"
                      :price-catalogue-id draft-id)
               :price-catalogue
               :rows
               (map :id))
          => [third-id first-id])

        (facts "Create new draft based on the earlier one"
          (fact "Success"
            (let [{:keys [draft]} (command sipoo :new-price-catalogue-draft
                                           :organizationId "753-R"
                                           :price-catalogue-id draft-id)
                  new-draft-id    (:id draft)]
              new-draft-id     => truthy
              new-draft-id =not=> draft-id
              draft => (just {:id              new-draft-id
                              :organization-id "753-R"
                              :type            "R"
                              :name            "Show me the money! (kopio)"
                              :state           "draft"
                              :valid-from      yesterday
                              :valid-until     (dec day-after-tomorrow)
                              :rows            [{:id                third-id
                                                 :code              "C"
                                                 :text              "Third item"
                                                 :unit              "vk"
                                                 :price-per-unit    30
                                                 :discount-percent  25
                                                 :operations        []
                                                 :product-constants {:kustannuspaikka  ""
                                                                     :alv              ""
                                                                     :laskentatunniste ""
                                                                     :muu-tunniste     "My tag"}}
                                                {:id               first-id
                                                 :code             "A1"
                                                 :text             "Updated first row"
                                                 :unit             "kpl"
                                                 :price-per-unit   10
                                                 :min-total-price  10
                                                 :max-total-price  100
                                                 :discount-percent 0
                                                 :operations       ["pientalo"]}]
                              :meta            (just {:modified    pos?
                                                      :modified-by {:id        (id-for-key sipoo)
                                                                    :firstName "Simo"
                                                                    :lastName  "Suurvisiiri"
                                                                    :role      "authority"
                                                                    :username  "sipoo"}})})
              (fact "Change the name"
                (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                         :price-catalogue-id new-draft-id
                         :edit {:name "New"}) => ok?)
              (fact "Change the valid period"
                (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                         :price-catalogue-id new-draft-id
                         :edit {:valid {:until (dec next-week)}}) => ok?)

              (fact "Update row first-id"
                (command sipoo :edit-price-catalogue-draft :organizationId "753-R"
                         :price-catalogue-id new-draft-id
                         :edit {:row {:id first-id :text "Second catalogue"}}) => ok?)

              (fact "Create, publish and delete catalogue."
                (let [{over-id :id rows :rows} (:draft (command sipoo :new-price-catalogue-draft
                                                                :organizationId "753-R"
                                                                :price-catalogue-id new-draft-id))]
                  over-id => truthy
                  (fact "New catalog is based on the new-draft-id catalogue"
                    (util/find-by-key :text "Second catalogue" rows) => truthy)
                  (command sipoo :edit-price-catalogue-draft
                           :organizationId "753-R"
                           :price-catalogue-id over-id
                           :edit {:row {:id             (-> rows first :id)
                                        :text           "Text"
                                        :price-per-unit 10
                                        :unit           "kpl"}}) => ok?
                  (command sipoo :edit-price-catalogue-draft
                           :organizationId "753-R"
                           :price-catalogue-id over-id
                           :edit {:valid {:from tomorrow}}) => ok?
                  (command sipoo :publish-price-catalogue
                           :organizationId "753-R"
                           :price-catalogue-id over-id) => ok?
                  (:price-catalogues (query sipoo :organization-price-catalogues
                                            :organizationId "753-R"))
                  => (just [(just {:id         draft-id  :state       "published"
                                   :name       "Show me the money!"
                                   :valid-from yesterday :valid-until (dec day-after-tomorrow)})
                            (just {:id          new-draft-id :state "draft" :name "New"
                                   :valid-until (dec next-week)})
                            (just {:id    over-id     :valid-from tomorrow
                                   :state "published" :name       "New (kopio)"})]
                           :in-any-order)

                  (fact "Deletion does not affect other catalogues."
                    (command sipoo :delete-price-catalogue
                             :organizationId "753-R"
                             :price-catalogue-id over-id) => ok?
                    (:price-catalogues (query sipoo :organization-price-catalogues
                                              :organizationId "753-R"))
                    => (just [(just {:id         draft-id  :state       "published"
                                     :name       "Show me the money!"
                                     :valid-from yesterday :valid-until (dec day-after-tomorrow)})
                              (just {:id          new-draft-id :state "draft" :name "New"
                                     :valid-until (dec next-week)})]
                             :in-any-order))

                  (fact "Create and delete draft"
                    (let [{bye-id :id} (:draft (command sipoo :new-price-catalogue-draft
                                                        :organizationId "753-R"))]
                      bye-id => truthy
                      (command sipoo :delete-price-catalogue
                               :organizationId "753-R"
                               :price-catalogue-id bye-id) => ok?))

                  (facts "Catalogues for an application"
                    (let [{app-id :id} (create-and-submit-application pena
                                                                      :propertyId sipoo-property-id
                                                                      :operation "pientalo"
                                                                      :address "Catalog Country")]
                      app-id => truthy
                      (fact "Drafts are not listed"
                        (:price-catalogues (query sonja :application-price-catalogues :id app-id))
                        => (just [(contains {:id   draft-id :state "published"
                                             :name "Show me the money!"})]))


                      (mongo/remove :price-catalogues "pseudo-catalog")

                      (fact "Application without catalog"
                        (let [{ya-app-id :id} (create-and-submit-application pena :propertyId sipoo-property-id
                                                                             :operation "ya-katulupa-maalampotyot"
                                                                             :address "Yolo Lu")]
                          ya-app-id => truthy
                          (:price-catalogues (query sonja :application-price-catalogues
                                                    :id ya-app-id))
                          => [])))))))))))

    (facts "No billing periods"
      (let [{draft-id :id
             rows     :rows} (:draft (command sipoo-ya :new-price-catalogue-draft
                                              :organizationId "753-YA"))
            setter           (fn [nbp] (command sipoo-ya :save-no-billing-periods
                                                :organizationId "753-YA"
                                                :price-catalogue-id draft-id
                                                :no-billing-periods nbp))
            getter           #(-> (query sipoo-ya :organization-price-catalogue
                                         :organizationId "753-YA"
                                         :price-catalogue-id draft-id)
                                  :price-catalogue
                                  :no-billing-periods)]
        draft-id => truthy
        (command sipoo-ya :edit-price-catalogue-draft
                 :organizationId "753-YA"
                 :price-catalogue-id draft-id
                 :edit {:row {:id             (-> rows first :id)
                              :text           "Text"
                              :price-per-unit 10
                              :unit           "kpl"}}) => ok?
        (fact "No billing periods can be set for drafts"
          (setter {:1 {:start "10.2.2020" :end "20.3.2020"}}) => ok?
          (getter) => {:1 {:start "10.2.2020" :end "20.3.2020"}})
        (fact "Multiple periods"
          (setter {:foo {:start "10.2.2020" :end "20.3.2020"}
                   :bar {:start "10.3.2020" :end "12.3.2020"}}) => ok?
          (getter) => {:foo {:start "10.2.2020" :end "20.3.2020"}
                       :bar {:start "10.3.2020" :end "12.3.2020"}})
        (fact "Clear periods"
          (setter {}) => ok?
          (getter) => {})

        (fact "Start cannot be before end"
          (setter {:1 {:start "10.4.2020" :end "20.3.2020"}})
          => {:ok      false
              :text    "error.price-catalogue.bad-no-billing-period"
              :period  {:start "10.4.2020" :end "20.3.2020"}
              :message "The end instant must be greater than the start instant"})
        (fact "The same day is OK"
          (setter {:good {:start "20.3.2020" :end "20.3.2020"}}) => ok?)
        (fact "Bad dates"
          (setter {:bad {:start "bad" :end "20.3.2020"}})
          => (just {:ok      false
                    :text    "error.price-catalogue.bad-no-billing-period"
                    :period  {:start "bad" :end "20.3.2020"}
                    :message truthy})
          (setter {:bad {:start "10.3.2020" :end ""}})
          => (just {:ok      false
                    :text    "error.price-catalogue.bad-no-billing-period"
                    :period  {:start "10.3.2020" :end ""}
                    :message truthy})
          (setter {:bad {:start "10.3.2020"}}) => fail?
          (setter {:bad {:start "10.3.2020" :end "2.4.2020blaah"}})
          => (just {:ok      false
                    :text    "error.price-catalogue.bad-no-billing-period"
                    :period  {:start "10.3.2020" :end "2.4.2020blaah"}
                    :message truthy})
          (setter {:bad {:start "10.3." :end "2.4.2020"}})
          => (just {:ok      false
                    :text    "error.price-catalogue.bad-no-billing-period"
                    :period  {:start "10.3." :end "2.4.2020"}
                    :message truthy})
          (setter {:bad {:start "42.3.2020" :end "2.4.2020"}})
          => (just {:ok      false
                    :text    "error.price-catalogue.bad-no-billing-period"
                    :period  {:start "42.3.2020" :end "2.4.2020"}
                    :message truthy}))
        (fact "Extra whitespace is OK"
          (setter {:ws {:start "   4.2.2020  " :end "  4.3.2020  "}}) => ok?
          (getter)  => {:ws {:start "4.2.2020" :end "4.3.2020"}})
        (fact "Periods can be set for published catalogues"
          (command sipoo-ya :edit-price-catalogue-draft
                   :organizationId "753-YA"
                   :price-catalogue-id draft-id
                   :edit {:valid {:from last-week}}) => ok?
          (command sipoo-ya :edit-price-catalogue-draft
                   :organizationId "753-YA"
                   :price-catalogue-id draft-id
                   :edit {:name "Yalla yalla"}) => ok?
          (command sipoo-ya :publish-price-catalogue
                   :organizationId "753-YA"
                   :price-catalogue-id draft-id) => ok?
          (setter {:1 {:start "1.3.2020" :end "2.4.2020"}}) => ok?
          (getter) => {:1 {:start "1.3.2020" :end "2.4.2020"}})

        (fact "Revert the catalogue back to draft"
          (command sipoo-ya :revert-price-catalogue-to-draft
                   :organizationId "753-YA"
                   :price-catalogue-id draft-id)
          => {:ok true :price-catalogue-id draft-id}
          (:price-catalogue (query sipoo-ya :organization-price-catalogue
                                   :organizationId "753-YA"
                                   :price-catalogue-id draft-id))
          => (contains {:id                 draft-id
                        :name               "Yalla yalla"
                        :valid-from         last-week
                        :state              "draft"
                        :no-billing-periods {:1 {:start "1.3.2020" :end "2.4.2020"}}
                        :meta               (just {:modified    pos?
                                                   :modified-by truthy})}))
        (fact "Draft cannot be reverted"
          (command sipoo-ya :revert-price-catalogue-to-draft
                   :organizationId "753-YA"
                   :price-catalogue-id draft-id)
          => (err :error.price-catalogue.wrong-state))))))
