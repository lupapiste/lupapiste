(ns lupapalvelu.organization-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]))

(facts
 (let [organization {:operations-attachments {:kikka [["type-group-1" "type-id-123"]]}
                     :krysp {:good {:url "http://example.com" :ftpUser "user" :version "1.0.0"}
                             :bad1 {:ftpUser "user" :version "1.0.0"}
                             :bad2 {:version "1.0.0"}
                             :bad3 {}}}
       valid-op     {:name "kikka"}
       invalid-op   {:name "kukka"}]
   (get-organization-attachments-for-operation organization valid-op) => [["type-group-1" "type-id-123"]]
   (get-organization-attachments-for-operation organization invalid-op) => nil
   (fact "KRYSP integration defined"
         (krysp-integration? organization :good) => true?
         (krysp-integration? organization :bad1) => falsey
         (krysp-integration? organization :bad2) => falsey
         (krysp-integration? organization :bad3) => falsey)))

(facts upsert-handler-role!
  (fact "update existing"
    (upsert-handler-role! {:id ..org-id.. :handler-roles [{:id ..role-id..}]} {:id ..role-id.. :name ..name..}) => "done"
    (provided (update-organization ..org-id.. {$set {:handler-roles.0.id ..role-id.. :handler-roles.0.name ..name..}}) => "done"))

  (fact "insert new"
    (upsert-handler-role! {:id ..org-id.. :handler-roles [{:id ..role-id..}]} {:id ..new-role-id.. :name ..name..}) => "done"
    (provided (update-organization ..org-id.. {$set {:handler-roles.1.id ..new-role-id.. :handler-roles.1.name ..name..}}) => "done"))

  (fact "update existing when there is multiple handlers in org"
    (upsert-handler-role! {:id ..org-id.. :handler-roles [{:id ..role-id-0..} {:id ..role-id-1..} {:id ..role-id-2..} {:id ..role-id-3..}]} {:id ..role-id-2.. :name ..name..}) => "done"
    (provided (update-organization ..org-id.. {$set {:handler-roles.2.id ..role-id-2.. :handler-roles.2.name ..name..}}) => "done")))

(facts update-assignment-triggers
  (fact "update existing"
    (update-assignment-trigger {:id ..org-id..} {:id ..trigger-id.. :targets ..targets.. :handlerRole ..handlerRole... :description ..description..} ..trigger-id..) => "done"
    (provided (mongo/update-by-query :organizations
                                     {:assignment-triggers {"$elemMatch" {:id ..trigger-id..}}, :_id ..org-id..}
                                     {"$set" {:assignment-triggers.$.targets ..targets.., :assignment-triggers.$.handlerRole ..handlerRole..., :assignment-triggers.$.description ..description..}}) => "done")
    (provided (#'lupapalvelu.organization/user-created? ..trigger-id..) => false)
    (provided (mongo/update-by-query :assignments {:trigger ..trigger-id..} {$set {:description ..description..}}) => "this is also called, function return value is not used")))

(facts create-trigger
  (fact "create trigger with handler"
  (create-trigger 123 ["target.attachment"] {:id 456 :name {:fi "Nimi" :sv "Namn" :en "Name"}} "Description") => {:id 123
                                                                                                                  :targets ["target.attachment"]
                                                                                                                  :handlerRole {:id 456 :name {:fi "Nimi" :sv "Namn" :en "Name"}}
                                                                                                                  :description "Description"})
  (fact "create trigger without handler"
  (create-trigger 123 ["target.attachment"] nil "Description") => {:id 123
                                                                   :targets ["target.attachment"]
                                                                   :description "Description"}))
