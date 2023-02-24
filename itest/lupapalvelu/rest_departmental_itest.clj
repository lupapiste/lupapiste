(ns lupapalvelu.rest-departmental-itest
  (:require [clj-http.client :as http]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-fixture "departmental")

(def departmental-userinfo      (str (server-address) "/rest/user"))
(def departmental-organizations (str (server-address)
                                     "/rest/docstore/departmental-organizations"))
(def oauth-token                (str (server-address)" /oauth/token"))

(def cookie-store (->cookie-store (atom {})))

(defn rest-call [url token]
  (http/get url {:oauth-token      token
                 :cookie-store cookie-store
                 :as               :json
                 :throw-exceptions false}))

(def rest-user          (partial rest-call departmental-userinfo))
(def rest-organizations (partial rest-call departmental-organizations))

(def status401 (contains {:status 401}))

(.addCookie cookie-store test-db-cookie)

(fact "No token"
  (rest-user "bad token") => status401)

(fact "User not enabled"
  (rest-user "dummy1") => status401)

(fact "Not an authority"
  (:body (rest-user "pena1")) => {:email     "pena@example.com"
                                  :firstName "Pena"
                                  :lastName  "Panaani"
                                  :id        pena-id
                                  :role      "applicant"})
(fact "Authority without departmental organizations"
  (:body (rest-organizations "sonja1")) => {:ok   true
                                            :data []}
  (:body (rest-user "sonja1"))
  => {:email         "sonja.sibbo@sipoo.fi"
      :firstName     "Sonja"
      :lastName      "Sibbo"
      :id            sonja-id
      :organizations [{:id "753-R" :roles ["authority" "approver"]}
                      {:id "753-YA" :roles ["authority" "approver"]}
                      {:id "998-R-TESTI-2" :roles ["authority" "approver"]}]
      :role          "authority"})

(fact "Departmental authority without departmental organizations"
  (:body (rest-organizations "bella1")) => {:ok   true
                                            :data []}
  (:body (rest-user "bella1"))
  => {:email         "bella.belastaja@example.org"
      :firstName     "Bella"
      :lastName      "Belastaja"
      :id            "bella-id"
      :role          "authority"})

(facts "Organizations"
  (fact "Enable departmental in 753-R"
    (command admin :update-docstore-info
             :org-id "753-R"
             :docStoreInUse false
             :docTerminalInUse false
             :docDepartmentalInUse true) => ok?)

  (fact "Sonja userinfo now includes departmental roles"
    (:body (rest-user "sonja1"))
    => (just {:email         "sonja.sibbo@sipoo.fi"
              :firstName     "Sonja"
              :lastName      "Sibbo"
              :id            sonja-id
              :organizations (just [(just {:id    "753-R"
                                           :roles (just ["authority" "approver" "departmental"]
                                                        :in-any-order)})
                                    {:id "753-YA" :roles ["authority" "approver"]}
                                    {:id "998-R-TESTI-2" :roles ["authority" "approver"]}]
                                   :in-any-order)
              :role          "authority"}))

  (fact "Bella with one departmental organization"
    (:body (rest-organizations "bella1"))
    => {:ok   true
        :data [{:id              "753-R"
                :name            {:fi "Sipoon rakennusvalvonta"
                                  :sv "Sipoon rakennusvalvonta"
                                  :en "Sipoon rakennusvalvonta"}
                :documentRequest {:email        ""
                                  :enabled      false
                                  :instructions {:en "" :fi "" :sv ""}}
                :municipalities  ["753"]}]}
  (:body (rest-user "bella1"))
  => {:email         "bella.belastaja@example.org"
      :firstName     "Bella"
      :lastName      "Belastaja"
      :id            "bella-id"
      :organizations [{:id "753-R" :roles ["departmental"]}]
      :role          "authority"})

  (fact "Biller role does not have `:organization/departmental` permission"
    (:body (rest-organizations "laura1"))
    => {:ok   true
        :data []}
    (:body (rest-user "laura1"))
    => {:email         "laura@sipoo.fi"
        :firstName     "Laura"
        :lastName      "Laskuttaja"
        :id            "laura-laskuttaja"
        :organizations [{:id "753-R"   :roles ["biller"]}
                        {:id "753-YA"  :roles ["biller"]}
                        {:id "FOO-ORG" :roles ["biller"]}]
        :role          "authority"})

  (fact "Organization details"
    (:body (rest-organizations "sonja1"))
    => {:ok   true
        :data [{:id              "753-R"
                :name            {:fi "Sipoon rakennusvalvonta"
                                  :sv "Sipoon rakennusvalvonta"
                                  :en "Sipoon rakennusvalvonta"}
                :documentRequest {:email        ""
                                  :enabled      false
                                  :instructions {:en "" :fi "" :sv ""}}
                :municipalities  ["753"]}]})
  (fact "Make changes to organization and fetch again"
    (command admin :update-docstore-info
             :org-id "753-R"
             :docStoreInUse false
             :docTerminalInUse false
             :docDepartmentalInUse true
             :organizationDescription {:fi "Moi" :sv "Hej"}) => ok?
    (command sipoo :set-document-request-info
             :organizationId "753-R"
             :enabled true
             :email "doc.req@example.com"
             :instructions {:fi "Ohjeet" :sv "Hjälp" :en "Info"}) => ok?

    (:body (rest-organizations "sonja1"))
    => {:ok   true
        :data [{:id              "753-R"
                :name            {:fi "Moi"
                                  :sv "Hej"
                                  :en "Sipoon rakennusvalvonta"}
                :documentRequest {:email        "doc.req@example.com"
                                  :enabled      true
                                  :instructions {:en "Info"
                                                 :fi "Ohjeet"
                                                 :sv "Hjälp"}}
                :municipalities  ["753"]}]})

  (fact "Enabled departmental for 753-YA"
    (command admin :update-docstore-info
             :org-id "753-YA"
             :docStoreInUse false
             :docTerminalInUse false
             :docDepartmentalInUse true
             :organizationDescription {:fi "Yleinen" :sv "Publik" :en "Public"}) => ok?
    (command sipoo-ya :set-document-request-info
             :organizationId "753-YA"
             :enabled true
             :email "ya-doc-req@example.com"
             :instructions {:fi "YA-Ohjeet" :sv "YA-Hjälp" :en "YA-Info"}) => ok?

    (:body (rest-organizations "sonja1"))
    => (just {:ok   true
              :data (just {:id              "753-R"
                           :name            {:fi "Moi"
                                             :sv "Hej"
                                             :en "Sipoon rakennusvalvonta"}
                           :documentRequest {:email        "doc.req@example.com"
                                             :enabled      true
                                             :instructions {:en "Info"
                                                            :fi "Ohjeet"
                                                            :sv "Hjälp"}}
                           :municipalities  ["753"]}
                          {:id              "753-YA"
                           :name            {:fi "Yleinen"
                                             :sv "Publik"
                                             :en "Public"}
                           :documentRequest {:email        "ya-doc-req@example.com"
                                             :enabled      true
                                             :instructions {:en "YA-Info"
                                                            :fi "YA-Ohjeet"
                                                            :sv "YA-Hjälp"}}
                           :municipalities  ["753" "007" "008"]}
                          :in-any-order)}))

  (fact "Bella with two departmental organizations"
    (:body (rest-organizations "bella1"))
    => (just {:ok   true
              :data (just (contains {:id "753-R"}) (contains {:id "753-YA"})
                          :in-any-order)})
    (:body (rest-user "bella1"))
    => (just {:email         "bella.belastaja@example.org"
              :firstName     "Bella"
              :lastName      "Belastaja"
              :id            "bella-id"
              :organizations (just {:id "753-R" :roles ["departmental"]}
                                   {:id "753-YA" :roles ["departmental"]}
                                   :in-any-order)
              :role          "authority"})))
