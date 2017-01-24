(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.notifications :refer [notify! create-app-model]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.application-api]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as user]
            [lupapalvelu.open-inforequest]
            [sade.dummy-email-server :as dummy]))

(testable-privates lupapalvelu.notifications get-email-subject get-application-link get-email-recipients-for-application)

(facts "email titles"
  (facts "{{municipality}} is rendered to subject"
    (get-email-subject {:address "Haavikontie 9" :municipality "837" } {:municipality "Tampere" } "new-comment" "fi")
    => "Lupapiste: Haavikontie 9, Tampere - sinulle on uusi kommentti"
    (get-email-subject {:address "Haavikontie 9" :municipality "837" } {:municipality "Tammerfors" } "new-comment" "sv")
    => "Lupapiste: Haavikontie 9, Tammerfors - du har f\u00e5tt en ny kommentar")

  (fact "Without valid localization, application address is returned"
    (get-email-subject {:address "Haavikontie 9" :municipality "837" } {:municipality "Tampere" } "foo" "cn")
    => "Lupapiste: Haavikontie 9"
    (provided
      (i18n/localize-fallback "cn" anything) => nil))

  (get-email-subject {:address "Haavikontie 9" :municipality "837"}  {:municipality "Tampere" } "statement-request" "fi")
    => "Lupapiste: Haavikontie 9, Tampere - lausuntopyynt\u00f6")

(fact "create application link"
  (fact "..for application"
    (get-application-link {:id 1} "" "fi" {:role "applicant"})
      => (str (sade.env/value :host) "/app/fi/applicant#!/application/1")
    (get-application-link {:id 1} "/tab" "fi" {:role "applicant"})
      => (str (sade.env/value :host) "/app/fi/applicant#!/application/1/tab")
    (get-application-link {:id 1} "tab" "fi" {:role "applicant"})
      => (str (sade.env/value :host) "/app/fi/applicant#!/application/1/tab"))

  (fact "..for inforequest"
    (get-application-link {:id 1 :infoRequest true} "/comment" "fi" {:role "authority"})
    => (str (sade.env/value :host) "/app/fi/authority#!/inforequest/1/comment")))

(fact "Application model"
  (create-app-model {:application {:id 1 :state "draft" :address "foodress"}} {:tab "footab"} {:firstName "Bob"
                                                                                               :language "sv"
                                                                                               :role "applicant"})
  => (contains {:link fn? :state fn? :address "foodress" :municipality fn? :operation fn?}))

(fact "Every user gets an email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "b" :role "writer"}
                                                 {:id "c" :role "reader"}] :title "title" }
                                        nil nil) => [ {:email "a@foo.com"} {:email "b@foo.com"} {:email "c@foo.com"}]
  (provided
    (user/get-user-by-id "a") => {:email "a@foo.com"}
    (user/get-user-by-id "b") => {:email "b@foo.com"}
    (user/get-user-by-id "c") => {:email "c@foo.com"}))

(fact "Every user except with role invalid get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "b" :role "writer"}
                                                 {:id "c" :role "invalid"}] :title "title" }
                                        nil nil) => [ {:email "a@foo.com"} {:email "b@foo.com"}]
  (provided
    (user/get-user-by-id "a") => {:email "a@foo.com"}
    (user/get-user-by-id "b") => {:email "b@foo.com"}))

(fact "Every user except with role reader get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "b" :role "writer"}
                                                 {:id "c" :role "reader"}] :title "title" }
                                        nil [:reader]) => [ {:email "a@foo.com"} {:email "b@foo.com"}]
  (provided
    (user/get-user-by-id "a") => {:email "a@foo.com"}
    (user/get-user-by-id "b") => {:email "b@foo.com"}))

(fact "Only writers get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "w1" :role "writer"}
                                                 {:id "w2" :role "writer"}
                                                 {:id "w3" :role "writer"}
                                                 {:id "c" :role "reader"}] :title "title" }
                                        [:writer] nil) => [ {:email "w1@foo.com"} {:email "w2@foo.com"} {:email "w3@foo.com"}]
  (provided
    (user/get-user-by-id "w1") => {:email "w1@foo.com"}
    (user/get-user-by-id "w2") => {:email "w2@foo.com"}
    (user/get-user-by-id "w3") => {:email "w3@foo.com"}))

(fact "Only writers get email (owner exlusion overrides include))"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "w1" :role "writer"}
                                                 {:id "w2" :role "writer"}
                                                 {:id "w3" :role "writer"}
                                                 {:id "c" :role "reader"}] :title "title" }
                                        [:owner :writer] [:owner]) => [ {:email "w1@foo.com"} {:email "w2@foo.com"} {:email "w3@foo.com"}]
  (provided
    (user/get-user-by-id "w1") => {:email "w1@foo.com"}
    (user/get-user-by-id "w2") => {:email "w2@foo.com"}
    (user/get-user-by-id "w3") => {:email "w3@foo.com"}))

(fact "Unsubsribtion prevents email"
  (get-email-recipients-for-application
    {:auth [{:id "a" :role "owner" :unsubscribed false}
            {:id "b" :role "writer" :unsubscribed true}
            {:id "c" :role "reader"}] :title "title" }
    nil nil) => [{:email "a@foo.com"} {:email "c@foo.com"}]
  (provided
    (user/get-user-by-id "a") => {:email "a@foo.com"}
    (user/get-user-by-id "c") => {:email "c@foo.com"}))

(testable-privates lupapalvelu.open-inforequest base-email-model)
(fact "Email for sending an open inforequest is like"
  (against-background
    (sade.env/value :host) => "http://lupapiste.fi")
  (let  [model (base-email-model {:data {:token-id "123"}} nil {})]
    (doseq [lang i18n/supported-langs]
      (fact {:midje/description (name lang)}
        ((:link model) lang) => (str "http://lupapiste.fi/api/raw/openinforequest?token-id=123&lang=" (name lang))))))

(fact "Unknown config"
  (notify! :foo {}) => (throws AssertionError))

(fact "organization-on-submit email"
  (against-background [(lupapalvelu.organization/get-organization "Foo") => {:notifications {:submit-notification-emails ["foo-org@example.com"]}}])
  (notify! :organization-on-submit {:application {:address "Foostreet 1",
                                                  :municipality "753",
                                                  :state "submitted"
                                                  :primaryOperation {:name "kerrostalo-rivitalo"}
                                                  :organization "Foo",
                                                  :_applicantIndex ["Foo 1", "Foo 2"]}})
  (let [msg (last (dummy/messages))]
    (:subject msg) => (contains "Foostreet 1, Sipoo - uusi hakemus")
    (get-in msg [:body :plain]) => (contains "osoitteessa Foostreet 1, Sipoo on nyt j\u00e4tetty vireille")
    (get-in msg [:body :plain]) => (contains "Alla on linkki")
    (get-in msg [:body :plain]) =not=> (contains "???")))
