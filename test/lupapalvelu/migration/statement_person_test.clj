(ns lupapalvelu.migration.statement-person-test
  (:require [lupapalvelu.migration.migrations :refer [add-missing-person-data-to-statement]]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.user :as user]
            [midje.sweet :refer :all]
            [sade.schema-generators :as gen]
            [schema-tools.core :as st]))

(defn generate-statement []
  (->  statement/Statement
       (st/update :person st/required-keys)
       gen/generate))

(facts add-missing-person-data-to-statement
  (fact "no person"
    (-> (generate-statement)
        (dissoc :person :metadata)
        (add-missing-person-data-to-statement)
        :person)
    => {:email "" :userId "" :name "" :text ""}
    (provided (user/get-user-by-email "") => nil))

  (fact "everything is fine"
    (let [statement (-> (generate-statement)
                        (dissoc :metadata)
                        (assoc-in [:person :email] ..email..))]
      (add-missing-person-data-to-statement statement)
      => statement
      (provided (user/get-user-by-email ..email..) => ..userId..)))

  (fact "only missing fields are updated - no name"
    (-> (generate-statement)
        (dissoc :metadata)
        (update :person dissoc :name)
        (update :person assoc :userId ..userId.. :email ..email.. :text ..text..)
        (add-missing-person-data-to-statement)
        :person)
    => (contains {:name "" :userId ..userId.. :email ..email.. :text ..text..})
    (provided (user/get-user-by-email ..email..) => {:id ..userId..}))

  (fact "no user id"
    (-> (generate-statement)
        (dissoc :metadata)
        (update :person dissoc :userId)
        (update :person assoc :email ..email..)
        (add-missing-person-data-to-statement)
        :person)
    => (contains {:userId ..userId..})
    (provided (user/get-user-by-email ..email..) => {:id ..userId..}))

  (fact "do not update if user id already set"
    (-> (generate-statement)
        (dissoc :metadata)
        (update :person assoc :userId ..originalUserId..)
        (update :person assoc :email ..email..)
        (add-missing-person-data-to-statement)
        :person)
    => (contains {:userId ..originalUserId..})
    (provided (user/get-user-by-email ..email..) => {:id ..anotherUserId..})))
