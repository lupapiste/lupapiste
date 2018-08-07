(ns lupapalvelu.rest-docstore-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.rest.docstore-api :as api]))

(apply-remote-minimal)

(def organizations-address (str (server-address) "/rest/docstore/organizations"))

(def organization-address (str (server-address) "/rest/docstore/organization"))

(def allowed-types-address (str (server-address) "/rest/docstore/allowed-attachment-types"))

(def docstore-user-basic-auth ["docstore" "basicauth"])

(defn- api-call [address params]
  (http-get address (merge params {:throw-exceptions false})))

(defn- docstore-api-call [address query-params & [undecoded?]]
  (-> (api-call address (merge {:basic-auth docstore-user-basic-auth}
                               (when-not (empty? query-params)
                                 {:query-params query-params})))
      ((if undecoded? identity decode-response))))

(def all-docstore-orgs
  (->> (filter :docstore-info minimal/organizations)
       (mapv (fn [{:keys [docstore-info scope] :as org}]
               (-> (merge docstore-info (select-keys org [:id :name :municipalities]))
                   (dissoc :allowedTerminalAttachmentTypes)
                   (assoc :municipalities (->> scope
                                               (map api/municipality-info)
                                               (distinct))))))))

(def default-sipoo-docstore-info
  (first (filter #(= "753-R" (:id %)) all-docstore-orgs)))

(def inactive-orgs
  (remove :docStoreInUse all-docstore-orgs))

(def R-orgs
  (filter #(re-matches #".+-R(-.+)?" (:id %)) all-docstore-orgs))

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
      (-> (docstore-api-call organizations-address {}) :body :data) => all-docstore-orgs)

    (fact "organizations - status=all"
      (-> (docstore-api-call organizations-address {:status "all"}) :body :data) => all-docstore-orgs)

    (fact "organizations - status=active"
      (-> (docstore-api-call organizations-address {:status "active"}) :body :data) => [default-sipoo-docstore-info])

    (fact "organizations - status=inactive"
     (-> (docstore-api-call organizations-address {:status "inactive"}) :body :data) => inactive-orgs)

    (fact "organizations - permit-type limitation"
      (-> (docstore-api-call organizations-address {:permit-type "R"}) :body :data) => R-orgs)

    (fact "organizations - invalid status"
     (-> (docstore-api-call organizations-address {:status "zorblax"} true))
     => http400?))

  (facts "allowed-attachment-types"
    (fact "only returns organizations that have enabled docterminal"
      (let [resp-data (-> (docstore-api-call allowed-types-address {}) :body :data)]
        (count resp-data) => 1
        (-> resp-data first :id) => "753-R"))

    (fact "lists the allowed attachment types for the organization"
      (-> (docstore-api-call allowed-types-address {}) :body :data first :allowedTerminalAttachmentTypes)
      => []
      (command sipoo :set-docterminal-attachment-type :attachmentType "osapuolet.cv" :enabled true) => ok?
      (-> (docstore-api-call allowed-types-address {}) :body :data first :allowedTerminalAttachmentTypes)
      => ["osapuolet.cv"]))

  (fact "Docstore user cannot access other REST endpoints"
        (api-call (str (server-address) "/rest/submitted-applications")
                  {:basic-auth docstore-user-basic-auth})
        => http401?))
