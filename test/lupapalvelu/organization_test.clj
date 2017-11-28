(ns lupapalvelu.organization-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]))

(facts
 (let [organization {:operations-attachments {:kikka [["type-group-1" "type-id-123"]]}
                     :krysp {:good1 {:url "http://example.com" :ftpUser "user" :version "1.0.0"}
                             :good2 {:ftpUser "user" :version "1.0.0"}
                             :bad1 {:ftpUser "user" :version ""}
                             :bad2 {:version "1.0.0"}
                             :bad3 {}
                             :http-good1 {:version "1.0.0" :http {:url "foo"}}
                             :http-good2 {:version "1.0.0" :http {:url "foo" :nonsense "ok"}}
                             :http-good3 {:version "1.0.0" :ftpUser "" :http {:url "foo" :nonsense "ok"}}
                             :http-bad1 {:http {:url "foo"}}
                             :http-bad2 {:version "1.0.0" :http {:url ""}}
                             :http-bad3 {:version "1.0.0" :ftpUser "" :http {:url "" :nonsense "ok"}}
                             }}
       valid-op     {:name "kikka"}
       invalid-op   {:name "kukka"}]
   (get-organization-attachments-for-operation organization valid-op) => [["type-group-1" "type-id-123"]]
   (get-organization-attachments-for-operation organization invalid-op) => nil
   (fact "KRYSP integration defined either FTP or HTTP"                        ; none of the required can't be blank
     (krysp-integration? organization :good1) => true?
     (krysp-integration? organization :good2) => true?
     (fact "version can't be blank"
       (krysp-integration? organization :bad1) => false?)
     (fact "ftp user required"
       (krysp-integration? organization :bad2) => false?)
     (krysp-integration? organization :bad3) => false?
     (krysp-integration? organization :http-good1) => true?
     (krysp-integration? organization :http-good2) => true?
     (fact "blank ftpUser ok if http defined"
       (krysp-integration? organization :http-good2) => true?)
     (fact "version needed"
       (krysp-integration? organization :http-bad1) => false?)
     (fact "http url needed"
       (krysp-integration? organization :http-bad2) => false?)
     (fact "ftpUser and http.url blanks"
       (krysp-integration? organization :http-bad3) => false?))))

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
    (update-assignment-trigger {:id ..org-id..} {:id ..trigger-id.. :targets ..targets.. :handlerRole {:id ..roleid..} :description ..description..} ..trigger-id..) => "done"
    (provided (mongo/update-by-query :organizations
                                     {:assignment-triggers {"$elemMatch" {:id ..trigger-id..}}, :_id ..org-id..}
                                     {"$set" {:assignment-triggers.$.targets ..targets.., :assignment-triggers.$.handlerRole {:id ..roleid..}, :assignment-triggers.$.description ..description..}}) => "done")
    (provided (#'lupapalvelu.organization/user-created? ..trigger-id..) => false)
    (provided (mongo/update-by-query :assignments {:trigger ..trigger-id..} {$set {:description ..description..}}) => "this is also called, function return value is not used"))

  (fact "remove handler role from existing trigger"
    (update-assignment-trigger {:id ..org-id..} {:id ..trigger-id.. :targets ..targets.. :handlerRole {:id nil :name nil} :description ..description..} ..trigger-id..) => "done"
    (provided (mongo/update-by-query :organizations
                                     {:assignment-triggers {"$elemMatch" {:id ..trigger-id..}}, :_id ..org-id..}
                                     {"$set" {:assignment-triggers.$.targets ..targets.., :assignment-triggers.$.description ..description..}
                                      "$unset" {:assignment-triggers.$.handlerRole 1}}) => "done")
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

(let [permit-type :R
      conf (fn [c] {:krysp {permit-type c}})]

  (facts "resolve-krysp-wfs"
    (against-background
      [(get-credentials anything) => ["pena" "pena"]]
      (fact "url must be present"
        (resolve-krysp-wfs (conf {:url "foo" :version "1"}) permit-type) => (contains {:url "foo" :version "1"})
        (resolve-krysp-wfs (conf {:url nil :version "1"}) permit-type) => nil
        (resolve-krysp-wfs (conf {:buildingUrl "foo" :version "1"}) permit-type) => nil)))

  (facts "resolve-building-wfs"
    (against-background
      [(get-credentials anything) => ["pena" "pena"]]
      (fact "buildingUrl must be present"
        (resolve-building-wfs (conf {:url "foo" :version "1"}) permit-type) => nil
        (resolve-building-wfs (conf {:url nil :version "1"}) permit-type) => nil
        (resolve-building-wfs (conf {:buildingUrl "foo" :version "1"}) permit-type) => (contains {:url "foo" :version "1"}))))

  (fact "get-building-wfs without urls"
    (get-building-wfs ..query.. permit-type) => nil
    (provided
      (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:version "1" :username "foo"})))

  (fact "get-building-wfs with :url"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1"})
    (provided
      (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:url "foo" :version "1"})))

  (fact "get-building-wfs with :buildingUrl 1 (returned as :url)"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1"})
    (provided
      (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:buildingUrl "foo" :version "1"})))

  (fact "get-building-wfs - :buildingUrl is bigger priority (returned as :url)"
    (get-building-wfs ..query.. permit-type) => (just {:url "foo" :version "1"})
    (provided
      (mongo/select-one :organizations ..query.. [:krysp]) => (conf {:buildingUrl "foo" :version "1" :url "jee"}))))
