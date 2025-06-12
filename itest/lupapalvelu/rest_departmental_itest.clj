(ns lupapalvelu.rest-departmental-itest
  (:require [clj-http.client :as http]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [->cookie-store admin command ok? pena-id server-address sipoo sipoo-ya
                                            sonja-id test-db-cookie with-test-system]]
            [lupapalvelu.permissions :as perm]
            [matcher-combinators.test :refer [match?]])
  (:import [org.apache.http.client CookieStore]))

(set! *warn-on-reflection* true)

(use-fixtures :each (fn [run-tests]
                      (with-test-system (-> {:fixture "departmental"}
                                            test-system/with-remote-components)
                        (run-tests))))

(defn departmental-userinfo []
  (str (server-address) "/rest/user"))

(defn departmental-organizations []
  (str (server-address)
       "/rest/docstore/departmental-organizations"))

(def ^CookieStore cookie-store (->cookie-store (atom {})))

(def bella-email "bella.belastaja@example.org")
(def sonja-email "sonja.sibbo@sipoo.fi")

(def ohjeet-transit "[\"~:div.quill\",[\"~:span.quill\",[\"~:p.quill\",[\"^1\",\"Ohjeet\"]]]]")
(def ya-ohjeet-transit "[\"~:div.quill\",[\"~:span.quill\",[\"~:p.quill\",[\"^1\",\"YA-Ohjeet\"]]]]")

(defn rest-call [url token]
  (http/get url {:oauth-token      token
                 :cookie-store     cookie-store
                 :as               :json
                 :throw-exceptions false}))

(def rest-user (fn [token] (rest-call (departmental-userinfo) token)))
(def rest-organizations (fn [token] (rest-call (departmental-organizations) token)))

(defn- enable-departmental! [org-id]
  (command admin :update-docstore-info
           :org-id org-id
           :docStoreInUse false
           :docTerminalInUse false
           :docDepartmentalInUse true))

(defn- set-oauth-roles [org-admin org-id email roles]
  (command org-admin :update-user-oauth-roles
           :organizationId org-id
           :email email
           :oauth-roles roles))

(.addCookie cookie-store test-db-cookie)

(deftest no-token-test
  (is (match? {:status 401} (rest-user "bad token"))))

(deftest user-not-enabled-test
  (is (match? {:status 401} (rest-user "dummy1"))))

(deftest not-an-authority-test
  (is (= {:email     "pena@example.com"
          :firstName "Pena"
          :lastName  "Panaani"
          :id        pena-id
          :role      "applicant"}
         (:body (rest-user "pena1")))))

(deftest authority-without-departmental-organizations-test
  (is (= {:ok   true
          :data []}
         (:body (rest-organizations "sonja1"))))
  (is (= {:email     sonja-email
          :firstName "Sonja"
          :lastName  "Sibbo"
          :id        sonja-id
          :role      "authority"}
         (:body (rest-user "sonja1")))))

(deftest departmental-authority-without-departmental-organizations-test
  (is (= {:ok   true
          :data []}
         (:body (rest-organizations "bella1"))))
  (is (= {:email     bella-email
          :firstName "Bella"
          :lastName  "Belastaja"
          :id        "bella-id"
          :role      "authority"}
         (:body (rest-user "bella1")))))

(deftest organizations-test
  (testing "Enable departmental in 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Sonja userinfo now includes departmental roles"
    (is (= {:email         sonja-email
            :firstName     "Sonja"
            :lastName      "Sibbo"
            :id            sonja-id
            :organizations [{:id    "753-R"
                             :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "sonja1")))))

  (testing "Bella with one departmental organization"
    (is (= {:ok   true
            :data [{:id              "753-R"
                    :name            {:fi "Sipoon rakennusvalvonta"
                                      :sv "Sipoon rakennusvalvonta"
                                      :en "Sipoon rakennusvalvonta"}
                    :documentRequest {:email        ""
                                      :enabled      false
                                      :instructions {}}
                    :municipalities  ["753"]}]}
           (:body (rest-organizations "bella1"))))
    (is (= {:email         bella-email
            :firstName     "Bella"
            :lastName      "Belastaja"
            :id            "bella-id"
            :organizations [{:id "753-R" :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "bella1")))))

  (testing "Biller role does not have `:organization/departmental` permission"
    (is (= {:ok   true
            :data []}
           (:body (rest-organizations "laura1"))))
    (is (= {:email     "laura@sipoo.fi"
            :firstName "Laura"
            :lastName  "Laskuttaja"
            :id        "laura-laskuttaja"
            :role      "authority"}
           (:body (rest-user "laura1")))))

  (testing "Organization details"
    (is (= {:ok   true
            :data [{:id              "753-R"
                    :name            {:fi "Sipoon rakennusvalvonta"
                                      :sv "Sipoon rakennusvalvonta"
                                      :en "Sipoon rakennusvalvonta"}
                    :documentRequest {:email        ""
                                      :enabled      false
                                      :instructions {}}
                    :municipalities  ["753"]}]}
           (:body (rest-organizations "sonja1")))))

  (testing "Make changes to organization and fetch again"
    (is (ok? (command admin :update-docstore-info
                      :org-id "753-R"
                      :docStoreInUse false
                      :docTerminalInUse false
                      :docDepartmentalInUse true
                      :organizationDescription {:fi "Moi" :sv "Hej"})))
    (is (ok? (command sipoo :set-document-request-info
                      :organizationId "753-R"
                      :enabled true
                      :email "doc.req@example.com"
                      :instructions {:fi [{:insert "Ohjeet"}]})))

    (is (= {:ok   true
            :data [{:id              "753-R"
                    :name            {:fi "Moi"
                                      :sv "Hej"
                                      :en "Sipoon rakennusvalvonta"}
                    :documentRequest {:email        "doc.req@example.com"
                                      :enabled      true
                                      :instructions {:fi ohjeet-transit}}
                    :municipalities  ["753"]}]}
           (:body (rest-organizations "sonja1")))))

  (testing "Enabled departmental for 753-YA"
    (is (ok? (command admin :update-docstore-info
                      :org-id "753-YA"
                      :docStoreInUse false
                      :docTerminalInUse false
                      :docDepartmentalInUse true
                      :organizationDescription {:fi "Yleinen" :sv "Publik" :en "Public"})))
    (is (ok? (command sipoo-ya :set-document-request-info
                      :organizationId "753-YA"
                      :enabled true
                      :email "ya-doc-req@example.com"
                      :instructions {:fi [{:insert "YA-Ohjeet"}]})))

    (is (= {:ok   true
            :data [{:id              "753-R"
                    :name            {:fi "Moi"
                                      :sv "Hej"
                                      :en "Sipoon rakennusvalvonta"}
                    :documentRequest {:email        "doc.req@example.com"
                                      :enabled      true
                                      :instructions {:fi ohjeet-transit}}
                    :municipalities  ["753"]}
                   {:id              "753-YA"
                    :name            {:fi "Yleinen"
                                      :sv "Publik"
                                      :en "Public"}
                    :documentRequest {:email        "ya-doc-req@example.com"
                                      :enabled      true
                                      :instructions {:fi ya-ohjeet-transit}}
                    :municipalities  ["753" "007" "008"]}]}
           (:body (rest-organizations "sonja1")))))

  (testing "Bella with two departmental organizations"
    (is (match? {:ok   true
                 :data [{:id "753-R"}
                        {:id "753-YA"}]}
                (:body (rest-organizations "bella1"))))
    (is (= {:email         bella-email
            :firstName     "Bella"
            :lastName      "Belastaja"
            :id            "bella-id"
            :organizations [{:id "753-R" :roles ["departmental"]}
                            {:id "753-YA" :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "bella1"))))))

(deftest oauth-departmental-role-test
  (testing "Enable departmental for 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Sonja gets oauth departmental role in addition to implicit orgAuthz role"
    (is (ok? (set-oauth-roles sipoo "753-R" sonja-email ["departmental"])))
    ;; Sonja has orgAuthz authority for 753-R which gives implicit :departmental.
    ;; Adding oauth :departmental should not duplicate it.
    (is (= {:email         sonja-email
            :firstName     "Sonja"
            :lastName      "Sibbo"
            :id            sonja-id
            :organizations [{:id "753-R" :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "sonja1")))))

  (testing "Sonja with oauth departmental-limited: orgAuthz departmental wins"
    (is (ok? (set-oauth-roles sipoo "753-R" sonja-email ["departmental-limited"])))
    ;; orgAuthz authority grants :organization/departmental permission (full).
    ;; oauth-role gives :departmental-limited.
    ;; The stronger :departmental should win.
    (is (= {:email         sonja-email
            :firstName     "Sonja"
            :lastName      "Sibbo"
            :id            sonja-id
            :organizations [{:id "753-R" :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "sonja1"))))))

(deftest oauth-departmental-limited-role-test
  (testing "Enable departmental for 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Update Bella to departmental-limited for 753-R"
    (is (ok? (set-oauth-roles sipoo "753-R" bella-email ["departmental-limited"]))))

  (testing "Bella sees 753-R with departmental-limited role"
    (is (= {:email         bella-email
            :firstName     "Bella"
            :lastName      "Belastaja"
            :id            "bella-id"
            :organizations [{:id "753-R" :roles ["departmental-limited"]}]
            :role          "authority"}
           (:body (rest-user "bella1")))))

  (testing "Bella organizations endpoint also returns 753-R"
    (is (= {:ok   true
            :data [{:id              "753-R"
                    :name            {:fi "Sipoon rakennusvalvonta"
                                      :sv "Sipoon rakennusvalvonta"
                                      :en "Sipoon rakennusvalvonta"}
                    :documentRequest {:email        ""
                                      :enabled      false
                                      :instructions {}}
                    :municipalities  ["753"]}]}
           (:body (rest-organizations "bella1"))))))

(deftest oauth-departmental-mixed-roles-test
  (testing "Enable departmental for both 753-R and 753-YA"
    (is (ok? (enable-departmental! "753-R")))
    (is (ok? (enable-departmental! "753-YA"))))

  (testing "Set Bella departmental-limited for 753-R and departmental for 753-YA"
    ;; Bella's fixture has :departmental for both — flip 753-R to
    ;; :departmental-limited so that both set-oauth-roles calls actually change
    ;; something and the test verifies two distinct roles.
    (is (ok? (set-oauth-roles sipoo "753-R" bella-email ["departmental-limited"])))
    (is (ok? (set-oauth-roles sipoo-ya "753-YA" bella-email ["departmental"]))))

  (testing "Bella has different roles for different organizations"
    (is (= {:email         bella-email
            :firstName     "Bella"
            :lastName      "Belastaja"
            :id            "bella-id"
            :organizations [{:id "753-R" :roles ["departmental-limited"]}
                            {:id "753-YA" :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "bella1")))))

  (testing "Bella organizations endpoint returns both"
    (is (match? {:ok   true
                 :data [{:id "753-R"}
                        {:id "753-YA"}]}
                (:body (rest-organizations "bella1"))))))

(deftest oauth-departmental-removed-test
  (testing "Enable departmental for 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Bella initially has departmental for 753-R"
    (is (= [{:id "753-R" :roles ["departmental"]}]
           (:organizations (:body (rest-user "bella1"))))))

  (testing "Remove Bella's oauth departmental role from 753-R"
    (is (ok? (set-oauth-roles sipoo "753-R" bella-email []))))

  (testing "Bella no longer has departmental organizations"
    (is (= {:email     bella-email
            :firstName "Bella"
            :lastName  "Belastaja"
            :id        "bella-id"
            :role      "authority"}
           (:body (rest-user "bella1"))))
    (is (= {:ok true :data []}
           (:body (rest-organizations "bella1"))))))

(def get-organization-permissions-orig perm/get-organization-permissions)

(defn- limit-departmental-permissions
  "Wraps `get-organization-permissions` so that every `:organization/departmental`
  in the result set is replaced with `:organization/departmental-limited`. Simulates
  a future orgAuthz role that only grants limited departmental access."
  [command]
  (let [perms (get-organization-permissions-orig command)]
    (if (contains? perms :organization/departmental)
      (-> perms
          (disj :organization/departmental)
          (conj :organization/departmental-limited))
      perms)))

(deftest mocked-orgauthz-departmental-limited-test
  (testing "Enable departmental for 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Sonja gets departmental-limited when orgAuthz yields limited permission"
    ;; Normally Sonja's authority orgAuthz grants :organization/departmental.
    ;; With the mock the permission is downgraded to :organization/departmental-limited.
    (with-redefs [perm/get-organization-permissions limit-departmental-permissions]
      (is (= {:email         sonja-email
              :firstName     "Sonja"
              :lastName      "Sibbo"
              :id            sonja-id
              :organizations [{:id "753-R" :roles ["departmental-limited"]}]
              :role          "authority"}
             (:body (rest-user "sonja1"))))))

  (testing "Without mock Sonja still gets full departmental"
    (is (= {:email         sonja-email
            :firstName     "Sonja"
            :lastName      "Sibbo"
            :id            sonja-id
            :organizations [{:id "753-R" :roles ["departmental"]}]
            :role          "authority"}
           (:body (rest-user "sonja1"))))))

(deftest mocked-orgauthz-limited-with-oauth-departmental-test
  (testing "Enable departmental for 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Give Sonja oauth departmental role"
    (is (ok? (set-oauth-roles sipoo "753-R" sonja-email ["departmental"]))))

  (testing "Strongest-role: oauth departmental wins over mocked orgAuthz departmental-limited"
    ;; orgAuthz (mocked) → :departmental-limited
    ;; oauth-roles       → :departmental
    ;; The stronger :departmental should win.
    (with-redefs [perm/get-organization-permissions limit-departmental-permissions]
      (is (= {:email         sonja-email
              :firstName     "Sonja"
              :lastName      "Sibbo"
              :id            sonja-id
              :organizations [{:id "753-R" :roles ["departmental"]}]
              :role          "authority"}
             (:body (rest-user "sonja1")))))))

(deftest mocked-orgauthz-limited-with-oauth-limited-test
  (testing "Enable departmental for 753-R"
    (is (ok? (enable-departmental! "753-R"))))

  (testing "Give Sonja oauth departmental-limited role"
    (is (ok? (set-oauth-roles sipoo "753-R" sonja-email ["departmental-limited"]))))

  (testing "Both paths return departmental-limited so result is departmental-limited"
    ;; orgAuthz (mocked) → :departmental-limited
    ;; oauth-roles       → :departmental-limited
    ;; Both limited → result is :departmental-limited.
    (with-redefs [perm/get-organization-permissions limit-departmental-permissions]
      (is (= {:email         sonja-email
              :firstName     "Sonja"
              :lastName      "Sibbo"
              :id            sonja-id
              :organizations [{:id "753-R" :roles ["departmental-limited"]}]
              :role          "authority"}
             (:body (rest-user "sonja1")))))))
