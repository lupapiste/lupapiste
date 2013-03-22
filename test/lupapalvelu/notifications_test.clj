(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.mongo :as mongo])
  (:use lupapalvelu.notifications
        midje.sweet))

(fact "create application link"
  (fact "for application"
    (get-application-link {:id 1} "fi" "" "http://localhost:8080")
      => "http://localhost:8080/app/fi/applicant?hashbang=!/application/1#!/application/1")
  (fact "for inforequest"
    (get-application-link {:id 1 :infoRequest true} "fi" "/comment" "http://localhost:8080")
      => "http://localhost:8080/app/fi/applicant?hashbang=!/inforequest/1/comment#!/inforequest/1/comment"))

; new comment
(fact "Each user in auth-array gets email from authority comment."
  (get-email-recipients-for-new-comment { :auth [{:id "a" :role "owner"} {:id "b" :role "writer"} {:id "c" :role "unknown"}] :title "title" }) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
  (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
    (mongo/by-id :users "b") => {:email "b@foo.com"}
    (mongo/by-id :users "c") => {:email "c@foo.com"}))

(defn get-html-body [html]
  (re-find #"(?ms)<body>.*<\/body>" html))

(fact "Email for new comment contains link to application"
  (get-message-for-new-comment { :id 123 :permitType "application"} "http://localhost:8000") => (contains "http://localhost:8000/app/fi/applicant?hashbang=!/application/123/conversation#!/application/123/conversation"))

; application opened
(fact "When application is opened, each use in auth-array gets email."
  (get-email-recipients-for-application-state-change { :auth [{:id "a" :role "owner"} {:id "b" :role "writer"} {:id "c" :role "unknown"}] :title "title" }) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
  (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
    (mongo/by-id :users "b") => {:email "b@foo.com"}
    (mongo/by-id :users "c") => {:email "c@foo.com"}))

(fact "Email for application open is like"
  (let [msg (get-html-body (get-message-for-application-state-change { :id 123 :state "open"} "http://localhost:8000"))]
    msg => (contains "http://localhost:8000/app/sv/applicant?hashbang=!/application/123#!/application/123")
    (re-find #"(?ms)Valmisteilla" msg) => truthy))

(fact "Email for application submitted contains the state string."
  (re-find #"Vireill\u00E4" (get-message-for-application-state-change { :state "submitted"} "")) => truthy)
