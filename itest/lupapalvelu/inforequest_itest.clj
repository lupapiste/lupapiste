(ns lupapalvelu.inforequest-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)
(last-email)

(defn inforequest-with-handler
  "Creates (as Pena) a new inforequest with Sonja as handler. Returns app-id."
  []
  (let [app-id (create-app-id pena :messages ["Too hot to handle!"]
                              :infoRequest true
                              :propertyId sipoo-property-id)]
    (command sonja :upsert-application-handler :id app-id
             :userId sonja-id :roleId sipoo-general-handler-id) => ok?
    app-id))

(defn has-handlers? [app-id]
  (boolean (some-> (query pena :application-handlers :id app-id)
                   :handlers
                   seq)))

(facts "inforequest workflow"
  (let [id (create-app-id pena :messages ["hello"] :infoRequest true :propertyId sipoo-property-id)]
    (fact "no emails were sent, because organization doesn't have notification emails in use"
      (last-email) => nil?)

    (fact "inforequest was created with message"
      (let [application (query-application pena id)]
        application => (in-state? "info")
        (:opened application) => truthy
        (count (:comments application)) => 1
        (-> (:comments application) first :text) => "hello"
        (fact "Pena's phone"
          (:applicantPhone application) => "0102030405")))

    (fact "Veikko can not assign inforequest to himself"
      (command veikko :upsert-application-handler :id id :userId veikko-id :roleId sipoo-general-handler-id) => not-accessible?)

    (fact "Sonja can assign inforequest to herself"
      (command sonja :upsert-application-handler :id id :userId sonja-id :roleId sipoo-general-handler-id) => ok?)

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
        (-> application :history last :state) => "open"))

    (fact "(Converted) application cannot be (re)converted"
      (command pena :convert-to-application :id id) => fail?))


  (fact "Pena cannot create app for organization that has inforequests disabled"
  (let [resp  (create-app pena :infoRequest true :propertyId "98100000000000")]
    resp =not=> ok?
    (:text resp) => "error.inforequests-disabled"))

  (fact "Pena can cancel inforequest he created"
    (let [application-id (create-app-id pena :infoRequest true :propertyId sipoo-property-id)]
      (command pena :cancel-inforequest :id application-id) => ok?
      (fact "Sonja is also allowed to cancel inforequest"
        (allowed? :cancel-inforequest :id application-id)))))

(facts "Inforequest conversion to application vs. handlers"
  (let [app-id1 (inforequest-with-handler)
        app-id2 (inforequest-with-handler)
        app-id3 (inforequest-with-handler)
        org-id  "753-R"]
    (fact "Initially removal flag is not set"
      (-> (query sipoo :organization-by-user :organizationId org-id)
          :organization :remove-handlers-from-converted-application)
      => nil)
    (fact "Handler survives the conversion"
      (command pena :convert-to-application :id app-id1) => ok?
      (has-handlers? app-id1) => true)
    (fact "Set the removal flag"
      (command sipoo :set-organization-remove-handlers-from-converted-application
               :organizationId org-id
               :enabled true)
      => ok?)
    (fact "Handler is removed during the conversion"
      (command pena :convert-to-application :id app-id2) => ok?
      (has-handlers? app-id2) => false)
    (fact "Unset the removal flag"
      (command sipoo :set-organization-remove-handlers-from-converted-application
               :organizationId org-id
               :enabled false)
      => ok?)
    (fact "Handler again survives the conversion"
      (command pena :convert-to-application :id app-id3) => ok?
      (has-handlers? app-id3) => true)))

(facts "Inforequest notification email"
  (last-email) ; clear box

  (command sipoo :set-organization-inforequest-notification-email :organizationId "753-R"
           :emails "testi@example.com") => ok?

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
        [url token] (re-find #"(?sm)/api/raw/openinforequest\?token-id=([A-Za-z0-9-]+)&lang=([a-z]{2})" (get-in email [:body :plain] ""))
        store (atom {})
        params {:cookie-store (->cookie-store store)
                :follow-redirects false
                :throw-exceptions false}]

    (fact "Inforequest was created"
      (:infoRequest application) => true)

    (fact "Inforequest is an open inforequest"
      (:openInfoRequest application) => true)

    (fact "Authority receives email about the new oir"
      (:to email) => "erajorma@example.com"
      (:subject email) => "Lupapiste: Neuvontapyynt\u00f6 Lupapisteess\u00e4"
      (count token) => pos?)

    (fact "Clicking the link redirects to oir page"
      (let [expected-location (str (server-address) "/app/fi/oir#!/inforequest/")
           resp (http-get (str (server-address) url) params)]
       (get-in resp [:headers "location"]) =>  #(sade.strings/starts-with % expected-location)))

    (comment-application pena application-id false) => ok?

    (fact "Authority receives email about the comment"
      (let [email          (last-email)
           [_ token] (re-find #"(?sm)/api/raw/openinforequest\?token-id=([A-Za-z0-9-]+)&lang=([a-z]{2})" (get-in email [:body :plain] ""))]
       (:to email) => "erajorma@example.com"
       (:subject email) => "Lupapiste: Neuvontapyynt\u00f6 Lupapisteess\u00e4"
       (count token) => pos?))

    (fact "Admin can not convert inforequests to normal yet"
      (command admin :convert-to-normal-inforequests :organizationId (:organization application)) => fail?)
    (facts "Configuring notification emails disables open inforequest"
      (command arto :set-organization-inforequest-notification-email
               :organizationId "433-R"
               :emails "foo@example.net") => ok?
      (last-email)
      (let [app-id (create-app-id pena :propertyId oir-property-id
                                  :infoRequest true :address "OIR")]
        (fact "Inforequest is not open"
          (:openInfoRequest (query-application pena app-id)) => falsey)
        (fact "Email sent to foo@example.net"
          (map :to (sent-emails)) => ["foo@example.net"]))
      (fact "Removing notification emails re-enables open inforequests"
        (command arto :set-organization-inforequest-notification-email
                 :organizationId "433-R"
                 :emails "") => ok?
        (->> (create-app-id pena :propertyId oir-property-id
                           :infoRequest true :address "OIR")
             (query-application pena)
             :openInfoRequest) => true)
      (fact "Bad email address disables open inforequests"
        (command admin :update-organization
                 :municipality "433"
                 :permitType "R"
                 :openInforequestEmail "") => ok?
        (->> (create-app-id pena :propertyId oir-property-id
                           :infoRequest true :address "OIR")
             (query-application pena)
             :openInfoRequest) => false))

    (fact "Admin disables open inforequests"
      (command admin :update-organization
        :permitType (:permitType application)
        :municipality (:municipality application)
        :inforequestEnabled true
        :applicationEnabled false
        :openInforequestEnabled false
        :openInforequestEmail  "erajorma@example.com"
        :opening nil
        :pateEnabled false) => ok?)

    (fact "Admin converts inforequests to normal"
      (let [resp (command admin :convert-to-normal-inforequests :organizationId (:organization application))]
        resp => ok?
        (:n resp) => 2))

    (fact "Token no longer works"
      (http-get (str (server-address) url) params) => http404?)

    (fact "Application is no longer oir"
      (let [application (query-application pena application-id)]
        (:infoRequest application) => true
        (:openInfoRequest application) => false))))

(defn add-to-company
  "Invites and accepts apikey to Esimerkki"
  [apikey]
  (let [email (email-for-key apikey)]
    (fact {:midje/description (str "Invite " email)}
      (command erkki :company-invite-user
               :email email
               :admin false :submit false) => ok?)
    (fact "Accept invitation"
      (->> (last-email) (token-from-email email) http-token-call) => truthy)))

(facts "Company inforequest"
  (add-to-company teppo)
  (add-to-company mikko)
  (let [app-id (create-app-id teppo :messages ["Business request"]
                              :infoRequest true
                              :propertyId sipoo-property-id)]
    (fact "Teppo has requested information successfully"
      app-id => truthy)
    (fact "Mikko can comment on the inforequest"
      (comment-application mikko app-id) => ok?)
    (fact "Mikko can cancel the inforequest"
      (command mikko :cancel-inforequest :id app-id) => ok?)))
