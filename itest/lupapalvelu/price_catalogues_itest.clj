(ns lupapalvelu.price-catalogues-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer [local-command local-query
                                            create-and-submit-application
                                            create-and-submit-local-application
                                            sonja pena sipoo sipoo-ya
                                            ok? fail?] :as itu]
            [lupapalvelu.invoice-api]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]
            [sade.util :refer [to-millis-from-local-date-string]]
            [taoensso.timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]
            [lupapalvelu.price-catalogues :as catalogues]))

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
                 :email                                     "pena@panaani.fi"
                 :username                                  "pena"})

(defn application-with [properties]
  (let [dummy-application {:id "APP-ID-1"
                           :organization-id "HC_ORG"
                           :state "draft"}]
    (merge dummy-application properties)))

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "minimal")

    (defn dummy-submitted-application []
      (create-and-submit-local-application
       pena
       :operation "pientalo"
       :x "385770.46" :y "6672188.964"
       :address "Kaivokatu 1"))

    (fact "organization-price-catalogues"

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
            (let [test-price-catalogue {:id "bar-id"
                                        :organization-id "753-R"
                                        :state "draft"
                                        :valid-from (to-millis-from-local-date-string "01.01.2019")
                                        :valid-until (to-millis-from-local-date-string "01.02.2019")
                                        :rows [{:code "12345"
                                                :text "Taksarivi 1"
                                                :unit "kpl"
                                                :price-per-unit 23
                                                :discount-percent 50
                                                :operations ["toimenpide1" "toimenpide2"]
                                                }]
                                        :meta {:created (to-millis-from-local-date-string "01.10.2018")
                                               :created-by dummy-user}}]
              (ensure-exists! "price-catalogues" test-price-catalogue) => :ok
              (let [response (local-query sipoo :organization-price-catalogues
                                          :organization-id "753-R")]
                response => ok?
                (:price-catalogues response) => (belong-to-org? "753-R")))))

    (fact "insert-price-catalogue command"

          (let [catalogue-request {:valid-from (to-millis-from-local-date-string "01.01.2019")
                                   :rows [{:code "12345"
                                           :text "Taksarivi 1"
                                           :unit "kpl"
                                           :price-per-unit 23
                                           :discount-percent 50
                                           :operations ["toimenpide1" "toimenpide2"]}]}]


            (fact "should return unauthorized response when user is not an organization admin"
                  (let [response (-> (local-command sonja :insert-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue catalogue-request))]
                    response => fail?
                    (:text response) => "error.unauthorized"))

            (fact "should return unauthorized response when user is an or organization admin or another org"
                  (let [response (-> (local-command sipoo-ya :insert-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue catalogue-request))]
                    response => fail?
                    (:text response) => "error.unauthorized"))

            (fact "should return invalid-price-catalogue response when request data in not valid"
                  (let [response (-> (local-command sipoo :insert-price-catalogue
                                                    :organization-id "753-R"
                                                    :price-catalogue {:announcement "I'm not valid price catalogue request data" }))]
                    response => fail?
                    (:text response) => "error.invalid-price-catalogue"))


            (fact "should save the price catalogue to db and return an ok response"
                  (let [response (local-command sipoo :insert-price-catalogue
                                                :organization-id "753-R"
                                                :price-catalogue catalogue-request)]
                    response => ok?

                    (let [new-catalogue (mongo/by-id "price-catalogues" (:price-catalogue-id response))]
                      (catalogues/validate-price-catalogue new-catalogue)
                      (select-keys new-catalogue [:valid-from :rows]) => catalogue-request)))))))
