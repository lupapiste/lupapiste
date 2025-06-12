(ns lupapalvelu.rest.dmcity-states-itest
  (:require [clojure.set :as set]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itu :refer [with-test-system]]
            [lupapalvelu.json :as json]
            [ring.util.http-predicates :as ruh])
  (:import [java.time ZoneId ZonedDateTime]))

(set! *warn-on-reflection* true)

(def operation "ya-kayttolupa-kattolumien-pudotustyot")
(def integration-user "matti-rest-api-user")

;;; Fixtures

(use-fixtures :once (fn [test-fn]
                      (with-test-system (-> {:fixture "minimal"}
                                            test-system/with-remote-components)
                        (test-fn))))

;;; Test helpers

(defn- submit!
  [{{:keys [body application-id]} :request
    :keys                         [options]}]
  (let [url   (format "%s/rest/application/%s/states" (itu/server-address) application-id)
        args  (merge {:throw-exceptions false
                      :basic-auth       [integration-user "vantaa"]
                      :content-type     :json
                      :body             (json/encode body)}
                     options)
        resp  (itu/http-put url args)
        json? (= "application/json;charset=utf-8"
                 (get-in resp [:headers "Content-Type"]))]
    (is json?)
    (if json?
      (update resp :body (fn [body] (json/decode body true)))
      resp)))

(defn- make-test-context!
  [{:keys [states]}]
  (let [bad-state-app (itu/create-and-open-application
                        itu/pena
                        :propertyId "09240900060124"
                        :operation operation)
        application   (itu/create-and-submit-application
                        itu/pena
                        :propertyId "09240900060124"
                        :operation operation)
        req           {:options {}
                       :request {:application-id (:id application)
                                 :body           {:states states}}}]
    {:bad-state-app bad-state-app
     :application   application
     :req           req}))

(defn- is-bad-request
  [{:keys [body] :as resp} text & errors]
  (is (ruh/bad-request? resp))
  (is (= {:status 400
          :ok     false
          :text   text
          :errors (into [] errors)}
         body)))

(defn- assert-no-side-effects
  [original-app]
  (let [updated-app (itu/query-application itu/pena (:id original-app))]
    (testing "application external state was not changed"
      (is (= (:external-state original-app)
             (:external-state updated-app))))))

(defn- timestamp
  ([yyyy mm dd]
   (timestamp yyyy mm dd 0 0 0))
  ([yyyy mm dd HH MM SS]
   (let [zdt (ZonedDateTime/of yyyy mm dd HH MM SS 0 (ZoneId/of "Europe/Helsinki"))]
     (-> zdt .toInstant .toEpochMilli))))

(defn- is-success
  "Verify the HTTP response and the various side-effects"
  [{:keys [body] :as resp} original-app expected-states]
  (is (ruh/success? resp))
  (is (= {:status 201
          :ok     true}
         body))
  (let [{:keys [external-state]} (itu/query-application itu/pena (:id original-app))]
    (is (int? (:received external-state)))
    (is (= expected-states
           (:states external-state)))))

(deftest rest-states-happy-path-i-test
  (let [states                    [{:state "submitted-date"
                                    :date  "2023-01-20"}
                                   {:state    "initial-meeting"
                                    :datetime "2023-12-01T12:34:56"}
                                   {:state   "registration-required"
                                    :enabled false}]
        {:keys [application req]} (make-test-context!
                                    {:states states})]
    (testing "states uploaded successfully"
      (let [resp (submit! req)]
        (is-success resp application [{:state "submitted-date"
                                       :date  (timestamp 2023 1 20)}
                                      {:state    "initial-meeting"
                                       :datetime (timestamp 2023 12 1 12 34 56)}
                                      {:state   "registration-required"
                                       :enabled false}])))

    (testing "states can be replaced"
      (let [states [{:state    "initial-meeting"
                     ;; Note we submit this in UTC..
                     :datetime "2023-12-01T12:34:56Z"}
                    {:state   "registration-required"
                     :enabled true}]
            req    (assoc-in req [:request :body :states] states)
            resp   (submit! req)]
        (is-success resp application [{:state    "initial-meeting"
                                       ;; time was correctly interpreted as UTC
                                       :datetime (timestamp 2023 12 1 14 34 56)}
                                      {:state   "registration-required"
                                       :enabled true}])))))

(deftest rest-states-failures-i-test
  (let [{:keys [application req bad-state-app]} (make-test-context!
                                                  {:states [{:state "submitted-date"
                                                             :date  "2023-01-20"}
                                                            {:state    "initial-meeting"
                                                             :datetime "2023-12-01T12:34:56"}
                                                            {:state   "registration-required"
                                                             :enabled false}]})]
    (testing "various failure conditions"

      (testing "Application in incorrect state"
        (let [req  (assoc-in req [:request :application-id] (:id bad-state-app))
              resp (submit! req)]
          (is (= {:status 403
                  :ok     false
                  :text   "Illegal application state"
                  :errors []}
                 (:body resp)))
          (assert-no-side-effects bad-state-app)))

      (testing "No user credentials provided"
        (let [req                     (assoc-in req [:options :basic-auth] [])
              {:keys [body] :as resp} (submit! req)]
          (is (ruh/forbidden? resp))
          (is (= {:status 403
                  :ok     false
                  :text   "Unauthenticated"
                  :errors []}
                 body))
          (assert-no-side-effects application)))

      (testing "User with no authorization provided"
        (let [req                     (assoc-in req [:options :basic-auth] ["sonja" "sonja"])
              {:keys [body] :as resp} (submit! req)]
          (is (ruh/unauthorized? resp))
          (is (= {:status 401
                  :ok     false
                  :text   "Unauthorized"
                  :errors []}
                 body))
          (assert-no-side-effects application)))

      (testing "invalid application id"
        (let [req  (assoc-in req [:request :application-id] "not-an-application-id")
              resp (submit! req)]
          (is-bad-request resp
                          "Invalid HTTP parameters"
                          {:field ["application-id"]
                           :error "not-valid"
                           :value "not-an-application-id"})
          (assert-no-side-effects application)))

      (testing "unknown application id"
        (let [req  (assoc-in req [:request :application-id] "LP-123-2023-12345")
              resp (submit! req)]
          (is-bad-request resp
                          "Application error"
                          {:field ["application-id"]
                           :error "application-not-found"
                           :value "LP-123-2023-12345"})
          (assert-no-side-effects application)))

      (testing "missing states"
        (let [req  (assoc-in req [:request :body] {})
              resp (submit! req)]
          (is-bad-request resp
                          "Invalid HTTP parameters"
                          {:field ["states"]
                           :error "missing-required-key"})
          (assert-no-side-effects application)))

      (testing "invalid state"
        (are [value] (let [req  (assoc-in req [:request :body] {:states value})
                           resp (submit! req)]
                       (is-bad-request resp
                                       "Invalid HTTP parameters"
                                       {:field ["states"]
                                        :error "not-valid"
                                        :value value})
                       (assert-no-side-effects application))
          ;; Things which are not states
          42
          3.141
          {:a "b"}
          "asd"))

      (testing "missing state field"
        (let [req  (update-in req [:request :body :states 0] dissoc :state)
              resp (submit! req)]
          (is-bad-request resp
                          "Invalid HTTP parameters"
                          {:field ["states" 0 "state"]
                           :error "missing-required-key"})
          (assert-no-side-effects application)))

      (testing "incorrectly named state field"
        (let [req  (update-in req [:request :body :states 0] set/rename-keys {:state :state-name})
              resp (submit! req)]
          (is-bad-request resp
                          "Invalid HTTP parameters"
                          {:field ["states" 0 "state"]
                           :error "missing-required-key"}
                          {:field ["states" 0 "state-name"]
                           :error "disallowed-key"})
          (assert-no-side-effects application)))

      (testing "unsupported state enumeration"
        (let [req  (update-in req [:request :body :states 0] assoc :state "[object Object]")
              resp (submit! req)]
          (is-bad-request resp
                          "Invalid HTTP parameters"
                          {:field    ["states" 0 "state"]
                           :error    "not-valid"
                           :value    "[object Object]"
                           :expected ["acceptance-date"
                                      "appeal-period"
                                      "apply-date"
                                      "decision-date"
                                      "display-decision-date"
                                      "display-end-date"
                                      "display-start-date"
                                      "end-date"
                                      "final-meeting"
                                      "initial-meeting"
                                      "order-date"
                                      "pending-date"
                                      "plan-accepted-date"
                                      "plan-draft-comments-end-date"
                                      "plan-draft-display-decision-date"
                                      "plan-draft-display-end-date"
                                      "plan-draft-display-start-date"
                                      "plan-enforced-date"
                                      "plan-pending-date"
                                      "plan-proposal-display-decision-date"
                                      "plan-proposal-display-end-date"
                                      "plan-proposal-display-start-date"
                                      "registered"
                                      "registration-required"
                                      "statements-board-date"
                                      "statements-city-date"
                                      "submitted-date"
                                      "survey-finished"
                                      "validation-required"]})
          (assert-no-side-effects application)))

      (testing "incorrectly formatted date"
        (let [req  (update-in req [:request :body :states 0] assoc :date "1.1.1970")
              resp (submit! req)]
          (is-bad-request resp
                          "Failed to validate states data"
                          {:field    ["states" 0 "date"]
                           :error    "not-valid"
                           :value    "1.1.1970"})
          (assert-no-side-effects application))))))
