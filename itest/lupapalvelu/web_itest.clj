(ns lupapalvelu.web-itest
  (:require [lupapalvelu.fixture.web :as web-fix]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.minimal-util :as min-util]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

(defn init []
  (let [store (atom {})]
    [store {:cookie-store      (->cookie-store store)
            :redirect-strategy :none
            :throw-exceptions  false}]))

(defn- make-token-query [token action & kvs]
  (let [[store initial-params] (init)
        _                      (http-get (str (server-address) "/app/fi/bulletins")
                                         initial-params)
        params                 (-> initial-params
                                   (assoc :as :json)
                                   (assoc-in [:headers "x-anti-forgery-token"] (get-anti-csrf store))
                                   (dissoc :cookie-store))]
    (http-get (str (server-address) "/api/query/" (name action))
              (cond-> params
                (seq kvs) (assoc :query-params (apply hash-map kvs))
                token     (assoc-in [:headers "authorization"]
                                    (str "Bearer " token))))))


(with-test-system (-> {:fixture "web"}
                      test-system/with-remote-components)
  (facts "redirect-after-login"
    (let [[store initial-params] (init)
          resp                   (http-get (str (server-address)
                                                "/app/fi/applicant?redirect-after-login=/foo/bar")
                                           initial-params)
          params                 (assoc-in initial-params [:headers "x-anti-forgery-token"] (get-anti-csrf store))]
      (:status resp) => 302
      (:headers resp) => (contains {"Location" (str (server-address) (env/value :frontpage :fi))})

      (fact "Login succeeds"
        (let [{body :body
               :as  login} (http-post (str (server-address) "/api/login") (assoc params :form-params {:username "pena"
                                                                                                      :password "pena"} :as :json))]
          body => ok?
          login => http200?))

      (fact "url is returned"
        (let [new-params  (assoc-in initial-params [:headers "x-anti-forgery-token"] (get-anti-csrf store))
              {body :body
               :as  resp} (http-get (str (server-address) "/api/query/redirect-after-login") (assoc new-params :as :json))]
          resp => http200?
          (:url body) => "foo/bar"))))

  (facts "2x redirect-after-login"
    (let [[store initial-params] (init)
          resp                   (http-get (str (server-address)
                                                "/app/fi/applicant?redirect-after-login=/foo/bar&redirect-after-login=/foo/bar2")
                                           initial-params)
          params                 (assoc-in initial-params [:headers "x-anti-forgery-token"] (get-anti-csrf store))]
      (:status resp) => 302
      (:headers resp) => (contains {"Location" (str (server-address) (env/value :frontpage :fi))})

      (fact "Login succeeds"
        (let [{body :body
               :as  login} (http-post (str (server-address) "/api/login")
                                      (assoc params
                                             :form-params {:username "pena"
                                                           :password "pena"}
                                             :as :json))]
          body => ok?
          login => http200?))

      (let [new-params (assoc-in initial-params [:headers "x-anti-forgery-token"] (get-anti-csrf store))]
        (fact "url is returned"
          (let [{body :body
                 :as  resp} (http-get (str (server-address)
                                           "/api/query/redirect-after-login")
                                      (assoc new-params :as :json))]
            resp => http200?
            (:url body) => "foo/bar2"))

        (facts "Alive OK when logged in"
          (let [q              #(http-get (str (server-address) %) (assoc new-params :as :json))
                {body :body
                 :as  alive}   (q "/api/alive")
                {body2 :body
                 :as   alive2} (q "/api/alive?bulletins=1")]
            body => ok?
            alive => http200?
            body2 => ok?
            alive2 => http200?)))))

  (fact "Alive vs. logged out"
    (let [[store initial-params] (init)
          _                      (http-get (str (server-address) "/app/fi/bulletins")
                                           initial-params)
          params                 (assoc-in initial-params [:headers "x-anti-forgery-token"] (get-anti-csrf store))
          q                      (fn [alive & p]
                                   (http-get (str (server-address) alive)
                                             (-> params
                                                 (assoc :as :json)
                                                 (assoc :query-params (apply hash-map p)))))]
      (fact "Plain alive fails"
        (let [alive (q "/api/alive")]
          (:body alive) => unauthorized?
          alive => http200?))

      (fact "Bulletins alive is OK"
        (let [alive1 (q "/api/alive?bulletins=hello")
              alive2 (q "/api/alive" :bulletins true)]
          (:body alive1) => ok?
          alive1 => http200?
          (:body alive2) => ok?
          alive2 => http200?))))

  (fact "CSRF vs. format strings"
    (http-get (str (server-address)
                   "/api/query/%25'%20AND%202*3*8=6*8%20AND%20'Hack'!='Hack%25/allowed-actions")
              {:as               :json
               :throw-exceptions false})
    => (contains {:status 403
                  :body   (json/encode {:ok false :text :error.invalid-csrf-token})}))

  (facts "Token tests"
    (let [req-no-token          (make-token-query nil :allowed-actions)
          req-token-not-found   (make-token-query "no-such-token" :allowed-actions)
          req-valid-map-token   (make-token-query web-fix/valid-map-token :allowed-actions)
          req-valid-other-token (make-token-query web-fix/valid-other-token :allowed-actions)
          req-expired-token     (make-token-query web-fix/expired-map-token :allowed-actions)]

      (fact "No token"
        (:body req-no-token) => (json/encode {:ok false :text :error.invalid-csrf-token}))

      (fact "Token not found"
        (:body req-token-not-found) => (json/encode {:ok false :text :error.invalid-csrf-token}))

      (fact "Valid map token returns only actions used by the map"
        (:body req-valid-map-token) => {:actions {:allowed-actions             {:ok true}
                                                  :application                 {:ok true}
                                                  :applications-search-default {:ok true}
                                                  :user                        {:ok true}
                                                  :bind-attachment             {:ok true}
                                                  :neighbor-mark-done          {:ok true}}
                                        :ok      true})

      (fact "Valid other token returns all allowed actions (i.e. actions with :allowed-token-types :all)"
        (:body req-valid-other-token) => {:actions {:allowed-actions {:ok true}}
                                          :ok      true})

      (fact "Expired token"
        (:body req-expired-token) => "Unauthorized")

      (fact "Valid map token"
        (-> (make-token-query web-fix/valid-map-token :applications-search-default)
            :body
            :search) => {:applicationType "application"
                         :skip            0
                         :limit           100
                         :sort            {:asc   false
                                           :field "modified"}})

      (fact "Valid other token - unauthorized"
        (:body (make-token-query web-fix/valid-other-token :applications-search-default))
        => unauthorized?)

      (facts "Tokens vs. application access"
        (let [{app-id :id} (create-and-submit-application pena :propertyId tampere-property-id
                                                          :address "Cubic Cables"
                                                          :operation "ya-katulupa-kaapelityot")
              {out-id :id} (create-and-submit-application mikko :propertyId sipoo-property-id
                                                          :address "Out of Bounds"
                                                          :operation "aita")
              success      (contains {:body   (contains {:ok true :application truthy})
                                      :status 200})
              out          (contains {:body   {:ok   false
                                               :text "error.application-not-accessible"}
                                      :status 200})]
          (fact "Pena can access application via token"
            (make-token-query web-fix/valid-pena-token :application :id app-id)
            => success)
          (fact "Jussi can access application via token"
            (make-token-query web-fix/valid-map-token :application :id app-id)
            => success)
          (fact "Neither can access Mikko's Sipoo application"
            (make-token-query web-fix/valid-pena-token :application :id out-id)
            => out
            (make-token-query web-fix/valid-map-token :application :id out-id)
            => out)
          (fact "User must be enabled for token access"
            (command admin :set-user-enabled :email (min-util/email-for-key jussi)
                     :enabled "false") => ok?
            (let [{:keys [body status]} (make-token-query web-fix/valid-map-token
                                                          :application
                                                          :id app-id)]
              status => 403
              body => (json/encode {:ok false :text :error.invalid-csrf-token}))))))))
