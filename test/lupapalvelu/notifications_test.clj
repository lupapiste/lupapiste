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

(defn get-html-body [html]
  (re-find #"(?ms)<body>.*<\/body>" html))

(fact "Email body for new comment is like"
   (get-html-body (get-message-for-new-comment { :id 123 :permitType "application"} "http://localhost:8000")) => 
"<body>
  <p>Hei,</p>
  <p>Uusi kommentti lisätty <a id=\"conversation-link-fi\" href=\"http://localhost:8000/fi/applicant#!/application/123/conversation\">keskusteluun</a>.</p>
  <p>Yst\u00E4v\u00E4llisin terveisin, Lupapiste</p>
  <hr />
  <p>Hej,</p>
  <p>Ett nytt komment lagt <a id=\"conversation-link-sv\" href=\"http://localhost:8000/sv/applicant#!/application/123/conversation\">till diskussionet</a>.</p>
  <p>V\u00E4nliga h\u00E4lsningar, Lupapiste</p>
</body>")

; application opened
(fact "When application is opened, each use in auth-array gets email."
   (get-email-recipients-for-application-state-change { :auth [{:id "a" :role "owner"} {:id "b" :role "writer"} {:id "c" :role "unknown"}] :title "title" }) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
   (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
             (mongo/by-id :users "b") => {:email "b@foo.com"}
             (mongo/by-id :users "c") => {:email "c@foo.com"}))

(fact "Email for application open is like"
   (get-html-body (get-message-for-application-state-change { :id 123 :state "open"} "http://localhost:8000")) => 
"<body>
  <p>Hei,</p>
  <p><a href=\"http://localhost:8000/fi/applicant#!/application/123\" id=\"application-link-fi\">Hakemuksen</a> tila on nyt: <span id=\"state-fi\">Valmisteilla</span></p>
  <p>Yst\u00E4v\u00E4llisin terveisin, Lupapiste</p>
  <hr />
  <p>Hej,</p>
  <p>Tillståndet av <a href=\"http://localhost:8000/sv/applicant#!/application/123\" id=\"application-link-sv\">ansökan</a> förändrats till: <span id=\"state-sv\">Inför</span></p>
  <p>V\u00E4nliga h\u00E4lsningar, Lupapiste</p>
</body>")

(fact "Email for application submitted contains the state string."
   (re-find #"Vireill\u00E4" (get-message-for-application-state-change { :state "submitted"} "")) => truthy)
