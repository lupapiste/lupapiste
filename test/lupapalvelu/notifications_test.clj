(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.notifications :refer [notify! create-app-model]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.user :as user]
            [lupapalvelu.open-inforequest]
            [sade.dummy-email-server :as dummy]))

(testable-privates lupapalvelu.notifications get-email-subject get-application-link get-email-recipients-for-application)

(facts "email titles"
       (get-email-subject {:title "Haavikontie 9" :municipality "837" } "fi" "new-comment")
       => "Lupapiste: Haavikontie 9 - uusi kommentti"
       (get-email-subject {:title "Haavikontie 9" :municipality "837" } "sv" "new-comment")
              => "Lupapiste: Haavikontie 9 - ny kommentar"
       (get-email-subject {:title "Haavikontie 9" :municipality "837" } "cn")
       => "Lupapiste: Haavikontie 9"
       (get-email-subject {:title "Haavikontie 9" :municipality "837"} "fi" "statement-request" true)
       => "Lupapiste: Tampere, Haavikontie 9 - Lausuntopyynt\u00f6")

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
      (create-app-model {:application {:id 1 :state "draft"}} {:tab ""} {:firstName "Bob"
                                                                         :language "sv"
                                                                         :role "applicant"})
      => (contains {:lang "sv" :name "Bob"}))

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
    (sade.env/value :host) => "http://lupapiste.fi"
    (sade.env/value :oir :wanna-join-url) => "http://lupapiste.fi/yhteydenotto")
  (let  [model (base-email-model {:data {:token-id "123"}} nil {})]
    (:link-fi model) => "http://lupapiste.fi/api/raw/openinforequest?token-id=123&lang=fi"
    (:info-fi model) => "http://lupapiste.fi/yhteydenotto"))

(fact "Unknown config"
  (notify! :foo {}) => (throws AssertionError))
