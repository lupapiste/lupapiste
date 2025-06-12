(ns lupapalvelu.session-itest
  "Integration tests for server-side session management.
   Tests verify session cookie rotation, persistence, logout invalidation,
   and password-change session invalidation behavior."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [->cookie-store decode-body server-address with-test-system]]
            [lupapalvelu.json :as json]
            [lupapalvelu.system.util-components :as components]
            [ring.util.codec :as codec]
            [sade.http :as http])
  (:import [org.apache.http.client CookieStore]
           [org.apache.http.cookie Cookie]
           [org.apache.http.impl.cookie BasicClientCookie]))

(set! *warn-on-reflection* true)

(use-fixtures :each (fn [f]
                      (with-test-system (-> {:fixture "minimal"}
                                            test-system/with-remote-components)
                        (f))))

(defn session-cookie-name
  []
  (-> (test-system/get-common-components)
      components/system->config
      :noir
      :cookie-name))

(defn- make-cookie-store
  "Creates and returns [store-atom cookie-store]. The atom holds {name → Cookie}.
   The test_db_name cookie is pre-loaded."
  []
  (let [store (atom {})]
    [store (->cookie-store store)]))

(defn- get-session-cookie
  "Returns the session cookie value string from the cookie store atom, or nil."
  ^String [store-atom]
  (some-> ^Cookie (get @store-atom (session-cookie-name)) .getValue))

(defn- login!
  "POSTs login credentials using the given cookie-store.
   Returns the HTTP response map."
  [cookie-store username password]
  (http/post (str (server-address) "/login")
             {:form-params       {:username username :password password}
              :cookie-store      cookie-store
              :throw-exceptions  false
              :redirect-strategy :none}))

(defn- logout!
  "Performs a GET logout using the given cookie-store.
   Returns the HTTP response map."
  [cookie-store]
  (http/get (str (server-address) "/app/fi/logout")
            {:cookie-store      cookie-store
             :throw-exceptions  false
             :redirect-strategy :none}))

(defn- get-anti-csrf
  "Returns the URL-decoded anti-csrf token from the cookie store atom, or nil."
  [store-atom]
  (some-> ^Cookie (get @store-atom "anti-csrf-token") .getValue codec/url-decode))

(defn- api-command
  "Sends a JSON command to /api/command/<name> using the given store-atom and cookie-store.
   The anti-csrf token is read from the store atom.
   Returns the decoded response body map."
  [store-atom cookie-store command-name params]
  (let [csrf (get-anti-csrf store-atom)
        resp (http/post (str (server-address) "/api/command/" (name command-name))
                        {:headers           {"content-type"         "application/json;charset=utf-8"
                                             "x-anti-forgery-token" csrf}
                         :body              (json/encode params)
                         :cookie-store      cookie-store
                         :throw-exceptions  false
                         :redirect-strategy :none})]
    (decode-body resp)))

(defn- api-query
  "Sends a query to /api/query/<name> using the given store-atom and cookie-store.
   Returns decoded response body map."
  [store-atom cookie-store query-name params]
  (let [csrf (get-anti-csrf store-atom)
        resp (http/get (str (server-address) "/api/query/" (name query-name))
                       {:headers           {"x-anti-forgery-token" csrf}
                        :query-params      params
                        :cookie-store      cookie-store
                        :throw-exceptions  false
                        :redirect-strategy :none})]
    (decode-body resp)))

(defn- make-session-cookie
  "Creates a cookie with the session cookie name and the given value."
  ^Cookie [value]
  (doto (BasicClientCookie. (session-cookie-name) value)
    (.setDomain "localhost")
    (.setPath "/")))

(deftest session-cookie-set-on-login
  (testing "Login sets a session cookie and the session is usable"
    (let [[store cs] (make-cookie-store)
          resp (login! cs "pena" "pena")]
      (is (= 302 (:status resp)) "Login should redirect")
      (let [cookie-val (get-session-cookie store)]
        (is (some? cookie-val) "Session cookie must be set after login")
        ;; Verify the session actually works by querying user data
        (let [user-resp (api-query store cs :user {})]
          (is (true? (:ok user-resp)) "Querying user should succeed with the session")
          (is (= "pena" (get-in user-resp [:user :username]))))))))

(deftest session-persists-across-requests
  (testing "Session is maintained across multiple requests"
    (let [[store cs] (make-cookie-store)
          _                  (login! cs "pena" "pena")
          cookie-after-login (get-session-cookie store)]
      (is (some? cookie-after-login))
      ;; Make multiple queries - session should persist
      (is (true? (:ok (api-query store cs :user {}))))
      (is (true? (:ok (api-query store cs :user {}))))
      ;; Cookie should remain the same (no rotation on regular queries)
      (is (= cookie-after-login (get-session-cookie store))
          "Session cookie should not change on regular requests"))))

(deftest logout-invalidates-session
  (testing "After logout, the old session cookie is no longer valid"
    (let [[store cs] (make-cookie-store)
          _                    (login! cs "pena" "pena")
          cookie-before-logout (get-session-cookie store)]
      (is (some? cookie-before-logout))
      ;; Verify session works before logout
      (is (true? (:ok (api-query store cs :user {}))))
      (let [csrf-cookie-before-logout (get @store "anti-csrf-token")]
        ;; Logout
        (let [logout-resp (logout! cs)]
          (is (= 302 (:status logout-resp)) "Logout should redirect"))
        ;; Now try to use the session - should fail.
        ;; Build a fresh cookie store with the OLD session cookie to simulate reuse.
        (let [[stale-store stale-cs] (make-cookie-store)]
          (.addCookie ^CookieStore stale-cs (make-session-cookie cookie-before-logout))
          (when csrf-cookie-before-logout
            (.addCookie ^CookieStore stale-cs csrf-cookie-before-logout)
            (swap! stale-store assoc "anti-csrf-token" csrf-cookie-before-logout))
          (let [user-resp (api-query stale-store stale-cs :user {})]
            (is (= {:ok false :text "error.unauthorized"} user-resp)
                "Old session cookie should not work after logout")))))))

(deftest password-change-rotates-current-session
  (testing "Changing password rotates the session cookie for the current session"
    (let [[store cs] (make-cookie-store)
          _             (login! cs "pena" "pena")
          cookie-before (get-session-cookie store)]
      (is (some? cookie-before))
      ;; Change password
      (let [result (api-command store cs :change-password
                                {:oldPassword "pena"
                                 :newPassword "1234QWERasdfZXCV%&/(tyui"})]
        (is (true? (:ok result)) "Password change should succeed"))
      (let [cookie-after (get-session-cookie store)]
        (is (some? cookie-after) "Session cookie should still be present after password change")
        (is (not= cookie-before cookie-after)
            "Session cookie should be rotated (different value) after password change")
        ;; Session should still work
        (let [user-resp (api-query store cs :user {})]
          (is (true? (:ok user-resp))
              "Current session should still be valid after password change"))))))

(deftest password-change-invalidates-other-sessions
  (testing "Changing password invalidates other sessions for the same user"
    (let [;; Session 1: Pena logs in
          [store1 cs1] (make-cookie-store)
          _ (login! cs1 "pena" "pena")
          ;; Session 2: Pena logs in from "another browser"
          [store2 cs2] (make-cookie-store)
          _ (login! cs2 "pena" "pena")]
      ;; Both sessions should work
      (is (true? (:ok (api-query store1 cs1 :user {}))) "Session 1 should work initially")
      (is (true? (:ok (api-query store2 cs2 :user {}))) "Session 2 should work initially")
      ;; Change password from session 1
      (let [result (api-command store1 cs1 :change-password
                                {:oldPassword "pena"
                                 :newPassword "1234QWERasdfZXCV%&/(tyui"})]
        (is (true? (:ok result)) "Password change should succeed"))
      ;; Session 1 should still work (rotated but alive)
      (is (true? (:ok (api-query store1 cs1 :user {})))
          "Session 1 (password changer) should still be valid")
      ;; Session 2 should be invalidated
      (let [user-resp (api-query store2 cs2 :user {})]
        (is (= {:ok false, :text "error.unauthorized"} user-resp)
            "Session 2 (other session) should be invalidated after password change")))))
