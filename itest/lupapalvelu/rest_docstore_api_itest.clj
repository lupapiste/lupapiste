(ns lupapalvelu.rest-docstore-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.organization :as org]))

(apply-remote-minimal)

(def organizations-address (str (server-address) "/rest/docstore/organizations"))

(def organization-address (str (server-address) "/rest/docstore/organization"))

(def docstore-user-basic-auth ["docstore" "basicauth"])

(defn- api-call [address params]
  (http-get address (merge params {:throw-exceptions false})))

(defn- docstore-api-call [address query-params & [undecoded?]]
  (-> (api-call address (merge {:basic-auth docstore-user-basic-auth}
                               (when-not (empty? query-params)
                                 {:query-params query-params})))
      ((if undecoded? identity decode-response))))

(def default-sipoo-docstore-info
  (assoc org/default-docstore-info
         :id "753-R"
         :name (i18n/supported-langs-map (constantly "Sipoon rakennusvalvonta"))
         :municipalities [{:id "753", :name {:en "Sipoo", :fi "Sipoo", :sv "Sibbo"}}]))

(facts "REST interface for organization docstore information"

  (fact "not available as anonymous user"
    (api-call organizations-address {}) => http401?
    (api-call organization-address {:query-params {:id "753-R"}}) => http401?)

  (fact "Docstore user can access"
    (docstore-api-call organizations-address {}) => http200?
    (docstore-api-call organization-address {:id "753-R"}) => http200?)

  (facts "Queries return correct information"

    (fact "organization"
      (-> (docstore-api-call organization-address {:id "753-R"}) :body :data)
      => default-sipoo-docstore-info)

    (fact "organization - missing organization"
          (-> (docstore-api-call organization-address {:id "Nonexistent"} true))
          => http404?)

    (fact "organizations"
      (-> (docstore-api-call organizations-address {}) :body :data)
      => [default-sipoo-docstore-info])

    (fact "organizations - status=all"
      (-> (docstore-api-call organizations-address {:status "all"}) :body :data)
      => [default-sipoo-docstore-info])

    (fact "organizations - status=active"
      (-> (docstore-api-call organizations-address {:status "active"}) :body :data)
      => [])
    (fact "organizations - status=inactive"
     (-> (docstore-api-call organizations-address {:status "inactive"}) :body :data)
     => [default-sipoo-docstore-info])

    (fact "organizations - permit-type limitation"
      (-> (docstore-api-call organizations-address {:permit-type "YA"}) :body :data) => [])

    (fact "organizations - invalid status"
     (-> (docstore-api-call organizations-address {:status "zorblax"} true))
     => http400?))

  (fact "Docstore user cannot access other REST endpoints"
        (api-call (str (server-address) "/rest/submitted-applications")
                  {:basic-auth docstore-user-basic-auth})
        => http401?))
