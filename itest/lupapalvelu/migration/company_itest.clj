(ns lupapalvelu.migration.company-itest
  (:require [lupapalvelu.company :as company]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.migration.company :as com]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (let [_            (create-app-id erkki :address "Company Circle")
          pena-app-id  (create-app-id pena :address "Target Tower")
          sonja-app-id (create-app-id sonja :address "Outside Offroad")
          company-auth {:firstName "Esimerkki Oy"
                        :id        "esimerkki"
                        :lastName  ""
                        :name      "Esimerkki Oy"
                        :role      "writer"
                        :type      "company"
                        :username  "7208863-8"
                        :y         "7208863-8"}]
      (fact "No such company"
        (com/auth-company-applications-by-domain "bad-id" "bad.net")
        => nil)
      (fact "Company locked"
        (mongo/update-by-id :companies "esimerkki" {$set {:locked 12345}})
        (com/auth-company-applications-by-domain "esimerkki" "example.com")
        => nil
        (mongo/update-by-id :companies "esimerkki" {$unset {:locked true}}))
      (fact "Company domain mismatch"
        (com/auth-company-applications-by-domain "esimerkki" "bad.net")
        => nil)
      (fact "No active members"
        (mongo/update-by-id :users erkki-id {$set {:enabled false}})
        (com/auth-company-applications-by-domain "esimerkki" "example.com")
        => nil
        (mongo/update-by-id :users erkki-id {$set {:enabled true}}))
      (fact "No company users authed"
        (com/auth-company-applications-by-domain "solita" "solita.fi")
        => 0)
      (fact "Authed, but not enabled"
        (mongo/update-by-id :users pena-id {$set {:enabled false}})
        (com/auth-company-applications-by-domain "esimerkki" "example.com")
        => 0
        (mongo/update-by-id :users pena-id {$set {:enabled true}}))
      (fact "No blank domains"
        (com/auth-company-applications-by-domain "esimerkki" "")
        => nil
        (com/auth-company-applications-by-domain "esimerkki" nil)
        => nil
        (com/auth-company-applications-by-domain "esimerkki" "  ")
        => nil)
      (fact "No regex tricks"
        (com/auth-company-applications-by-domain "esimerkki" ".+") => 0)
      (fact "Success"
        (com/auth-company-applications-by-domain "esimerkki" "example.com"
                                                 "Success!")
        => 1)
      (fact "Pena's application is modified"
        (let [[{:keys [id auth]}
               :as apps] (mongo/select :applications
                                       {:_sheriff-notes.note "Success!"})]
          (count apps) => 1
          id => pena-app-id
          (map (juxt :id :role) auth)
          => (just [pena-id "writer"] ["esimerkki" "writer"])
          (last auth) => company-auth))
      (fact "Nothing to do anymore"
        (com/auth-company-applications-by-domain "esimerkki" "example.com")
        => 0)
      (fact "Sonja invites Teppo to her application"
        (command sonja :invite-with-role :id sonja-app-id
                 :email (min-util/email-for-key teppo)
                 :role "writer"
                 :documentId ""
                 :documentName ""
                 :path ""
                 :text "Come on, man!") => ok?)
      (fact "The invite has not been accepted, so migration does nothing"
        (com/auth-company-applications-by-domain "esimerkki" "example.com")
        => 0)
      (fact "Teppo accepts invitation"
        (command teppo :approve-invite :id sonja-app-id) => ok?)
      (fact "New success: only Sonja's applicaton affected"
        (com/auth-company-applications-by-domain "esimerkki" "  eXaMpLe.COM  "
                                                 "Awesome!")
        => 1
        (let [[{:keys [id auth]}
               :as apps] (mongo/select :applications
                                       {:_sheriff-notes.note "Awesome!"})]
          (count apps) => 1
          id => sonja-app-id
          (map (juxt :id :role) auth)
          => (just [sonja-id "writer"]
                   [teppo-id "writer"]
                   ["esimerkki" "writer"])
          (last auth) => company-auth))
      (fact "Again, nothing to do"
        (com/auth-company-applications-by-domain "esimerkki" "example.com")
        => 0)
      (fact "Note is optional"
        (let [app-id (create-app-id sven :address "Vilken Vägen")]
          (com/auth-company-applications-by-domain "esimerkki" "example.com")
          => 1
          (let [{notes :_sheriff-notes
                 auth  :auth} (mongo/by-id :applications app-id)]
            notes => nil
            auth => (just (contains {:id sven-id :role "writer"})
                          company-auth)))))
    (facts "Merge companies"
      (fact "Can not merge if company to merge into not found"
        (com/merge-companies-into "a" "some-text" "solita") => :error.company-to-merge-into-not-found)
      (fact "Can not merge if company to merge into is locked"
        (mongo/update-by-query :companies {:_id "solita"} {$set {:locked 42}})
        (com/merge-companies-into "solita" "some-text" "a") => :error.merge-into-company-locked
        (mongo/update-by-query :companies {:_id "solita"} {$unset {:locked 1} }))
      (fact "Can not merge if no companies to merge from given"
        (com/merge-companies-into "solita" "some-text" ) => :error.no-companies-to-merge-from-given)
      (fact "Can not merge if some company to merge from does not exist"
        (com/merge-companies-into "solita" "some-text" "b") => :error.some-company-to-merge-from-not-found)
      (fact "Can not merge if a company to merge into is also marked to merge from"
        (com/merge-companies-into "solita" "some-text" "solita" "b") => :error.company-to-merge-into-also-in-companies-to-merge)
      (let [pena-app-id  (create-app-id pena :address "Address 1")
            pena-app-id2 (create-app-id pena :address "Address 2")
            from-id      "solita"
            to-id        "esimerkki"
            from-company (company/find-company-by-id from-id)
            auth         (company/company->auth from-company)]
        (mongo/update-by-query :applications
                               {:_id {$in [pena-app-id pena-app-id2]}}
                               {$push {:auth auth}}) => 2
        (fact "State of company being merged from before merge"
          (company/company-users-count from-id) => 1
          (mongo/count :applications {:auth.id from-id}) => 2
          (:locked from-company) => nil)
        (fact "State of company being merged to before merge"
          (company/company-users-count to-id) => 1
          (mongo/count :applications {:auth {$elemMatch {:id to-id}}}) => 4)
        (fact "Can merge companies"
          (com/merge-companies-into to-id  "Note: merge companies" from-id) => 1)
        (fact "State of company being merged from after merge"
          (company/company-users-count from-id) => 0
          (mongo/count :applications {:auth {$elemMatch {:id from-id}}}) => 0
          (-> (company/find-company-by-id from-id) :locked some?) => true)
        (fact "State of company being merged to after merge"
          (company/company-users-count to-id) => 2
          (mongo/count :applications {:auth {$elemMatch {:id to-id}}}) => 6)
        (fact "Sheriff-notes added when merging companies"
          (-> (mongo/select :users {:_id "kainosolita"} {:_sheriff-notes 1})
              first :_sheriff-notes first :note) => "Note: merge companies"
          (-> (mongo/select :applications {:_id pena-app-id} {:_sheriff-notes 1})
              first :_sheriff-notes first :note) => "Note: merge companies")))))
