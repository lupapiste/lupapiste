(ns lupapalvelu.notifications-test
  (:require [lupapalvelu.notifications :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.application-api]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [lupapalvelu.open-inforequest]
            [sade.dummy-email-server :as dummy]
            [sade.env :as env])
  (:import (javax.mail.internet InternetAddress)))

(testable-privates lupapalvelu.notifications get-email-subject get-email-recipients-for-application ->to)

(facts "email titles"
  (facts "{{municipality}} is rendered to subject"
    (get-email-subject {:address "Haavikontie 9" :municipality "837" } {:municipality "Tampere" } "new-comment" "fi")
    => "Lupapiste: Haavikontie 9, Tampere - sinulle on uusi viesti"
    (get-email-subject {:address "Haavikontie 9" :municipality "837" } {:municipality "Tammerfors" } "new-comment" "sv")
    => "Lupapiste: Haavikontie 9, Tammerfors - du har f\u00e5tt ett nytt meddelande")

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
  (get-email-recipients-for-application { :auth [{:id "a" :role "writer"}
                                                 {:id "b" :role "foreman"}
                                                 {:id "c" :role "reader"}]
                                         :title "title" }
                                        nil nil)
  => [ {:email "a@foo.com"} {:email "b@foo.com"} {:email "c@foo.com"}]
  (provided
   (usr/get-users {:_id {"$in" ["a" "b" "c"]}})
   => [{:email "a@foo.com"}
       {:email "b@foo.com"}
       {:email "c@foo.com"}]))

(fact "Every user except with role invalid get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "writer"}
                                                 {:id "b" :role "reader"}
                                                 {:id "c" :role "invalid"}]
                                         :title "title" }
                                        nil nil)
  => [ {:email "a@foo.com"} {:email "b@foo.com"}]
  (provided
   (usr/get-users {:_id {"$in" ["a" "b"]}})
   => [{:email "a@foo.com"} {:email "b@foo.com"}]))

(fact "Every user except with role reader get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "writer"}
                                                 {:id "b" :role "foreman"}
                                                 {:id "c" :role "reader"}]
                                         :title "title" }
                                        nil [:reader])
  => [ {:email "a@foo.com"} {:email "b@foo.com"}]
  (provided
   (usr/get-users {:_id {"$in" ["a" "b"]}})
   => [{:email "a@foo.com"} {:email "b@foo.com"}]))

(fact "Only writers get email"
  (get-email-recipients-for-application { :auth [{:id "a" :role "foreman"}
                                                 {:id "w1" :role "writer"}
                                                 {:id "w2" :role "writer"}
                                                 {:id "w3" :role "writer"}
                                                 {:id "c" :role "reader"}]
                                         :title "title" }
                                        [:writer] nil)
  => [ {:email "w1@foo.com"} {:email "w2@foo.com"} {:email "w3@foo.com"}]
  (provided
   (usr/get-users {:_id {"$in" ["w1" "w2" "w3"]}})
   => [{:email "w1@foo.com"} {:email "w2@foo.com"} {:email "w3@foo.com"}]))

(fact "Only writers get email (foreman exlusion overrides include))"
  (get-email-recipients-for-application { :auth [{:id "a" :role "foreman"}
                                                 {:id "w1" :role "writer"}
                                                 {:id "w2" :role "writer"}
                                                 {:id "w3" :role "writer"}
                                                 {:id "c" :role "reader"}]
                                         :title "title" }
                                        [:foreman :writer] [:foreman])
  => [ {:email "w1@foo.com"} {:email "w2@foo.com"} {:email "w3@foo.com"}]
  (provided
   (usr/get-users {:_id {"$in" ["w1" "w2" "w3"]}})
   => [{:email "w1@foo.com"} {:email "w2@foo.com"} {:email "w3@foo.com"}]))

(fact "Unsubscription prevents email"
  (get-email-recipients-for-application
   {:auth  [{:id "a" :role "foreman" :unsubscribed false}
            {:id "b" :role "writer" :unsubscribed true}
            {:id "c" :role "reader"}]
    :title "title" }
    nil nil)
  => [{:email "a@foo.com"} {:email "c@foo.com"}]
  (provided
   (usr/get-users {:_id {"$in" ["a" "c"]}})
   => [{:email "a@foo.com"} {:email "c@foo.com"}]))

(fact "Company admins get email"
  (get-email-recipients-for-application {:auth [{:id "regular" :role "writer"}
                                                {:id "com"
                                                 :type "company"
                                                 :role "writer"}]
                                         :title "title"}
                                        [:writer] nil)
  => [{:email "joe.regular@example.org"}
      {:email "admin1@example.com"}
      {:email "admin2@example.com"}]
  (provided
   (usr/get-users {"$or" [{:company.id   {"$in" ["com"]}
                           :company.role :admin}
                          {:_id {"$in" ["regular"]}}]})
   => [{:email "joe.regular@example.org"}
       {:email "admin1@example.com"}
       {:email "admin2@example.com"}]))

(testable-privates lupapalvelu.open-inforequest base-email-model)
(fact "Email for sending an open inforequest is like"
  (against-background
    (sade.env/value :host) => "http://lupapiste.fi")
  (let  [model (base-email-model {:data {:token-id "123"}} nil {})]
    (doseq [lang i18n/supported-langs]
      (fact {:midje/description (name lang)}
        ((:link model) lang)
        => (str "http://lupapiste.fi/api/raw/openinforequest?token-id=123&lang=" (name lang))))))

(fact "Unknown config"
  (notify! :foo {}) => (throws AssertionError))

(when (env/value :email :dummy-server)

(fact "organization-on-submit email"
  (against-background [(lupapalvelu.organization/get-organization "Foo") => {:notifications {:submit-notification-emails ["foo-org@example.com"]}}])
  (notify! :organization-on-submit {:application {:address          "Foostreet 1",
                                                  :municipality     "753",
                                                  :state            "submitted"
                                                  :primaryOperation {:name "kerrostalo-rivitalo"}
                                                  :organization     "Foo",
                                                  :_applicantIndex  ["Foo 1", "Foo 2"]}})
  (Thread/sleep 100)
  (let [msg (last (dummy/messages))]
    (:subject msg) => (contains "Foostreet 1, Sipoo - uusi hakemus")
    (get-in msg [:body :plain]) => (contains "osoitteessa Foostreet 1, Sipoo on nyt jätetty vireille")
    (get-in msg [:body :plain]) => (contains "Alla on linkki")
    (get-in msg [:body :plain]) =not=> (contains "???")))

(fact "organization-on-submit email empty :_applicantIndex"
  (against-background [(lupapalvelu.organization/get-organization "Foo") => {:notifications {:submit-notification-emails ["foo-org@example.com"]}}])
  (notify! :organization-on-submit {:application {:address          "Foostreet 1",
                                                  :municipality     "753",
                                                  :state            "submitted"
                                                  :primaryOperation {:name "kerrostalo-rivitalo"}
                                                  :organization     "Foo",
                                                  :_applicantIndex  []}}) => nil) ; previously 'wrong number of args' exception for reduce


(defemail :test {:recipients-fn :recipients
                 :model-fn      (constantly
                                 {:hi  ""
                                  :hej ""
                                  :moi ""})
                 :template      "testbody.md"})

(defn- notify-invite! [user]
  (notify! :test {:recipients [user]}))

(defn- last-notification-text [{does-contain :contains does-not-contain :does-not-contain}]
  (Thread/sleep 100)
  (fact {:midje/description (str "Notification contains " contains " and does not contain " does-not-contain)}
    (let [msg (last (dummy/messages))]
      (doseq [text does-contain]
        (get-in msg [:body :plain]) => (contains text))
      (doseq [text does-not-contain]
        (get-in msg [:body :plain]) =not=> (contains text)))))

(facts "notification language"
  (against-background [(#'lupapalvelu.notifications/get-email-subject anything anything anything anything) => "Subject"])
  (notify-invite! {:email "pekka@suomi.fi" :language "fi"})
  (last-notification-text {:contains         ["suomi"]
                           :does-not-contain ["Svenska" "English"]})

  (notify-invite! {:email "sven@sverige.sv" :language "sv"})
  (last-notification-text {:contains         ["Svenska"]
                           :does-not-contain ["suomi" "English"]})

  (notify-invite! {:email "johnny@engl.ish" :language "en"})
  (last-notification-text {:contains         ["English"]
                           :does-not-contain ["suomi" "Svenska"]})


  (notify-invite! {:email "nano@nano.nano" :language nil})
  (last-notification-text {:contains ["suomi" "Svenska" "English"]})))

(facts "To: field construction"
  (letfn [(make-address [recipient]
            (->> recipient
                 (->to)
                 (InternetAddress.)))]
    (facts "tough cases"
      (->to nil) => nil
      (->to {}) => nil)
    (fact "Only email"
      (->to {:email "test@example.com"}) => "test@example.com"
      (make-address {:email "test@example.com"}) => truthy)
    (fact "normal case"
      (->to {:email "test@example.com" :firstName "Foo" :lastName "Faa"}) => "Foo Faa <test@example.com>"
      (make-address {:email "test@example.com" :firstName "Foo" :lastName "Faa"}) => truthy)
    (fact "no lastname"
      (->to {:email "test@example.com" :firstName "Foo" :lastName ""}) => "test@example.com"
      (make-address {:email "test@example.com" :firstName "Foo" :lastName ""}) => truthy)
    (facts "with comma"
      (->to {:email "test@example.com" :firstName "Foo" :lastName ","}) => "Foo  <test@example.com>"
      (make-address {:email "test@example.com" :firstName "Foo" :lastName ","}) => truthy)
    (facts "'<>' is stripped"
      (->to {:email "test@example.com" :firstName ">Foo<" :lastName "<Hannu>"}) => "Foo Hannu <test@example.com>"
      (make-address {:email "test@example.com" :firstName ">Foo<" :lastName "<Hannu>"}) => truthy)))
