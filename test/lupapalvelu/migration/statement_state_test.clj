(ns lupapalvelu.migration.statement-state-test
  (:require  [midje.sweet :refer :all]
             [sade.schemas :as schemas]
             [lupapalvelu.migration.migrations :as m]
             [lupapalvelu.statement :as statement]))


(fact "statement has state already"
  (-> (schemas/generate statement/Statement)
      (assoc :state :responded)
      (m/update-statement-state-by-status)
      (get :state)) 
  => :responded)

(fact "statement has state  but no status"
  (-> (schemas/generate statement/Statement)
      (assoc :state :draft)
      (dissoc :status)
      (m/update-statement-state-by-status)
      (get :state)) 
  => :draft)

(fact "statement has status but no state"
  (-> (schemas/generate statement/Statement)
      (assoc :status "puoltaa")
      (dissoc :state)
      (m/update-statement-state-by-status)
      (get :state)) 
  => :given)

(fact "statement has neither status or state"
  (-> (schemas/generate statement/Statement)
      (dissoc :state :status)
      (m/update-statement-state-by-status)
      (get :state)) 
  => :requested)
