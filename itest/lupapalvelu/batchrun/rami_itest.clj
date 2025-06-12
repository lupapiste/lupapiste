(ns lupapalvelu.batchrun.rami-itest
  (:require [lupapalvelu.batchrun.rami :as rami]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.password-reset :as pw-reset]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.itest-system :as test-system]))

(testable-privates lupapalvelu.batchrun.rami
                   read-configuration
                   promote-reporting-system-for-organizations
                   rollback-reporting-system-for-organizations
                   user->reporter
                   rollback-users
                   rami-oauth?)

(def start-text @#'rami/start-text)
(def end-text   @#'rami/end-text)
(def email-subject "Lupapiste: Tervetuloa Rakentamisen mittari -palvelun käyttäjäksi")

(defn no-emails []
  (fact "No emails sent"
    (last-email) => nil))

(defn no-user [email]
  (fact {:midje/description (str "No user " email)}
    (mongo/any? :users {:email email}) => false
    (no-emails)))

(defn not-reporter [org-id email]
  (fact {:midje/description (format "User %s is not a reporter in %s." email org-id)}
    (mongo/any? :users {:email       email
                        :oauth-roles {$elemMatch {:organization org-id
                                                  :client-type  :bi-app
                                                  :role         :reporter}}})
    => false
    (no-emails)))

(defn is-reporter
  ([org-id email role]
   (fact {:midje/description (format "User %s is a %s in %s."
                                     email (name role) org-id)}
     (mongo/any? :users {:email       email
                         :oauth-roles {$elemMatch {:organization org-id
                                                   :client-type  :bi-app
                                                   :role role}}})
     => true))
  ([org-id email] (is-reporter org-id email :reporter)))

(defn make-user
  ([org-id email]
   {:organization {:id   org-id
                   :name (str "Organization " org-id)}
    :firstName    "Hello"
    :lastName     "World"
    :email        email})
  ([email] (make-user "123-R" email)))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (let [system (test-system/get-common-components)]
    (facts "read-configuration"
      (read-configuration "empty.edn") => []
      (provided
        (util/read-edn-file anything) => nil)
      (read-configuration "empty-list.edn") => []
      (provided
        (util/read-edn-file anything) => [])
      (read-configuration "bad.edn") => (throws)
      (provided
        (util/read-edn-file anything) => {:hello "world"})
      (read-configuration "bad-organization.edn") => (throws)
      (provided
        (util/read-edn-file anything) => [{:email           "foo.bar@example.com"
                                           :firstname       "Foo"
                                           :lastname        "Bar"
                                           :organization-id "404-BAD"}])
      (read-configuration "bad-email.edn") => (throws)
      (provided
        (util/read-edn-file anything) => [{:email           "bad.example.com"
                                           :firstname       "Foo"
                                           :lastname        "Bar"
                                           :organization-id "753-R"}])
      (read-configuration "good.edn")
      => (just {:email        "foo.bar@example.com"
                :firstName    "Foo"
                :lastName     "Bar"
                :organization {:id   "753-R"
                               :name "Sipoon rakennusvalvonta"}}
               {:email        "dim.sum@example.net"
                :firstName    "Dim"
                :lastName     "Sum"
                :organization {:id   "186-R"
                               :name "Järvenpään rakennusvalvonta"}}
               :in-any-order)
      (provided
        (util/read-edn-file anything) => [{:email           " foo.bar@example.com "
                                           :firstname       " Foo "
                                           :lastname        " Bar "
                                           :organization-id " 753-R "}
                                          {:email           " dim.sum@example.net "
                                           :firstname       " Dim "
                                           :lastname        " Sum "
                                           :organization-id " 186-R "}]))

    (facts "Organizations vs reporting systems"
      (let [;; Sipoo, no reporting system
            org-sipoo (mongo/with-id (mongo/update-one-and-return :organizations {:_id "753-R"}
                                                                  {$unset {:reporting-system true}}))
            ;; Järvenpää Rami 1.0
            org-jp    (mongo/with-id (mongo/update-one-and-return :organizations {:_id "186-R"}
                                                                  {$set {:reporting-system :bi-app}}))
            ;; Tampere Rami 2.0
            org-tre   (mongo/with-id (mongo/update-one-and-return :organizations {:_id "837-R"}
                                                                  {$set {:reporting-system :bi-app2}}))
            org-ids   (mapv :id [org-sipoo org-jp org-tre])]
        org-sipoo => truthy
        org-jp => truthy
        org-tre => truthy
        org-ids => not-empty
        (facts "Promotion"
          (promote-reporting-system-for-organizations org-ids) => nil
          (fact "Sipoo"
            (mongo/by-id :organizations (:id org-sipoo))
            => (contains {:reporting-system "bi-app2"
                          :_sheriff-notes   (just (just {:note    start-text
                                                         :created truthy})) }))
          (fact "Järvenpää"
            (mongo/by-id :organizations (:id org-jp))
            => (contains {:reporting-system "bi-app2"
                          :_sheriff-notes   (just (just {:note             start-text
                                                         :reporting-system "bi-app"
                                                         :created          truthy})) }))
          (fact "Tampere (unchanged)"
            (mongo/by-id :organizations (:id org-tre))
            => org-tre))
        (facts "Rollback"
          (rollback-reporting-system-for-organizations org-ids) => nil
          (fact "Sipoo"
            (let [org (mongo/by-id :organizations (:id org-sipoo))]
              (:reporting-system org) => nil
              (dissoc org :_sheriff-notes) => org-sipoo
              (:_sheriff-notes org)
              => (just (just {:note start-text :created truthy})
                       (just {:note end-text :created truthy}))))
          (fact "Järvenpää"
            (let [org (mongo/by-id :organizations (:id org-jp))]
              (:reporting-system org) => "bi-app"
              (dissoc org :_sheriff-notes) => org-jp
              (:_sheriff-notes org)
              => (just (just {:note             start-text :created truthy
                              :reporting-system "bi-app"})
                       (just {:note end-text :created truthy}))))
          (fact "Tampere (unchanged)"
            (mongo/by-id :organizations (:id org-tre))
            => org-tre))))

    (with-local-actions ;; Needed for `last-email` to work
      (last-email) ;; Reset mail buffer
      (facts "user->reporter"
        (fact "AD: non-existing user in Pori (forced)"
          (user->reporter system (make-user "609-R" "unknown.person@pori.fi")) => nil
          (no-user "unknown.person@pori.fi"))
        (fact "AD: Existing AD user in Pori (not forced)"
          (mongo/update-by-id :ad-logins "pori-ad-login" {$set {:forced false}})
          (mongo/update-by-query :users {:username "priscilla"}
                                 {$set   {:ad-login true}
                                  $unset {:orgAuthz true}})
          => 1
          (user->reporter system (make-user "609-R" "priscilla.panaani@pori.fi")) => nil
          (not-reporter "609-R" "priscilla.panaani@pori.fi"))

        (fact "New user to Tampere"
          (user->reporter system (make-user "837-R" "tam.pere@example.net"))
          (let [email                     "tam.pere@example.net"
                u                         (mongo/select-one :users {:email email})
                token                     (mongo/select-one :token {})
                {:keys [subject to body]} (last-email)]
            u => (contains {:firstName      "Hello"     :lastName "World"
                            :role           "authority" :enabled  true
                            :email          email
                            :_sheriff-notes (just (just {:note            start-text
                                                         :organization-id "837-R"
                                                         :created         truthy}))
                            :oauth-roles    (just (just {:client-type  "bi-app"
                                                         :role         "reporter"
                                                         :organization "837-R"}))})
            (:private u) => empty?
            (:orgAuthz u) => nil
            token => (contains {:data       {:email email}
                                :token-type "password-reset"})
            subject => email-subject
            to => "Hello World <tam.pere@example.net>"
            (in-text body ["Sinut on kutsuttu Lupapisteeseen Rakentamisen mittari -lisäpalvelun käyttäjäksi organisaatioon Organization 837-R."
                           "Käyttäjätunnuksena toimii sähköpostiosoitteesi."
                           "Jatka klikkaamalla alla olevaa linkkiä. Jos linkki ei toimi, kopioi osoite ja liitä se selaimen osoiteriville."

                           (pw-reset/reset-link {:config {:host "https://localhost"}} "fi" (:id token))])))

        (fact "Unsupported role"
          (doseq [email ["admin@solita.fi" "tampe-rest@example.com" "dummy@example.com"]]
            (user->reporter system (make-user email)) => nil
            (not-reporter "123-R" email)))

        (fact "Not enabled"
          (mongo/update-by-query :users {:username "jussi"}
                                 {$set {:enabled false}})
          => 1
          (user->reporter system (make-user "jussi.viranomainen@tampere.fi")) => nil
          (not-reporter "123-R" "jussi.viranomainen@tampere.fi"))

        (fact "Already authed implicitly"
          (mongo/update-by-query :users {:username "kosti"}
                                 {$pull {:oauth-roles {:client-type :bi-app}}})
          => 1
          (doseq [email ["sonja.sibbo@sipoo.fi" "luukas.lukija@sipoo.fi"
                         "kosti.kommentoija@sipoo.fi"]]
            (user->reporter system (make-user "753-R" email)) => nil
            (not-reporter "753-R" email)))

        (fact "Already authed explicitly"
          (mongo/update-by-query :users {:username "laura"}
                                 {$push {:oauth-roles {:client-type  :bi-app
                                                       :organization :753-R
                                                       :role         :director}}})
          => 1
          (doseq [[org-id email role] [["837-R" "tam.pere@example.net" :reporter]
                                       ["753-R" "laura@sipoo.fi" :director]]]
            (user->reporter system (make-user org-id email)) => nil
            (is-reporter org-id email role)
            (no-emails)))

        (fact "Existing user to reporter"
          (mongo/remove-many :token {:token-type :password-reset})
          (doseq [[org-id email] [["753-R" "pena@example.com"]
                                  ["753-R" "priscilla.panaani@pori.fi"]
                                  ["753-R" "r-biller@vantaa.fi"]]]
            (not-reporter org-id email)
            (user->reporter system (make-user org-id email))
            (is-reporter org-id email)
            (mongo/count :users {:email          email
                                 :_sheriff-notes {$elemMatch {:note            start-text
                                                              :organization-id org-id
                                                              :created         {$gt 12345}}}})
            => 1
            (let [{:keys [to subject body]} (last-email)]
              to => (format "Hello World <%s>" email)
              subject => email-subject
              (in-text body ["Sinut on kutsuttu Lupapisteeseen Rakentamisen mittari -lisäpalvelun käyttäjäksi organisaatioon Organization 753-R."
                             ["Käyttäjätunnuksena toimii sähköpostiosoitteesi."]])
              (mongo/any? :token {:token-type :password-reset}) => false))))

      (facts "rollback-users"
        (fact "Nothing to do"
          (rollback-users ["unknown.person@pori.fi" "admin@solita.fi" "tampe-rest@example.com"
                           "dummy@example.com" "jussi.viranomainen@tampere.fi"
                           "sonja.sibbo@sipoo.fi" "luukas.lukija@sipoo.fi"
                           "kosti.kommentoija@sipoo.fi" "laura@sipoo.fi"])
          => nil
          (provided
            (mongo/update-by-id :users anything anything) => nil :times 0))
        (facts "Successful rollback"
          (rollback-users ["pena@example.com" "priscilla.panaani@pori.fi"
                           "tam.pere@example.net""r-biller@vantaa.fi"])
          (doseq [[org-id email] [["753-R ""pena@example.com"]
                                  ["753-R" "priscilla.panaani@pori.fi"]
                                  ["837-R" "tam.pere@example.net"]
                                  ["753-R" "r-biller@vantaa.fi"]]
                  :let           [u (mongo/select-one :users {:email email})]]
            (fact {:midje/description email}
              (-> u :_sheriff-notes last) => (just {:note    end-text
                                                    :created truthy})
              (rami-oauth? u org-id) => false))
          (fact "New rollback does nothing"
            (rollback-users ["pena@example.com" "priscilla.panaani@pori.fi"
                             "tam.pere@example.net""r-biller@vantaa.fi"])
            => nil
            (provided
              (mongo/update-by-id :users anything anything) => nil :times 0))))

      (facts "full campaign"
        (fact "bad command"
          (rami/campaign system "bad" "data.edn") => nil
          (provided
            (util/read-edn-file anything) => nil :times 0))
        (fact "before campaign"
          (mongo/any? :organizations {:_id              {$in ["529-R" "297-R"]}
                                      :reporting-system :bi-app2})
          => false
          (mongo/any? :users {:email                   {$in ["nancy.new@example.com"
                                                             "kuopio-r@kuopio.fi"]}
                              :oauth-roles.client-type :bi-app})
          => false
          (no-emails))
        (fact "start"
          (rami/campaign system "start" "data.edn") => nil
          (provided
            (util/read-edn-file anything) => [{:email           "nancy.new@example.com"
                                               :organization-id "529-R"
                                               :firstname       "Nancy"
                                               :lastname        "New"}
                                              {:email           "velho.viranomainen@kuopio.fi"
                                               :organization-id "297-R"
                                               :firstname       "Velho"
                                               :lastname        "Viranomainen"}
                                              {:email           "kuopio-r@kuopio.fi"
                                               :organization-id "297-R"
                                               :firstname       "Head"
                                               :lastname        "Honcho"}]))
        (fact "after campaign"
          (mongo/count :organizations {:_id              {$in ["529-R" "297-R"]}
                                       :reporting-system :bi-app2})
          => 2
          (mongo/count :users {:email                   {$in ["nancy.new@example.com"
                                                              "kuopio-r@kuopio.fi"]}
                               :oauth-roles.client-type :bi-app})
          => 2
          (facts "sent emails"
            (let [sent              (sent-emails)
                  nancy-mail        (util/find-by-key :to "Nancy New <nancy.new@example.com>" sent)
                  {nancy-token :id} (mongo/select-one :token {:data.email "nancy.new@example.com" })
                  head-mail         (util/find-by-key :to "Head Honcho <kuopio-r@kuopio.fi>" sent)]
              (count sent) => 2
              (fact "Nancy New"
                nancy-token => truthy
                (:subject nancy-mail) => email-subject
                (in-text (:body nancy-mail) ["Sinut on kutsuttu Lupapisteeseen Rakentamisen mittari -lisäpalvelun käyttäjäksi organisaatioon Naantalin rakennusvalvonta."
                                             "Käyttäjätunnuksena toimii sähköpostiosoitteesi."
                                             nancy-token]))
              (fact "Head Honcho"
                (mongo/any? :token {:data.email "kuopio-r@kuopio.fi"}) => false
                (:subject head-mail) => email-subject
                (in-text (:body head-mail) ["Sinut on kutsuttu Lupapisteeseen Rakentamisen mittari -lisäpalvelun käyttäjäksi organisaatioon Naantalin rakennusvalvonta."
                                            ["Käyttäjätunnuksena toimii sähköpostiosoitteesi."]])))))
        (fact "end"
          (rami/campaign system "end" "data.edn") => nil
          (provided
            (util/read-edn-file anything) => [{:email           "nancy.new@example.com"
                                               :organization-id "529-R"
                                               :firstname       "Nancy"
                                               :lastname        "New"}
                                              {:email           "velho.viranomainen@kuopio.fi"
                                               :organization-id "297-R"
                                               :firstname       "Velho"
                                               :lastname        "Viranomainen"}
                                              {:email           "kuopio-r@kuopio.fi"
                                               :organization-id "297-R"
                                               :firstname       "Head"
                                               :lastname        "Honcho"}]))
        (fact "after rollback"
          (mongo/any? :organizations {:_id              {$in ["529-R" "297-R"]}
                                      :reporting-system :bi-app2})
          => false
          (mongo/any? :users {:email                   {$in ["nancy.new@example.com"
                                                             "kuopio-r@kuopio.fi"]}
                              :oauth-roles.client-type :bi-app})
          => false
          (no-emails))))))
