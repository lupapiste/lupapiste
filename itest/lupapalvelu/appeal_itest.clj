(ns lupapalvelu.appeal-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.mongo :as mongo]))

(apply-remote-minimal)

(facts "Creating appeal"
  (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
    app-id => string?
    (fact "Can't add appeal before verdict"
      (command sonja :create-appeal
               :id app-id
               :targetId (mongo/create-id)
               :type "appeal"
               :appellant "Pena"
               :made 123456
               :text "foo") => (partial expected-failure? :error.command-illegal-state))

    (let [{vid :verdict-id} (give-verdict sonja app-id :verdictId "321-2016")]
      vid => string?
      (fact "wrong verdict ID"
        (command sonja :create-appeal
                 :id app-id
                 :targetId (mongo/create-id)
                 :type "appeal"
                 :appellant "Pena"
                 :made 123456
                 :text "foo") => (partial expected-failure? :error.verdict-not-found))
      (fact "successful appeal"
        (command sonja :create-appeal
                 :id app-id
                 :targetId vid
                 :type "appeal"
                 :appellant "Pena"
                 :made (now)
                 :text "foo") => ok?)
      (fact "text is optional"
        (command sonja :create-appeal
                 :id app-id
                 :targetId vid
                 :type "rectification"
                 :appellant "Pena"
                 :made (now)) => ok?)
      (fact "appeal is saved to application to be viewed"
        (map :type (:appeals (query-application pena app-id))) => (just ["appeal" "rectification"]))
      (fact "appeal query is OK"
        (let [response-data (:data (query pena :appeals :id app-id))]
          (keys response-data) => (just [:appeals :appealVerdicts])
          (count (:appeals response-data)) => 2
          (count (:appealVerdicts response-data)) => 0)))))

(facts "Creating appeal verdicts"
  (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
    app-id => string?
    (fact "Can't add appeal before verdict"
      (command sonja :create-appeal-verdict
               :id app-id
               :targetId (mongo/create-id)
               :giver "Teppo"
               :made 123456
               :text "foo") => (partial expected-failure? :error.command-illegal-state))

    (let [{vid :verdict-id} (give-verdict sonja app-id :verdictId "321-2016")]
      vid => string?
      (fact "wrong verdict ID"
        (command sonja :create-appeal-verdict
                 :id app-id
                 :targetId (mongo/create-id)
                 :giver "Teppo"
                 :made 123456
                 :text "foo") => (partial expected-failure? :error.verdict-not-found))
      (fact "an appeal must exists before creating verdict appeal"
        (command sonja :create-appeal-verdict
                 :id app-id
                 :targetId vid
                 :giver "Teppo"
                 :made (now)
                 :text "foo") => (partial expected-failure? :error.appeals-not-found))
      (fact "first create appeal"
        (command sonja :create-appeal
                 :id app-id
                 :targetId vid
                 :type "rectification"
                 :appellant "Pena"
                 :made (now)
                 :text "rectification 1") => ok?)
      (fact "... then try to create invalid appeal verdict"
        (command sonja :create-appeal-verdict
                 :id app-id
                 :targetId vid
                 :giver "Teppo"
                 :made "18.3.2016"
                 :text "verdict for rectification 1") => (partial expected-failure? :error.invalid-appeal-verdict))
      (fact "... then actually create a valid appeal verdict"
        (command sonja :create-appeal-verdict
                 :id app-id
                 :targetId vid
                 :giver "Teppo"
                 :made (now)
                 :text "verdict for rectification 1") => ok?)

      (fact "appeal query is OK after giving appeal and appeal verdict"
        (let [response-data (:data (query pena :appeals :id app-id))]
          (keys response-data) => (just [:appeals :appealVerdicts])
          (count (:appeals response-data)) => 1
          (count (:appealVerdicts response-data)) => 1)))))
