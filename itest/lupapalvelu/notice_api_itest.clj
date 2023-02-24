(ns lupapalvelu.notice-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(def ^:private sipoo-R-org-id "753-R")

(apply-remote-minimal) ; minimal ensures wanted organization tags exist

(defn application-notice [username id notice]
  (fact {:midje/description (format "Notice for %s: %s" username notice)}
    (:authorityNotice (query-application (apikey-for username) id))
    => notice))

(fact "adding notice"
  (let [{id :id} (create-and-submit-application pena)]
    (fact "user can't set application urgency"
      pena =not=> (allowed? :change-urgency :id id :urgency "urgent")
      (command pena :change-urgency :id id :urgency "urgent") =not=> ok?)

    (fact "authority can set application urgency"
      sonja => (allowed? :change-urgency :id id :urgency "urgent")
      (command sonja :change-urgency :id id :urgency "urgent") => ok?
      (:urgency (query-application sonja id)) => "urgent")

    (fact "user can't set notice message"
      pena =not=> (allowed? :add-authority-notice :id id :authorityNotice "foobar")
      (command pena :add-authority-notice :id id :authorityNotice "foobar") =not=> ok?)

    (fact "read-only authority can't set notice message"
      luukas =not=> (allowed? :add-authority-notice :id id :authorityNotice "foobar")
      (command luukas :add-authority-notice :id id :authorityNotice "foobar") =not=> ok?)

    (fact "authority can set notice message"
      sonja => (allowed? :add-authority-notice :id id :authorityNotice "respect my authority")
      (command sonja :add-authority-notice :id id :authorityNotice "respect my authority") => ok?
      (:authorityNotice (query-application sonja id)) => "respect my authority")

    (facts "Application tags" ; tags are in minimal fixture
      (fact "user can't set application tags"
        (command pena :add-application-tags :id id :tags ["111111111111111111111111" "222222222222222222222222"]) =not=> ok?)
      (fact "authority can set application tags"
        (command sonja :add-application-tags :id id :tags ["111111111111111111111111" "222222222222222222222222"]) => ok?)
      (fact "authority can't set tags that are not defined in organization"
        (command sonja :add-application-tags :id id :tags ["foo" "bar"]) => (partial expected-failure? "error.unknown-tags"))

      (let [query    (query-application sonja id)
            org-tags (get-in query [:organizationMeta :tags])]
        (:tags query) => ["111111111111111111111111" "222222222222222222222222"]

        (fact "application's organization meta includes correct tags with ids as keys"
          org-tags => {:111111111111111111111111 "yl\u00E4maa", :222222222222222222222222 "ullakko"}))

      (fact "When tag is removed, it is also removed from applications"
        (let [id (create-app-id sonja)]
          (command sipoo :save-organization-tags :organizationId sipoo-R-org-id
                   :tags [{:id "123000000000000000000000" :label "foo"} {:id "321000000000000000000000" :label "bar"}]) => ok?
          (command sonja :add-application-tags :id id :tags ["123000000000000000000000" "321000000000000000000000"]) => ok?
          (:tags (query-application sonja id)) => (just ["123000000000000000000000" "321000000000000000000000"])
          (command sipoo :save-organization-tags :organizationId sipoo-R-org-id
                   :tags [{:id "123000000000000000000000" :label "foo"}]) => ok?

          (fact "only 123 is left as 321 was removed"
            (:tags (query-application sonja id)) => (just ["123000000000000000000000"])))))

    (facts "Statement givers and notice panel"
      (let [err (just {:ok   false
                       :text "error.not-organization-statement-giver"})]
        (fact "Jussi is an organisation statement giver"
          (command sipoo :create-statement-giver :organizationId sipoo-R-org-id
                   :email (email-for-key jussi)
                   :text "Moro"
                   :name "Jussi") => ok?)
        (fact "Request statements from Jussi and Teppo"
          (command sonja :request-for-statement
                   :id id
                   :functionCode nil
                   :selectedPersons [{:email (email-for-key jussi)
                                      :name  "Jussi" :text "moro"}
                                     {:email (email-for-key teppo)
                                      :name  "Teppo" :text "hei"}]) => ok?)
        (facts "Authority notice pseudo query"
          (query pena :authority-notice :id id) => unauthorized?
          (query sonja :authority-notice :id id) => ok?
          (query ronja :authority-notice :id id) => ok?
          (query luukas :authority-notice :id id) => ok?
          (query jussi :authority-notice :id id) => ok?
          (query teppo :authority-notice :id id) => unauthorized?)
        (facts "Change urgency"
          (command jussi :change-urgency :id id :urgency "normal") => ok?
          (command teppo :change-urgency :id id :urgency "pending") => unauthorized?)
        (facts "Add tags"
          (command jussi :add-application-tags :id id
                   :tags ["123000000000000000000000"])=> ok?
          (command teppo :add-application-tags :id id
                   :tags ["321000000000000000000000"]) => unauthorized?)
        (facts "Add authority notice"
          (command jussi :add-authority-notice :id id :authorityNotice "Hello world!") => ok?
          (command teppo :add-authority-notice :id id :authorityNotice "Foo bar") => unauthorized?)
        (facts "Read authority notice"
          (application-notice "sonja" id "Hello world!")
          (application-notice "jussi" id "Hello world!")
          (application-notice "pena" id nil)
          (application-notice "luukas" id "Hello world!")
          (application-notice "teppo@example.com" id nil))
        (facts "Application organization tags"
          (let [tags (contains {:tags [{:id    "123000000000000000000000"
                                        :label "foo"}]})]
            (query sonja :application-organization-tags :id id) => tags
            (query jussi :application-organization-tags :id id) => tags
            (query teppo :application-organization-tags :id id) => err
            (query luukas :application-organization-tags :id id) => fail?
            (query pena :application-organization-tags :id id) => fail?))))
    (facts "Authority + application statementGiver combo LP-365545 & LP-365577"
      (fact "Ronja can see notices"
        (query ronja :authority-notice :id id) => ok?)
      (fact "Ronja could change urgnecy"
        ronja => (allowed? :change-urgency :id id :urgency "foo"))
      (fact "Ronja could add notice & tags"
        ronja => (allowed? :add-authority-notice :id id :authorityNotice "foo")
        ronja => (allowed? :add-application-tags :id id :tags ["foo"]))
      ; invite Ronja as statementGiver to reveal bug presented in LP-365545 & LP-365577
      (command sonja :request-for-statement :functionCode nil :id id
               :selectedPersons [{:name "R0NJ4" :text "Testi" :email (email-for "ronja")}]
               :saateText "ronja testi") => ok?
      (fact "Ronja can still see notices"
        (query ronja :authority-notice :id id) => ok?)
      (fact "Ronja can still change urgnecy"
        ronja => (allowed? :change-urgency :id id :urgency "foo"))
      (fact "Ronja can still add notice & tags"
        ronja => (allowed? :add-authority-notice :id id :authorityNotice "foo")
        ronja => (allowed? :add-application-tags :id id :tags ["foo"])))
    (facts "Guests and notice panel"
      (fact "Add Veikko as a guest authority"
        (command sipoo :update-guest-authority-organization :organizationId sipoo-R-org-id
                 :description "Vexi"
                 :email "veikko.viranomainen@tampere.fi"
                 :firstName "Veikko"
                 :lastName "Viranomainen"))
      (fact "Sonja adds Veikko as a guest authority to application"
        (command sonja :invite-guest :id id
                 :role "guestAuthority"
                 :email "veikko.viranomainen@tampere.fi"
                 :text "Invitation!") => ok?)
      (fact "Veikko cannot do notice queries"
        (query veikko :authority-notice :id id) => fail?
        (query veikko :application-organization-tags :id id) => fail?)
      (fact "Pena invites Mikko as a guest to application"
        (command sonja :invite-guest :id id
                 :role "guest"
                 :email "mikko.intonen@example.com"
                 :text "Invitation!") => ok?)
      (fact "Mikko cannot do notice queries"
        (query mikko :authority-notice :id id) => fail?
        (query mikko :application-organization-tags :id id) => fail?))))
