(ns lupapalvelu.application-bulletins-api-itest
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.application-bulletins-api :as bulletin-api]
            [lupapalvelu.fixture.fixtures]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [create-and-submit-application fails-with? query unauthorized?
                                            with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as sade]))

(def test-hyvinkaa-apikey "5056e6d3aa24a1c901e6b9er")

(deftest hide-bulletin-op-description-in-certain-cases
  (with-test-system (-> {:fixture "bulletin-pate-orgs"}
                        test-system/with-remote-components)
    (let [application (create-and-submit-application test-hyvinkaa-apikey
                                                     :municipality "106"
                                                     :propertyId "10600000000000"
                                                     :address "Harjukatu 10"
                                                     :operation "muu-uusi-rakentaminen")
          app2        (create-and-submit-application test-hyvinkaa-apikey
                                                     :municipality "106"
                                                     :propertyId "10600000000000"
                                                     :address "Harjukatu 11"
                                                     :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
          app3        (create-and-submit-application test-hyvinkaa-apikey
                                                     :municipality "106"
                                                     :propertyId "10600000000000"
                                                     :address "Harjukatu 12"
                                                     :operation "kiinteistonmuodostus")
          app4        (create-and-submit-application test-hyvinkaa-apikey
                                                     :municipality "106"
                                                     :propertyId "10600000000000"
                                                     :address "Harjukatu 13"
                                                     :operation "poikkeamis")]

      (testing "Bulletin op description required"
        (is (= (:ok (query test-hyvinkaa-apikey :app-bulletin-op-description-required :id (:id application)))
               true)))
      (testing "Bulletin op description not required for YA"
        (is (fails-with? :error.unauthorized
                         (query test-hyvinkaa-apikey :app-bulletin-op-description-required :id (:id app2)))))
      (testing "Bulletin op description not required for kiinteistonmuodostus"
        (is (unauthorized? (query test-hyvinkaa-apikey :app-bulletin-op-description-required :id (:id app3)))))
      (testing "Bulletin op description not required for dmcity"
        (is (= (query test-hyvinkaa-apikey :app-bulletin-op-description-required :id (:id app4))
               {:ok   false
                :text "error.dmcity.enabled"}))))))

(defn- make-bulletin
  [{:keys [id now section appealPeriodStartsAt]}]
  {:_applicantIndex []
   :_id             id
   :address         "testikatu"
   :bulletinState   :verdictGiven
   :modified        (- now 10)
   :versions        [{:address                "testikatu"
                      :appealPeriodEndsAt     (+ appealPeriodStartsAt 100000)
                      :appealPeriodStartsAt   appealPeriodStartsAt
                      :applicant              nil
                      :application-id         id
                      :bulletinOpDescription  nil
                      :bulletinProcess        :kuulutus
                      :bulletinState          :verdictGiven
                      :bulletinVersionCreated (- now 10)
                      :category               :r
                      :location               [1 2]
                      :modified               (- now 10)
                      :municipality           "753"
                      :primaryOperation       {:created     (- now 30)
                                               :description nil
                                               :id          "op-mongo-id"
                                               :name        "kerrostalo-rivitalo"}
                      :propertyId             "75341600350006"
                      :state                  :verdictGiven
                      :verdictData            {:code    "hyvaksytty"
                                               :contact "Pekka Päättäjä"
                                               :section section
                                               :text    "foo"}
                      :verdictDate            (- now 5)}]})

(deftest get-application-bulletins-local-test
  (with-test-system {:fixture "minimal"}
    (let [now (sade/now)]
      (mongo/insert-batch :application-bulletins (shuffle [;; three bulletins on the same date
                                                           (make-bulletin {:now                  now
                                                                           :id                   "LP-1"
                                                                           :section              "1"
                                                                           :appealPeriodStartsAt now})
                                                           (make-bulletin {:now                  now
                                                                           :id                   "LP-2"
                                                                           :section              "2"
                                                                           :appealPeriodStartsAt now})
                                                           (make-bulletin {:now                  now
                                                                           :id                   "LP-3"
                                                                           :section              "100"
                                                                           :appealPeriodStartsAt now})
                                                           (make-bulletin {:now                  now
                                                                           :id                   "LP-8"
                                                                           :section              "1" ;; same section as LP-1!
                                                                           :appealPeriodStartsAt now})
                                                           ;; earlier bulletins with higher section nr.
                                                           (make-bulletin {:now                  now
                                                                           :id                   "LP-5"
                                                                           :section              "150"
                                                                           :appealPeriodStartsAt (dec now)})
                                                           (make-bulletin {:now                  now
                                                                           :id                   "LP-4"
                                                                           :section              "151"
                                                                           :appealPeriodStartsAt (dec now)})]))

      (testing "sort by appeal period start, then by section if necessary, and then by id"
        (is (= [;; newer bulletins first, largest section first
                "LP-3" ;; section 100 > 2
                "LP-2" ;; section 2 > 1
                "LP-8" ;; ID 8 > ID 1
                "LP-1"
                ;; older bulletins, largest section first
                "LP-4" ;; 151 > 150
                "LP-5"]
               (->> (bulletin-api/get-application-bulletins {:page 0
                                                             :sort {:field "appealPeriodStartsAt+section"}}
                                                            now)
                    :data
                    (mapv :id)))))
      (testing "sort by just appeal period start, then by id if necessary"
        (is (= [;; newer bulletins first
                "LP-8"
                "LP-3"
                "LP-2"
                "LP-1"
                ;; older bulletins
                "LP-5"
                "LP-4"]
               (->> (bulletin-api/get-application-bulletins {:page 0
                                                             :sort {:field "appealPeriodStartsAt"}}
                                                            now)
                    :data
                    (mapv :id))))))))
