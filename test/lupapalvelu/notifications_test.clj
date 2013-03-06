(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.email :as email])
  (:use lupapalvelu.notifications
        midje.sweet))


(defn by-id [id] {:email (str id "@foo.com")})

(fact "Each owner and writer gets email from authority comment. Readers do not."
   (get-emails-for-new-comment { :auth [{:id "a"} {:id "b"}] :title "title" }) => [ "a@foo.com" "b@foo.com" ]
   (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
             (mongo/by-id :users "b") => {:email "b@foo.com"}))

(comment
(fact "email is sent when authority adds a comment"
  (send-notifications-on-new-comment { :auth [{:id "a"} {:id "b"}] :title "title" } { :role "authority" } "foo" ) => anything
  (provided (mongo/by-id :users "a") => {:email "a@foo.com"}
            (mongo/by-id :users "b") => {:email "a@foo.com"}
            (send-mail-to-recipients anything anything anything) => anything)))


(fact "email for new comment is like"
   (get-message-for-new-comment { :id 123 :permitType "application"} "http://localhost:8000") => 
     "<html><body><p>Uusi kommentti lisätty: <a id=\"application-link-fi\">http://localhost:8000/fi/applicant#!/application/123</a></p>

<p>Ett nytt komment lagt: <a id=\"application-link-sv\">http://localhost:8000/sv/applicant#!/application/123</a></p>
</body></html>")
