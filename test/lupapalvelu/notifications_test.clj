(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.notifications :refer [notify!]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.mongo :as mongo]
            [sade.dummy-email-server :as dummy]))

(testable-privates lupapalvelu.notifications get-email-subject get-application-link get-email-recipients-for-application)

(facts "email titles"
  (get-email-subject {:title "Haavikontie 9" :municipality "837" } "new-comment") => "Lupapiste.fi: Haavikontie 9 - uusi kommentti"
  (get-email-subject {:title "Haavikontie 9" :municipality "837" }) => "Lupapiste.fi: Haavikontie 9"
  (get-email-subject {:title "Haavikontie 9" :municipality "837" } "statement-request" true) => "Lupapiste.fi: Tampere, Haavikontie 9 - Lausuntopyynt\u00f6")

(fact "create application link"
  (fact "..for application"
    (get-application-link {:id 1} "" "fi")
      => (str (sade.env/value :host) "/app/fi/applicant#!/application/1"))
  (fact "..for inforequest"
    (get-application-link {:id 1 :infoRequest true} "/comment" "fi" )
      => (str (sade.env/value :host) "/app/fi/applicant#!/inforequest/1/comment")))

(fact "Every user gets an email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "b" :role "writer"}
                                                 {:id "c" :role "unknown"}] :title "title" }
                                        nil nil) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
  (provided
    (mongo/by-id :users "a" {:email 1}) => {:email "a@foo.com"}
    (mongo/by-id :users "b" {:email 1}) => {:email "b@foo.com"}
    (mongo/by-id :users "c" {:email 1}) => {:email "c@foo.com"}))

(fact "Every user except with role unknown get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "b" :role "writer"}
                                                 {:id "c" :role "unknown"}] :title "title" }
                                        nil ["unknown"]) => [ "a@foo.com" "b@foo.com"]
  (provided
    (mongo/by-id :users "a" {:email 1}) => {:email "a@foo.com"}
    (mongo/by-id :users "b" {:email 1}) => {:email "b@foo.com"}))

(fact "Only writers get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "w1" :role "writer"}
                                                 {:id "w2" :role "writer"}
                                                 {:id "w3" :role "writer"}
                                                 {:id "c" :role "unknown"}] :title "title" }
                                        ["writer"] nil) => [ "w1@foo.com" "w2@foo.com" "w3@foo.com"]
  (provided
    (mongo/by-id :users "w1" {:email 1}) => {:email "w1@foo.com"}
    (mongo/by-id :users "w2" {:email 1}) => {:email "w2@foo.com"}
    (mongo/by-id :users "w3" {:email 1}) => {:email "w3@foo.com"}))

(fact "Only writers get email (when excluded)"
  (get-email-recipients-for-application { :auth [{:id "a" :role "owner"}
                                                 {:id "w1" :role "writer"}
                                                 {:id "w2" :role "writer"}
                                                 {:id "w3" :role "writer"}
                                                 {:id "c" :role "unknown"}] :title "title" }
                                        ["owner" "writer"] ["owner"]) => [ "w1@foo.com" "w2@foo.com" "w3@foo.com"]
  (provided
    (mongo/by-id :users "w1" {:email 1}) => {:email "w1@foo.com"}
    (mongo/by-id :users "w2" {:email 1}) => {:email "w2@foo.com"}
    (mongo/by-id :users "w3" {:email 1}) => {:email "w3@foo.com"}))

(testable-privates lupapalvelu.open-inforequest base-email-model)
(fact "Email for sending an open inforequest is like"
  (against-background
    (sade.env/value :host) => "http://lupapiste.fi"
    (sade.env/value :oir :wanna-join-url) => "http://lupapiste.fi/yhteydenotto")
  (let  [model (base-email-model {:data {:token-id "123"}} nil)]
    (:link-fi model) => "http://lupapiste.fi/api/raw/openinforequest?token-id=123&lang=fi"
    (:info-fi model) => "http://lupapiste.fi/yhteydenotto"))

(fact "Unknown config"
  (notify! :foo {}) => (throws AssertionError))

