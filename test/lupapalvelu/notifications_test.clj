(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.email :as email])
  (:use lupapalvelu.notifications
        midje.sweet))

; new comment
(fact "Each user in auth-array gets email from authority comment."
   (get-email-recipients-for-new-comment { :auth [{:id "a" :role "owner"} {:id "b" :role "writer"} {:id "c" :role "unknown"}] :title "title" }) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
   (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
             (mongo/by-id :users "b") => {:email "b@foo.com"}
             (mongo/by-id :users "c") => {:email "c@foo.com"}))

(fact "Email for new comment is like"
   (get-message-for-new-comment { :id 123 :permitType "application"} "http://localhost:8000") => 
"<html>
<body>
  <p>Hei,</p>
  <p>Uusi kommentti lisätty <a id=\"conversation-link-fi\" href=\"http://localhost:8000/fi/applicant#!/application/123/conversation\">keskusteluun</a>.</p>
  <p>Terveisin, Lupapiste</p>
  <hr />
  <p>Hej,</p>
  <p>Ett nytt komment lagt <a id=\"conversation-link-sv\" href=\"http://localhost:8000/sv/applicant#!/application/123/conversation\">till diskussionet</a>.</p>
  <p>Grattis, Lupapiste</p>
</body>\n
</html>")

; application opened
(fact "When application is opened, each use in auth-array gets email."
   (get-email-recipients-for-application-state-change { :auth [{:id "a" :role "owner"} {:id "b" :role "writer"} {:id "c" :role "unknown"}] :title "title" }) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
   (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
             (mongo/by-id :users "b") => {:email "b@foo.com"}
             (mongo/by-id :users "c") => {:email "c@foo.com"}))

(fact "Email for application state change is like"
   (get-message-for-application-state-change { :id 123 :state "open"} "http://localhost:8000") => 
"<html>
<body>
  <p>Hei,</p>
  <p>Hakemuksen tila on nyt <span id=\"state-fi\">Valmisteilla</span>: <a href=\"http://localhost:8000/fi/applicant#!/application/123\" id=\"application-link-fi\">linkki</a></p>
  <p>Terveisin, Lupapiste</p>
  <hr />
  <p>Hej,</p>
  <p>Tillståndet förändrat till <span id=\"state-sv\">Inför</span>: <a href=\"http://localhost:8000/sv/applicant#!/application/123\" id=\"application-link-sv\">link</a></p>
  <p>Grattis, Lupapiste</p>
</body>\n
</html>")
