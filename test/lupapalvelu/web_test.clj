(ns lupapalvelu.web-test
  (:require [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.web :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [ring.mock.request :as r]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.security-headers :refer [bulletin-request?]])
  (:import [org.apache.commons.io IOUtils]))

(set! *warn-on-reflection* true)

(testable-privates lupapalvelu.web
                   ->hashbang
                   user-has-org-role?
                   authentication
                   session-not-recently-refreshed?)

(facts "facts about hashbang parsing"
  (->hashbang nil)               => nil
  (->hashbang [])                => nil
  (->hashbang [nil])             => nil
  (->hashbang "http://foo")      => nil
  (->hashbang 1234)              => nil
  (->hashbang {:foo "bar"})      => nil
  (->hashbang "foo")             => "foo"
  (->hashbang "/foo")            => "foo"
  (->hashbang "!/foo")           => "foo"
  (->hashbang "%21/foo")         => "foo"
  (->hashbang "#!/foo")          => "foo"
  (->hashbang "/../../foo")      => "foo"
  (->hashbang "/../../#!foo")    => "foo"
  (->hashbang ["#!/foo" "#!/bar"]) => "bar")

(fact {:a 1 :b 2}
  => (and (contains {:a 1})
          (contains {:b 2})))

(facts "parse-json-body"
  (parse-json-body {:body (IOUtils/toInputStream "{\"a\": true, \"b\": 42}")
                    :content-type "application/json;charset=utf-8"})
    => (contains {:params {:a true :b 42}
                  :json {:a true :b 42}})
    (parse-json-body {:body (IOUtils/toInputStream "{\"a\": true, .;!€#. NO CARRIER")
                      :content-type "application/json;charset=utf-8"})
    => :invalid-json)

(facts "parse-json-body-middleware"
  (let [handler (parse-json-body-middleware identity)]
    (fact "valid json is parsed"
      (let [req {:body         (IOUtils/toInputStream "{\"a\": true, \"b\": 42}")
                 :content-type "application/json;charset=utf-8"}]
        (handler req) => (assoc req :json {:a true :b 42} :params {:a true :b 42})))
    (fact "invalid json returns bad request"
      (let [req {:body         (IOUtils/toInputStream "{\"a\": true, .;!€#. NO CARRIER")
                 :content-type "application/json;charset=utf-8"}]
        (handler req) => {:status 400 :body "Invalid JSON body"}))))

(facts "user-has-org-role? test"
  (let [auth  (->> minimal/users
                   (filter (comp (partial = "sonja.sibbo@sipoo.fi") :email))
                   (first))
        admin (->> minimal/users
                   (filter (comp (partial = "admin@sipoo.fi") :email))
                   (first))]
    (user-has-org-role? :authorityAdmin auth) => falsey
    (user-has-org-role? :authorityAdmin admin) => truthy)

  (let [user {:role "authority"
              :orgAuthz {:org1 #{:foo}
                         :org2 #{:bar}}}]
    (user-has-org-role? :foo user) => truthy
    (user-has-org-role? :bar user) => truthy
    (user-has-org-role? :boz user) => falsey))

(facts "session-timeout-handler test"
  (fact "request without session is passed through as such"
    (let [req {:uri "/foo/bar"}]
      (session-timeout-handler identity req) => req))

  (fact "request without session to a path requiring session refresh is passed through as such"
    (let [req {:uri "/api/command/foo"}]
      (session-timeout-handler identity req) => req))

  (fact "user is removed from request session if session has expired"
    (session-timeout-handler identity
                             {:uri     "/foo/bar"
                              :session {:expires 0
                                        :user    {:foo "bar"}}}) => {:uri     "/foo/bar"
                                                                     :session {:expires 0}})

  (fact "user is removed from request session if session has expired and request is made to a path requiring session refresh"
    (session-timeout-handler identity
                             {:uri     "/api/command/foo"
                              :session {:expires 0
                                        :user    {:foo "bar"}}}) => {:uri     "/api/command/foo"
                                                                     :session {:expires 0}})

  (fact "valid session is not removed from request"
    (let [request-valid {:uri     "/foo/bar"
                         :session {:expires (+ (now) 1000)}}]
      (session-timeout-handler identity request-valid) => request-valid))

  (fact "valid session is refreshed for a request made to a path requiring session refresh"
    (let [original-expires (+ (now) 1000)
          req              {:uri     "/api/command/foo"
                            :session {:expires original-expires}}]
      (->> req
           (session-timeout-handler identity)
           :session
           :expires
           (< original-expires)) => truthy))

  (fact "valid session expiration is set if request session is expired but handler creates valid session user"
    (let [now     (now)
          req     {:uri     "/api/command/foo"
                   :session {:expires 1}}
          handler (fn [_request] {:session {:user {:expires (+ now 1000)}}})]
      (->> req
           (session-timeout-handler handler)
           :session
           :expires
           (< now)) => truthy)))

(let [n                  1706200728021
      five-mins-from-now 1706201028021
      new-user           {:id       "hi" :username "world"
                          :orgAuthz {}   :expires  five-mins-from-now}
      ad-new-user        (assoc new-user :ad-login? true)]
  (against-background
    [(now) => n
     (date/now) => (date/zoned-date-time n)
     (usr/get-user {:id "hi" :enabled true})
     => {:id "hi" :username "world"}]
    (facts "AD login flag survives user update"
      (authentication identity {:session {:user {:id       "hi"
                                                 :username "hello"
                                                 :expires  12345}}})
      => {:user new-user :session {:user new-user}}
      (authentication identity {:session {:user {:id        "hi"
                                                 :username  "hello"
                                                 :ad-login? true
                                                 :expires   12345}}})
      => {:user ad-new-user :session {:user ad-new-user}}
      (authentication identity {:session {:user {:id        "hi"
                                                 :username  "hello"
                                                 :ad-login? true
                                                 :what      "ever"
                                                 :expires   (inc n)}}})
      => {:user    {:id        "hi"
                    :username  "hello"
                    :ad-login? true
                    :what      "ever"
                    :expires   (inc n)}
          :session {:user {:id        "hi"
                           :username  "hello"
                           :ad-login? true
                           :what      "ever"
                           :expires   (inc n)}}})))

(facts "bulletin-redirects"
  (against-background (name->bulletin-redirect-conf) => {"hyvinkaa" {:lang "fi" :name "hyvinkaa" :org-id "106-R"}
                                                         "sipoo"    {:lang "fi" :name "sipoo" :org-id "753-R"}
                                                         "sibbo"    {:lang "sv" :name "sibbo" :org-id "753-R"}})
  (let [handler (constantly {:status 200})
        mw      (bulletin-redirects handler)]
    (fact "non-julkipano"
      (let [r (r/request :get "http://localhost/foo")]
        (mw r) => {:status 200}))
    (facts "old julkipano site"
      (fact "julkipano root"
        (let [r (r/request :get "http://julkipano.fi")]
          (mw r) => {:headers {"Location" "/app/fi/bulletins"}
                     :status  302
                     :body    ""}))
      (fact "julkipano unknown municipality"
        (let [r (r/request :get "http://julkipano.fi/i-dont-exist")]
          (mw r) => {:status 200}))
      (fact "julkipano hyvinkää"
        (let [r (r/request :get "http://julkipano.fi/hyvinkaa")]
          (mw r) => {:headers {"Location" "/app/fi/local-bulletins?organization=106-R&bulletinProcess=julkipano"}
                     :status  302
                     :body    ""}))
      (fact "julkipano sipoo"
        (let [r (r/request :get "http://julkipano.fi/sipoo")]
          (mw r) => {:headers {"Location" "/app/fi/local-bulletins?organization=753-R&bulletinProcess=julkipano"}
                     :status  302
                     :body    ""}))
      (fact "julkipano sibbo"
        (let [r (r/request :get "http://julkipano.fi/sibbo")]
          (mw r) => {:headers {"Location" "/app/sv/local-bulletins?organization=753-R&bulletinProcess=julkipano"}
                     :status  302
                     :body    ""})))
    (facts "new kuulutukset site"
      (fact "kuulutukset root"
        (let [r (r/request :get "http://kuulutukset.lupapiste.fi")]
          (mw r) => {:headers {"Location" "/app/fi/bulletins"}
                     :status  302
                     :body    ""}))
      (fact "julkipano unknown municipality"
        (let [r (r/request :get "http://kuulutukset.lupapiste.fi/i-dont-exist")]
          (mw r) => {:status 200}))
      (fact "julkipano hyvinkää"
        (let [r (r/request :get "http://kuulutukset.lupapiste.fi/hyvinkaa")]
          (mw r) => {:headers {"Location" "/app/fi/local-bulletins?organization=106-R&bulletinProcess=kuulutus"}
                     :status  302
                     :body    ""}))
      (fact "julkipano sipoo"
        (let [r (r/request :get "http://kuulutukset.lupapiste.fi/sipoo")]
          (mw r) => {:headers {"Location" "/app/fi/local-bulletins?organization=753-R&bulletinProcess=kuulutus"}
                     :status  302
                     :body    ""}))
      (fact "julkipano sibbo"
        (let [r (r/request :get "http://kuulutukset.lupapiste.fi/sibbo")]
          (mw r) => {:headers {"Location" "/app/sv/local-bulletins?organization=753-R&bulletinProcess=kuulutus"}
                     :status  302
                     :body    ""})))))

(facts "get-name->bulletin-redirect-conf"
  (against-background (mongo/select :organizations anything [:scope])
    => [{:id "106-R" :scope [{:bulletins {:redirect-names [{:lang "fi" :name "hyvinkaa"}
                                                           {:lang "sv" :name "hyvinkaa"}]}}]}
        {:id "753-R" :scope [{:bulletins {:redirect-names [{:lang "en" :name "sipoo"}
                                                           {:lang "sv" :name "sibbo"}
                                                           {:lang "fi" :name "sipoo"}]}}]}])
  (let [get-name #'lupapalvelu.web/get-name->bulletin-redirect-conf]
    (fact "bulletin map is produced"
      (get-name) => {"hyvinkaa" {:lang "fi" :name "hyvinkaa" :org-id "106-R"}
                     "sibbo"    {:lang "sv" :name "sibbo" :org-id "753-R"}
                     "sipoo"    {:lang "fi" :name "sipoo" :org-id "753-R"}})))

(facts "bulletin-request?"
  (doseq [uri ["" "/app/en/local-bulletins" "/app/fi/bulletins" "/app//local-bulletins"]]
    (fact {:midje/description (format "URI '%s' should not be a valid bulletin request" uri)}
      (bulletin-request? {:uri uri}) => false))
  (doseq [referer ["" "/app/en/local-bulletins" "/app/fi/bulletins"
                   "https://foobar.lupapiste.fi/app/fi/local-bulletins"
                   "https://julkipano.lupapiste.fi/app/en/local-bulletins"
                   "https://julki.lupapiste.fi/app/fi/local-bulletins"
                   "https://julkipanofoobar.lupapiste.fi/app/fi/local-bulletins"]]
    (fact {:midje/description (format "URI '%s' should not be a valid bulletin request referrer" referer)}
      (bulletin-request? {:headers {"referer" referer}
                          :uri     "whatever"}) => false))
  (doseq [lang ["fi" "sv"]]
    (let [uri (format "/app/%s/local-bulletins" lang)]
      (fact {:midje/description (format "URI '%s' is valid bulletin request" uri)}
        (bulletin-request? {:uri uri}) => true))
    (doseq [env ["-qa" "-dev" ""]]
      (let [referrer (format "https://julkipano%s.lupapiste.fi/app/%s/local-bulletins"
                             env lang)]
        (fact {:midje/description (format "Referrer '%s' is a valid bulletin request referrer" referrer)}
          (bulletin-request? {:headers {"referer" referrer}
                              :uri     "whatever"}) => true))
       (let [referrer (format "https://kuulutukset%s.lupapiste.fi/app/%s/local-bulletins"
                              env lang)]
         (fact {:midje/description (format "Referrer '%s' is a valid bulletin request" referrer)}
           (bulletin-request? {:headers {"referer" referrer}
                               :uri     "whatever"}) => true)))))

(facts "session-not-recently-refreshed?"
  (let [n (now)]
    (fact "no session at all is treated as not recently refreshed"
      (session-not-recently-refreshed? {} n) => true)

    (fact "session with no :expires is treated as not recently refreshed"
      (session-not-recently-refreshed? {:session {}} n) => true)

    (fact "session with :expires of 0 is not recently refreshed"
      (session-not-recently-refreshed? {:session {:expires 0}} n) => true)

    (fact "session that was just refreshed (expires = now + timeout) is considered recently refreshed"
      (let [expires (+ n default-session-timeout)]
        (session-not-recently-refreshed? {:session {:expires expires}} n) => false))

    (fact "session refreshed exactly at the one-minute boundary (expires = now + timeout - 60000) is still recent"
      (let [expires (- (+ n default-session-timeout) 60000)]
        (session-not-recently-refreshed? {:session {:expires expires}} n) => false))

    (fact "session refreshed just over a minute ago (expires = now + timeout - 60001) is not recently refreshed"
      (let [expires (- (+ n default-session-timeout) 60001)]
        (session-not-recently-refreshed? {:session {:expires expires}} n) => true))

    (fact "custom session timeout is respected when session is fresh"
      (let [custom-timeout 3600000
            expires        (+ n custom-timeout)
            req            {:session {:expires              expires
                                      :user {:session-timeout custom-timeout}}}]
        (session-not-recently-refreshed? req n) => false))

    (fact "custom session timeout is respected when session is stale"
      (let [custom-timeout 3600000
            expires        (- (+ n custom-timeout) 60001)
            req            {:session {:expires              expires
                                      :user {:session-timeout custom-timeout}}}]
        (session-not-recently-refreshed? req n) => true))))

