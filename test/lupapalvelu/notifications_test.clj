(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.email :as email])
  (:use lupapalvelu.notifications
        midje.sweet))


(fact "Each user in auth-array gets email from authority comment."
   (get-emails-for-new-comment { :auth [{:id "a" :role "owner"} {:id "b" :role "writer"} {:id "c" :role "writer"}] :title "title" }) => [ "a@foo.com" "b@foo.com" "c@foo.com"]
   (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
             (mongo/by-id :users "b") => {:email "b@foo.com"}
             (mongo/by-id :users "c") => {:email "c@foo.com"}))

(fact "Email for new comment is like"
   (get-message-for-new-comment { :id 123 :permitType "application"} "http://localhost:8000") => 
"<html>
<body>
  <p>Uusi kommentti lisätty: <a id=\"application-link-fi\">http://localhost:8000/fi/applicant#!/application/123</a></p>
  <p>Ett nytt komment lagt: <a id=\"application-link-sv\">http://localhost:8000/sv/applicant#!/application/123</a></p>
</body>\n
</html>")
