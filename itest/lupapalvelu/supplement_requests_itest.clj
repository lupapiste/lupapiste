(ns lupapalvelu.supplement-requests-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.util :as util]))

(defn now-ts [change-fn]
  (-> (date/now) (change-fn) (date/timestamp)))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Supplement request permit type"
    (let [r-app (create-and-submit-application pena :operation "kerrostalo-rivitalo"
                                               :propertyId sipoo-property-id
                                               :x 406898.625 :y 6684125.375
                                               :address "Hitantine 108")]
      (fact "R permit can not have supplement requests"
        (command sonja :create-supplement-request :id (:id r-app)
                 :due-date (now-ts #(date/plus % :days 2))
                 :text [{:insert "Vaadin täydennystä"}])
        => (err :error.invalid-permit-type))))

  (facts "Full supplement request process"
    (let [app      (create-and-submit-application pena :operation "lannan-varastointi"
                                                  :propertyId oulu-property-id
                                                  :x 430109.3125 :y 7210461.375
                                                  :address "Oulu 10")
          app-id   (:id app)
          due-date (now-ts #(date/plus % :days 2))]

      (facts "Authority creates supplement request"
        (fact "Due date must be timestamp"
          (command olli :create-supplement-request :id app-id
                   :due-date "tomorrow"
                   :text [{:insert "Vaadi tädenystä"}])
          => (err :error.illegal-timestamp))
        (fact "Text cannot be blank"
          (command olli :create-supplement-request :id app-id
                   :due-date due-date
                   :text [{:insert "  "}])
          => (err :error.missing-parameters))
        (fact "Success"
          (command olli :create-supplement-request :id app-id
                   :due-date (- due-date 50000)
                   :text [{:insert "Vaadi tädenystä"}])
          => ok?))

      (fact "Applicant can't create a supplement request"
        (command pena :create-supplement-request :id app-id
                 :due-date due-date
                 :text [{:insert "Vaadin oikeutta"}])
        => unauthorized?)

      (fact "Draft supplement request is visible to authority"
        (let [requests     (:supplement-requests (query olli :get-supplement-requests :id app-id))
              {req-id :id
               :as    req} (first requests)]
          (count requests) => 1
          (:text req) => [{:insert "Vaadi tädenystä"}]
          (:due-date req) => (- due-date 50000)
          (-> req :state keyword) => :draft

          (fact "Draft supplement request is not visible to applicant"
            (let [requests (:supplement-requests (query pena :get-supplement-requests :id app-id))]
              (count requests) => 0))

          (fact "Applicant cannot send the supplement request"
            (command pena :send-supplement-request :id app-id
                     :request-id req-id)
            => unauthorized?
            (fact "Applicant cannot update the supplement request"
              (command pena :update-supplement-request :id app-id
                       :request-id req-id
                       :due-date 123456
                       :text [{:insert "what?"}])
              => unauthorized?))

          (facts "Authority updates supplement request"
            (fact "Due date must be timestamp"
              (command olli :update-supplement-request :id app-id
                       :request-id req-id
                       :due-date "tomorrow"
                       :text [{:insert "Vaadin täydennystä"}])
              => (err :error.illegal-timestamp))
            (fact "Text cannot be blank"
              (command olli :update-supplement-request :id app-id
                       :request-id req-id
                       :due-date due-date
                       :text [{:insert "  "}])
              => (err :error.missing-parameters))
            (fact "Success"
              (command olli :update-supplement-request :id app-id
                       :request-id req-id
                       :due-date due-date
                       :text [{:insert     "Vaadin täydennystä"
                               :attributes {:link  " LUPAPISTE.FI "
                                            :color "violet"}}])
              => ok?))

          (fact "Authority can send the supplement request"
            (command olli :send-supplement-request :id app-id
                     :request-id req-id) => ok?)

          (fact "Requested supplement request is visible to applicant"
            (->> (query pena :get-supplement-requests :id app-id)
                 :supplement-requests
                 (util/find-by-id req-id))
            => (contains {:due-date due-date
                          :text     [{:insert     "Vaadin täydennystä"
                                      :attributes {:link "http://lupapiste.fi"}}]
                          :state    "requested"}))

          (fact "Sent request can't be deleted"
            (command olli :delete-supplement-request :id app-id
                     :request-id (:id req))
            => (err :error.illegal-state))

          (fact "Applicant can reply to the supplement request"
            (command pena :reply-supplement-request :id app-id
                     :request-id req-id) => ok?)

          (fact "Applicant can't mark the supplement request done"
            (command pena :mark-supplement-request-done :id app-id
                     :request-id req-id)
            => unauthorized?)

          (fact "Authority can mark the supplement request done"
            (command olli :mark-supplement-request-done :id app-id
                     :request-id req-id) => ok?
            (let [requests (:supplement-requests (query olli :get-supplement-requests :id app-id))
                  req      (first requests)]
              (count requests) => 1
              (:replied req) => some?
              (-> req :state keyword) => :done))))

      (fact "Authority can delete unsent supplement request"
        (let [{:keys [request-id]} (command olli :create-supplement-request :id app-id
                                            :due-date due-date
                                            :text [{:insert "Vaadin täydennystä taas"}])]
          (command olli :delete-supplement-request :id app-id
                   :request-id request-id) => ok?
          (let [requests (:supplement-requests (query olli :get-supplement-requests :id app-id))
                req      (first requests)]
            (count requests) => 1
            (-> req :state keyword) => :done))))))
