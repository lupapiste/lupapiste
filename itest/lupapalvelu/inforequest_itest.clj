(ns lupapalvelu.inforequest-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(apply-remote-minimal)
(last-email)

(facts "inforequest workflow"
  (let [{id :id :as resp} (create-app pena :messages ["hello"] :infoRequest true :propertyId sipoo-property-id)]

    resp => ok?

    (fact "no emails were sent, because organization doesn't have notification emails in use"
      (last-email) => nil?)

    (fact "inforequest was created with message"
      (let [application (query-application pena id)]
        application => (in-state? "info")
        (:opened application) => truthy
        (count (:comments application)) => 1
        (-> (:comments application) first :text) => "hello"))

    (fact "Veikko can not assign inforequest to himself"
      (command veikko :assign-application :id id :assigneeId veikko-id) => not-accessible?)

    (fact "Sonja can assign inforequest to herself"
      (command sonja :assign-application :id id :assigneeId sonja-id) => ok?)

    (fact "Sonja can mark inforequest answered"
      (command sonja :can-mark-answered :id id) => ok?

      (fact "Pena did not get email because there was no message"
        (last-email) => nil?))

    (fact "When Commenting on inforequest marks it answered"
      (query-application pena id)    => (in-state? :info)
      (comment-application sonja id false) => ok?
      (query-application pena id)    => (in-state? :answered))

    (fact "Sonja can no longer mark inforequest answered"
      (command sonja :can-mark-answered :id id) => fail?)

    (fact "Pena can convert-to-application"
      (command pena :convert-to-application :id id) => ok?

      (let [application (query-application pena id)]
        application => (in-state? :open)
        (-> application :history last :state) => "open")))


  (fact "Pena cannot create app for organization that has inforequests disabled"
  (let [resp  (create-app pena :infoRequest true :propertyId "99800000000000")]
    resp =not=> ok?
    (:text resp) => "error.inforequests-disabled"))

  (fact "Pena can cancel inforequest he created"
    (let [{application-id :id :as resp} (create-app pena :infoRequest true :propertyId sipoo-property-id)]
      resp => ok?
      (command pena :cancel-inforequest :id application-id) => ok?
      (fact "Sonja is also allowed to cancel inforequest"
        (allowed? :cancel-inforequest :id application-id)))))

(facts "Inforequest notification email"
  (last-email) ; clear box

  (command sipoo :set-organization-inforequest-notification-email :emails "testi@example.com") => ok?

  (create-app pena :messages ["hello"] :infoRequest true :propertyId sipoo-property-id) => ok?
  (let [email (last-email)]
    (fact "Organization get's notification email"
      (:to email) => (contains "testi@example.com")
      (:subject email) => (contains "Neuvontapyynt\u00f6 Lupapisteess\u00e4"))))

(facts "Open inforequest"
  ; Reset emails
  (last-email)

  (let [application-id (create-app-id pena :propertyId oir-property-id :infoRequest true :address "OIR")
        application    (query-application pena application-id)
        email          (last-email)
        [url token lang] (re-find #"(?sm)/api/raw/openinforequest\?token-id=([A-Za-z0-9-]+)&lang=([a-z]{2})" (get-in email [:body :plain] ""))
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :follow-redirects false
                :throw-exceptions false}]

    (fact "Inforequest was created"
      (:infoRequest application) => true)

    (fact "Inforequest is an open inforequest"
      (:openInfoRequest application) => true)

    (fact "Auhtority receives email about the new oir"
      (:to email) => "erajorma@example.com"
      (:subject email) => "Lupapiste: Neuvontapyynt\u00f6 Lupapisteess\u00e4"
      (count token) => pos?)

    (fact "Clicking the link redirects to oir page"
      (let [expected-location (str (server-address) "/app/fi/oir#!/inforequest/")
           resp (http-get (str (server-address) url) params)]
       (get-in resp [:headers "location"]) =>  #(sade.strings/starts-with % expected-location)))

    (comment-application pena application-id false) => ok?

    (fact "Auhtority receives email about the comment"
      (let [email          (last-email)
           [_ token lang] (re-find #"(?sm)/api/raw/openinforequest\?token-id=([A-Za-z0-9-]+)&lang=([a-z]{2})" (get-in email [:body :plain] ""))]
       (:to email) => "erajorma@example.com"
       (:subject email) => "Lupapiste: Neuvontapyynt\u00f6 Lupapisteess\u00e4"
       (count token) => pos?))

    (fact "Admin can not convert inforequests to normal yet"
      (command admin :convert-to-normal-inforequests :organizationId (:organization application)) => fail?)

    (fact "Admin disables open inforequests"
      (command admin :update-organization
        :permitType (:permitType application)
        :municipality (:municipality application)
        :inforequestEnabled true
        :applicationEnabled false
        :openInforequestEnabled false
        :openInforequestEmail  "erajorma@example.com"
        :opening nil) => ok?)

    (fact "Admin converts inforequests to normal"
      (let [resp (command admin :convert-to-normal-inforequests :organizationId (:organization application))]
        resp => ok?
        (:n resp) => 1))

    (fact "Token no longer works"
      (http-get (str (server-address) url) params) => http404?)

    (fact "Application is no longer oir"
      (let [application (query-application pena application-id)]
        (:infoRequest application) => true
        (:openInfoRequest application) => false))
    )
  )
