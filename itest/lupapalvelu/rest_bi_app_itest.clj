(ns lupapalvelu.rest-bi-app-itest
  (:require [clj-http.client :as http]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [org.apache.http.client CookieStore]))

(set! *warn-on-reflection* true)

(defn- user-endpoint []
  (str (server-address) "/rest/user"))

(def ^CookieStore cookie-store (->cookie-store (atom {})))

(defn rest-call [url token]
  (http/get url {:oauth-token      token
                 :cookie-store     cookie-store
                 :as               :json
                 :throw-exceptions false}))

(def rest-user-body (comp :body (fn [token] (rest-call (user-endpoint) token))))

(.addCookie cookie-store test-db-cookie)

(def sipoo-org-id "753-R")

(defn- set-oauth-roles [username roles]
  (command sipoo :update-user-oauth-roles :organizationId sipoo-org-id
           :email (min-util/email-for username)
           :oauth-roles roles))

(defn- set-organization-reporting-system [bi-app-version]
  (command admin :set-organization-reporting-system :org-id sipoo-org-id
           :reporting-system bi-app-version))

(defn- set-organization-departmental-in-use [departmental-in-use?]
  (command admin :update-docstore-info
           :org-id sipoo-org-id
           :docStoreInUse false
           :docTerminalInUse false
           :docDepartmentalInUse departmental-in-use?))

(defn- get-user-by-email [email]
  (query admin :user-by-email :email email))

(defn- get-oauth-roles [username]
  (->> (get-user-by-email (min-util/email-for username))
       :user
       :oauth-roles
       (map :role)
       sort))

(defn- sonja-with-org-roles [org-roles]
  (util/assoc-when {:email     "sonja.sibbo@sipoo.fi"
                    :firstName "Sonja"
                    :id        sonja-id
                    :lastName  "Sibbo"
                    :role      "authority"}
                   :organizations org-roles))

(with-test-system (-> {:fixture "departmental"}
                      test-system/with-remote-components)
  (facts "Organization has reporting system"

       (set-organization-reporting-system "bi-app") => ok?

       (fact "Sonja has implicit reporter role from authority orgAuth"
         (command sipoo :update-user-roles :organizationId sipoo-org-id
                  :email (min-util/email-for "sonja")
                  :oauth-roles []
                  :roles ["authority"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["reporter"]}]))

       (fact "Sonja has implicit reporter role from commenter orgAuth"
         (command sipoo :update-user-roles :organizationId sipoo-org-id
                  :email (min-util/email-for "sonja")
                  :oauth-roles []
                  :roles ["commenter"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["reporter"]}]))

       (fact "Sonja has no oauth-roles"
         (command sipoo :update-user-roles :organizationId sipoo-org-id
                  :email (min-util/email-for "sonja")
                  :oauth-roles []
                  :roles ["digitizer"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles nil))

       (fact "Sonja has oauth-roles: ['reporter']"
         (set-oauth-roles "sonja" ["reporter"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["reporter"]}]))

       (fact "Sonja has oauth-roles: ['director']"
         (set-oauth-roles "sonja" ["director"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["director"]}]))

       (fact "Sonja has oauth-roles: ['director' 'reporter']"
         (set-oauth-roles "sonja" ["reporter" "director"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["reporter" "director"]}]))


       (fact "Sonja bi-app does not receive departmental role"
         (set-oauth-roles "sonja" ["reporter" "director" "departmental"]) => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["reporter" "director"]}]))

       (fact "Sonja bi-app2 does not receive roles when org has reporting-system set to bi-app"
         (rest-user-body "sonja-bi-app2") => (sonja-with-org-roles nil))

       (set-organization-reporting-system "bi-app2") => ok?

       (fact "Sonja bi-app does not receive roles when org has reporting-system set to bi-app2"
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles nil))

       (fact "Sonja bi-app2 receives previously set roles when org has reporting-system set to bi-app2"
         (rest-user-body "sonja-bi-app2") => (sonja-with-org-roles [{:id sipoo-org-id :roles ["reporter" "director"]}]))

       (fact "Sonja bi-app2 receives no roles when org has no reporting system"
         (set-organization-reporting-system "not-in-use") => ok?
         (rest-user-body "sonja-bi-app") => (sonja-with-org-roles nil)
         (rest-user-body "sonja-bi-app2") => (sonja-with-org-roles nil))

       (fact "Non-existing user is added to oauth users"
         (set-organization-reporting-system "bi-app") => ok?
         (command sipoo :update-user-oauth-roles :organizationId sipoo-org-id
                  :email "new_user@example.com"
                  :oauth-roles ["reporter"]) => ok?

         (get-user-by-email "new_user@example.com")
         => (contains {:ok   true
                       :user (contains {:email       "new_user@example.com"
                                        :enabled     false
                                        :firstName   ""
                                        :id          ss/not-blank?
                                        :lastName    "new_user@example.com"
                                        :oauth-roles [{:client-type  "bi-app"
                                                       :organization "753-R"
                                                       :role         "reporter"}]
                                        :role        "authority"
                                        :username    "new_user@example.com"})})))

  (facts "Inactive oauth-roles in organization"

    (fact "Remove all oauth-roles from sonja in 753-R"

      (set-organization-reporting-system "bi-app2") => ok?
      (set-organization-departmental-in-use true) => ok?
      (set-oauth-roles "sonja" [])
      (get-oauth-roles "sonja") => [])

    (fact "User cannot be given oauth-roles that are not active in organization"

      (set-organization-reporting-system "not-in-use") => ok?
      (set-organization-departmental-in-use false) => ok?

      (set-oauth-roles "sonja" ["departmental" "director" "reporter"]) => ok?
      (get-oauth-roles "sonja") => []

      (set-organization-reporting-system "bi-app2") => ok?
      (set-oauth-roles "sonja" ["departmental" "director" "reporter"]) => ok?
      (get-oauth-roles "sonja") => ["director" "reporter"])

    (fact "Inactive roles are not removed"

      (set-organization-reporting-system "not-in-use") => ok?
      (set-organization-departmental-in-use true) => ok?

      (set-oauth-roles "sonja" ["departmental"]) => ok?
      (get-oauth-roles "sonja") => ["departmental" "director" "reporter"]

      (set-oauth-roles "sonja" []) => ok?
      (get-oauth-roles "sonja") => ["director" "reporter"])))
