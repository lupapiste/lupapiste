(ns lupapalvelu.migration.statement-person-test
  (:require [midje.sweet :refer :all]
            [sade.schema-generators :as gen]
            [lupapalvelu.user :as user]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.migration.migrations :refer [add-missing-person-data-to-statement]]
            [schema.core :as sc]))

(facts add-missing-person-data-to-statement
  (fact "no person"
    (-> (gen/generate statement/Statement)
        (dissoc :person :metadata)
        (add-missing-person-data-to-statement)
        :person)
    => {:email "" :userId "" :name "" :text ""}
    (provided (user/get-user-by-email "") => nil))

  (fact "everything is fine"
    (let [statement (-> (gen/generate statement/Statement)
                        (dissoc :metadata)
                        (assoc-in [:person :email] ..email..))]
      (add-missing-person-data-to-statement statement)
      => statement
      (provided (user/get-user-by-email ..email..) => ..userId..)))

  (fact "only missing fields are updated - no name"
    (-> (gen/generate statement/Statement)
        (dissoc :metadata)
        (update :person dissoc :name)
        (update :person assoc :userId ..userId.. :email ..email.. :text ..text..)
        (add-missing-person-data-to-statement)
        :person)
    => (contains {:name "" :userId ..userId.. :email ..email.. :text ..text..})
    (provided (user/get-user-by-email ..email..) => {:id ..userId..}))

  (fact "no user id"
    (-> (gen/generate statement/Statement)
        (dissoc :metadata)
        (update :person dissoc :userId)
        (update :person assoc :email ..email..)
        (add-missing-person-data-to-statement)
        :person)
    => (contains {:userId ..userId..})
    (provided (user/get-user-by-email ..email..) => {:id ..userId..}))

  (fact "do not update if user id already set"
    (-> (gen/generate statement/Statement)
        (dissoc :metadata)
        (update :person assoc :userId ..originalUserId..)
        (update :person assoc :email ..email..)
        (add-missing-person-data-to-statement)
        :person)
    => (contains {:userId ..originalUserId..})
    (provided (user/get-user-by-email ..email..) => {:id ..anotherUserId..})))
