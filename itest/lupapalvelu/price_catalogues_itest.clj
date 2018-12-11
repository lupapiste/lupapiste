(ns lupapalvelu.price-catalogues-itest
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as timeformat]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.invoice-api]
            [lupapalvelu.itest-util :refer [local-command local-query
                                            create-and-submit-application
                                            create-and-submit-local-application
                                            sonja pena sipoo sipoo-ya admin
                                            ok? fail?] :as itu]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.price-catalogues :as catalogues]
            [lupapalvelu.invoices.schemas :as invsc]
            [lupapalvelu.time-util :as tu]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]
            [sade.util :refer [to-millis-from-local-date-string
                               to-millis-from-local-datetime-string
                               format-timestamp-local-tz
                               to-finnish-date]]
            [schema.core :as sc]
            [taoensso.timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]))

(def timestamp to-millis-from-local-date-string)

(def time-format (timeformat/formatter "dd.MM.YYYY"))

(defn today []
  (timeformat/unparse-local-date time-format (t/today)))

(defn yesterday []
  (timeformat/unparse-local-date time-format (t/plus (t/today) (t/days -1))))

(defn catalogues-belong-to-org?
  [org-id catalogues]
  (->> catalogues
       (map :organization-id)
       (every? (fn [catalogue-org-id] (= org-id catalogue-org-id)))))

(defn belong-to-org? [org-id]
  (partial catalogues-belong-to-org? org-id))


(defn ensure-exists! [collection {:keys [id] :as doc}]
  (cond
    (not id) :fail
    (mongo/by-id collection id) :ok
    :else (do
            (mongo/insert collection doc)
            (if (mongo/by-id collection id)
              :ok
              :fail))))

(def dummy-user {:id                                        "penan-id"
                 :firstName                                 "pena"
                 :lastName                                  "panaani"
                 :role                                      "authority"
                 :username                                  "pena"})

(defn catalogue-request-with [properties]
  (merge {:valid-from-str "1.1.2019"
          :rows [{:code "12345"
                  :text "Taksarivi 1"
                  :unit "kpl"
                  :price-per-unit 23
                  :discount-percent nil
                  :min-total-price nil
                  :max-total-price nil
                  :operations ["toimenpide1" "toimenpide2"]}]}
         properties))

(defn catalogue-with [properties]
  (merge {:id "foo-id"
          :organization-id "753-R"
          :state "draft"
          :valid-from (timestamp "1.1.2000")
          :valid-until (timestamp "1.5.2001")
          :rows [{:code "12345"
                  :text "Taksarivi 1"
                  :unit "kpl"
                  :price-per-unit 23
                  :min-total-price nil
                  :max-total-price nil
                  :discount-percent 50
                  :operations ["toimenpide1" "toimenpide2"]}]
          :meta {:created (timestamp "01.12.2019")
                 :created-by dummy-user}}
         properties))

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "invoicing-enabled")

    (fact "organization-price-catalogues query"

          (fact "should return unauthorized response when user is not an organization admin"
                (let [response (-> (local-query sonja :organization-price-catalogues
                                                :organization-id "753-R"))]
                  response => fail?
                  (:text response) => "error.unauthorized"))

          (fact "should return unauthorized response when user is an or organization admin or another org"
                (let [response (-> (local-query sipoo-ya :organization-price-catalogues
                                                :organization-id "753-R"))]
                  response => fail?
                  (:text response) => "error.unauthorized"))

          (fact "should return empty collection when no price catalogues found for org-id"
                (let [response (-> (local-query sipoo :organization-price-catalogues
                                                :organization-id "753-R"))]
                  (:price-catalogues response) => []))

          (fact "should return one price catalogue when one inserted in db for the org-id"
                (let [test-price-catalogue (catalogue-with {:organization-id "753-R"
                                                            :valid-from (timestamp "1.1.1980")} )]
                  (ensure-exists! "price-catalogues" test-price-catalogue) => :ok
                  (let [response (local-query sipoo :organization-price-catalogues
                                              :organization-id "753-R")]
                    response => ok?
                    (:price-catalogues response) => (belong-to-org? "753-R")))))

    (fact "publish-price-catalogue command"

          (let [catalogue-request {:valid-from-str "1.1.2030"
                                   :rows [{:code "12345"
                                           :text "Taksarivi 1"
                                           :unit "kpl"
                                           :min-total-price 1
                                           :max-total-price 100
                                           :price-per-unit 23
                                           :discount-percent 50
                                           :operations ["toimenpide1" "toimenpide2"]}]}]




            (fact "should return unauthorized response when user is not an organization admin"
                  (let [response (-> (local-command sonja :publish-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue catalogue-request))]
                    response => fail?
                    (:text response) => "error.unauthorized"))

            (fact "should return unauthorized response when user is an or organization admin or another org"
                  (let [response (-> (local-command sipoo-ya :publish-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue catalogue-request))]
                    response => fail?
                    (:text response) => "error.unauthorized"))

            (fact "should return invalid-price-catalogue response when request data in not valid"
                  (let [response (-> (local-command sipoo :publish-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue {:announcement "I'm not valid price catalogue request data" }))]
                    response => fail?
                    (:text response) => "error.invalid-price-catalogue"))

            (fact "should return invalid response when valid-from-str is today"
                  (let [response (-> (local-command sipoo :publish-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue (assoc catalogue-request :valid-from-str (today))))]
                    response => fail?
                    (:text response) => "error.price-catalogue.incorrect-date"))

            (fact "should return invalide response when valid-from-str is yesterday"
                  (let [response (-> (local-command sipoo :publish-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue (assoc catalogue-request :valid-from-str (yesterday))))]
                    response => fail?
                    (:text response) => "error.price-catalogue.incorrect-date"))


            (fact "should save the price catalogue to db and return an ok response when"

                  (fact "all fields have values"
                        (let [response (local-command sipoo :publish-price-catalogue
                                                      :organization-id "753-R"
                                                      :price-catalogue catalogue-request)]
                          response => ok?

                          (let [{:keys [valid-from rows] :as new-catalogue} (mongo/by-id "price-catalogues" (:price-catalogue-id response))]
                            (sc/validate invsc/PriceCatalogue new-catalogue)
                            (to-finnish-date valid-from) => (:valid-from-str catalogue-request)
                            rows => (:rows catalogue-request))))

                  (fact "optional fields have nil value"
                        (let [catalogue-request {:valid-from-str "1.1.2019"
                                                 :rows [{:code "12345"
                                                         :text "Taksarivi 1"
                                                         :unit "kpl"
                                                         :price-per-unit 23
                                                         :discount-percent nil
                                                         :min-total-price nil
                                                         :max-total-price nil
                                                         :operations ["toimenpide1" "toimenpide2"]}]}
                              response (local-command sipoo :publish-price-catalogue
                                                      :organization-id "753-R"
                                                      :price-catalogue catalogue-request)]
                          response => ok?
                          (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))]
                            (sc/validate invsc/PriceCatalogue new-catalogue)

                            (to-finnish-date (:valid-from new-catalogue)) => (:valid-from-str catalogue-request)
                            (:rows new-catalogue) => (:rows catalogue-request))))

                  (fact "operations is empty"
                        (let [catalogue-request {:valid-from-str "1.1.2019"
                                                 :rows [{:code "12345"
                                                         :text "Taksarivi 1"
                                                         :unit "kpl"
                                                         :price-per-unit 23
                                                         :discount-percent 50
                                                         :min-total-price 10
                                                         :max-total-price 100
                                                         :operations []}]}
                              response (local-command sipoo :publish-price-catalogue
                                                      :organization-id "753-R"
                                                      :price-catalogue catalogue-request)]
                          response => ok?

                          (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))]
                            (sc/validate invsc/PriceCatalogue new-catalogue)
                            (to-finnish-date (:valid-from new-catalogue)) => (:valid-from-str catalogue-request)
                            (:rows new-catalogue) => (:rows catalogue-request)))))

            (fact  "should set the valid-until field for the previous price catalogue as the day before new catalogues valid-from when valid-until is not previously set"

                   (let [previous-published-catalogue (catalogue-with {:id "previous-published"
                                                                       :valid-from (timestamp "1.2.2020")
                                                                       :valid-until nil
                                                                       :state "published"})
                         previous-draft-catalogue (catalogue-with {:id "previous-draft"
                                                                   :valid-from (timestamp "5.2.2020")
                                                                   :valid-until nil
                                                                   :state "draft"})
                         new-catalogue-req (catalogue-request-with {:valid-from-str "10.2.2020"})]

                     (ensure-exists! "price-catalogues" previous-published-catalogue) => :ok
                     (ensure-exists! "price-catalogues" previous-draft-catalogue) => :ok

                     (let [response (local-command sipoo :publish-price-catalogue
                                                   :organization-id "753-R"
                                                   :price-catalogue new-catalogue-req)]
                       response => ok?

                       (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))
                             previous-published-catalogue-in-db (mongo/by-id "price-catalogues" (:id previous-published-catalogue))]

                         (sc/validate invsc/PriceCatalogue new-catalogue)
                         (sc/validate invsc/PriceCatalogue previous-published-catalogue-in-db)
                         (to-finnish-date (:valid-until previous-published-catalogue-in-db)) => "9.2.2020"))))

            (fact  "should not update valid-until of previous catalogue if the current value is before the valid-from of the new catalogue"

                   (let [previous-published-catalogue-with-valid-until (catalogue-with {:id "previous-published-with-valid-until-before-new-cat"
                                                                                        :valid-from (timestamp "15.2.2020")
                                                                                        :valid-until (timestamp "18.2.2020")
                                                                                        :state "published"})

                         new-catalogue-req (catalogue-request-with {:valid-from-str "20.2.2020"})]

                     (ensure-exists! "price-catalogues" previous-published-catalogue-with-valid-until) => :ok

                     (let [response (local-command sipoo :publish-price-catalogue
                                                   :organization-id "753-R"
                                                   :price-catalogue new-catalogue-req)]
                       response => ok?

                       (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))
                             previous-pub-cat-with-valid-until-in-db (mongo/by-id "price-catalogues" (:id previous-published-catalogue-with-valid-until))]

                         (sc/validate invsc/PriceCatalogue new-catalogue)
                         (sc/validate invsc/PriceCatalogue previous-pub-cat-with-valid-until-in-db)
                         (to-finnish-date (:valid-until previous-pub-cat-with-valid-until-in-db)) => "18.2.2020"))))

            (fact  "should update valid-until of previous catalogue if the current value is after the valid-from of the new catalogue"

                   (let [previous-published-catalogue-with-valid-until (catalogue-with {:id "previous-published-with-valid-until-after-new-cat"
                                                                                        :valid-from (timestamp "21.2.2020")
                                                                                        :valid-until (timestamp "28.2.2020")
                                                                                        :state "published"})

                         new-catalogue-req (catalogue-request-with {:valid-from-str "25.2.2020"})]

                     (ensure-exists! "price-catalogues" previous-published-catalogue-with-valid-until) => :ok

                     (let [response (local-command sipoo :publish-price-catalogue
                                                   :organization-id "753-R"
                                                   :price-catalogue new-catalogue-req)]
                       response => ok?

                       (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))
                             previous-pub-cat-with-valid-until-in-db (mongo/by-id "price-catalogues" (:id previous-published-catalogue-with-valid-until))]

                         (sc/validate invsc/PriceCatalogue new-catalogue)
                         (sc/validate invsc/PriceCatalogue previous-pub-cat-with-valid-until-in-db)
                         (to-finnish-date (:valid-until previous-pub-cat-with-valid-until-in-db)) => "24.2.2020"))))

            (fact  "should set the valid-until field for the new catalogue as the day before the valid-from of the next published catalogue"

                   (let [next-draft-catalogue     (catalogue-with {:id "later-draft" :valid-from (timestamp "8.3.2020") :state "draft"})
                         next-published-catalogue (catalogue-with {:id "later-pub-1" :valid-from (timestamp "1.4.2020") :state "published"})
                         much-later-catalogue     (catalogue-with {:id "later-pub-2" :valid-from (timestamp "1.5.2020") :state "published"})
                         new-catalogue-req (catalogue-request-with {:valid-from-str "1.3.2020"})]

                     (ensure-exists! "price-catalogues" next-draft-catalogue) => :ok
                     (ensure-exists! "price-catalogues" next-published-catalogue) => :ok
                     (ensure-exists! "price-catalogues" much-later-catalogue) => :ok

                     (let [response (local-command sipoo :publish-price-catalogue
                                                   :organization-id "753-R"
                                                   :price-catalogue new-catalogue-req)]
                       response => ok?

                       (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))]

                         (sc/validate invsc/PriceCatalogue new-catalogue)
                         (to-finnish-date (:valid-until new-catalogue)) => "31.3.2020"))))


            (fact "should replace published catalogues that have the same valid-from as the new catalogue"

                  (let [same-day-published-catalogue-1 (catalogue-with {:id "same-day-pub-cat-1" :valid-from (timestamp "1.6.2020") :state "published"})
                        same-day-published-catalogue-2 (catalogue-with {:id "same-day-pub-cat-2" :valid-from (timestamp "1.6.2020") :state "published"})
                        same-day-draft-catalogue       (catalogue-with {:id "same-day-draft-cat" :valid-from (timestamp "1.6.2020") :state "draft"})
                        new-catalogue-req (catalogue-request-with {:valid-from-str "1.6.2020"})]

                    (ensure-exists! "price-catalogues" same-day-published-catalogue-1) => :ok
                    (ensure-exists! "price-catalogues" same-day-published-catalogue-2) => :ok
                    (ensure-exists! "price-catalogues" same-day-draft-catalogue) => :ok

                     (let [response (local-command sipoo :publish-price-catalogue
                                                   :organization-id "753-R"
                                                   :price-catalogue new-catalogue-req)]

                       response => ok?


                       (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))
                             same-day-pub-cat-1-in-db (mongo/by-id "price-catalogues" (:id same-day-published-catalogue-1))
                             same-day-pub-cat-2-in-db (mongo/by-id "price-catalogues" (:id same-day-published-catalogue-2))
                             same-day-draft-cat-in-db  (mongo/by-id "price-catalogues" (:id same-day-draft-catalogue))]

                         (sc/validate invsc/PriceCatalogue new-catalogue)

                         same-day-pub-cat-1-in-db => nil
                         same-day-pub-cat-2-in-db => nil
                         same-day-draft-cat-in-db => same-day-draft-catalogue))))

            (fact "should set the valid-until timestamp for the previous catalogue at the end of the day"
                  (let [prev-catalogue (catalogue-with {:id "prev-cat-with-correct-valid-until" :state "published"
                                                        :valid-from (timestamp "1.8.2020") :valid-until nil})
                        new-catalogue-req (catalogue-request-with {:valid-from-str "5.8.2020"})]
                    (ensure-exists! :price-catalogues prev-catalogue) => :ok

                    (local-command sipoo :publish-price-catalogue :organization-id "753-R" :price-catalogue new-catalogue-req)

                    (-> (mongo/by-id :price-catalogues "prev-cat-with-correct-valid-until")
                        :valid-until
                        (format-timestamp-local-tz "d.M.YYYY HH:mm:ss:SSS"))
                    => "4.8.2020 23:59:59:999"))))

    (fact "application-catalogue"
          (fact "finds the correct one when all catalogues are in the state published"

                (let [earlier (catalogue-with {:id "1" :valid-from (timestamp "2.1.2021") :valid-until (timestamp "3.2.2021") :organization-id "753-R" :state "published"})
                      valid   (catalogue-with {:id "2" :valid-from (timestamp "4.2.2021") :valid-until (timestamp "4.5.2021") :organization-id "753-R" :state "published"})
                      later   (catalogue-with {:id "3" :valid-from (timestamp "5.5.2021") :valid-until nil                    :organization-id "753-R" :state "published"})]

                  (with-redefs [catalogues/fetch-price-catalogues (fn [organization-id] [earlier valid later])
                                catalogues/submitted-timestamp (fn [application] (timestamp "3.3.2021"))]

                    (let [application (create-and-submit-local-application pena
                                                                           :operation "pientalo"
                                                                           :address "Kaivokatu 1")
                          response (local-query sonja :application-price-catalogue
                                                :id (:id application))]

                      response => ok?

                      (get-in response [:price-catalogue :id]) => "2"))))

          (fact "returns error when no valid price catalogue found"

                (let [later1 (catalogue-with {:id "1" :valid-from (timestamp "2.1.2021") :valid-until (timestamp "3.2.2021") :organization-id "753-R" :state "published"})
                      later2 (catalogue-with {:id "2" :valid-from (timestamp "4.2.2021") :valid-until (timestamp "4.5.2021") :organization-id "753-R" :state "published"})]

                  (with-redefs [catalogues/fetch-price-catalogues (fn [organization-id] [later1 later2])
                                catalogues/submitted-timestamp (fn [application] (timestamp "3.3.2018"))]

                    (let [application (create-and-submit-local-application pena
                                                                           :operation "pientalo"
                                                                           :address "Kaivokatu 1")
                          response (local-query sonja :application-price-catalogue
                                                :id (:id application))]

                      response => fail?
                      (:text response) => "error.application-valid-unique-price-catalogue-not-found")))))))
