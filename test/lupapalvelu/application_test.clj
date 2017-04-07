(ns lupapalvelu.application-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$set $push]]
            [swiss.arrows :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application :refer :all]
            [lupapalvelu.application-api]
            [lupapalvelu.company :as com]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.user :as usr]
            [lupapalvelu.ya :as ya]
            ))

(fact "update-document"
  (update-application {:application ..application.. :data {:id ..id..}} ..changes..) => nil
  (provided
    ..application.. =contains=> {:id ..id..}
    (mongo/update-by-query :applications {:_id ..id..} ..changes..) => 1))

(testable-privates lupapalvelu.application-api add-operation-allowed? validate-handler-role validate-handler-role-not-in-use validate-handler-id-in-application validate-handler-in-organization)
(testable-privates lupapalvelu.application required-link-permits new-attachment-types-for-operation attachment-grouping-for-type person-id-masker-for-user)
(testable-privates lupapalvelu.ya validate-link-agreements-signature validate-link-agreements-state)

(facts "mark-indicators-seen-updates"
  (let [timestamp 123
        expected-seen-bys (-<>> ["comments" "statements" "verdicts"
                                 "authority-notices" "info-links"]
                               (map (partial format "_%s-seen-by.pena"))
                               (zipmap <> (repeat timestamp)))
        expected-attachment (assoc expected-seen-bys :_attachment_indicator_reset timestamp)
        expected-docs (assoc expected-attachment "documents.0.meta._indicator_reset.timestamp" timestamp)]
    (mark-indicators-seen-updates {} {:id "pena"} timestamp) => expected-seen-bys
    (mark-indicators-seen-updates {:documents []} {:id "pena", :role "authority"} timestamp) => expected-attachment
    (mark-indicators-seen-updates {:documents [{}]} {:id "pena", :role "authority"} timestamp) => expected-docs))

(defn find-by-schema? [docs schema-name]
  (domain/get-document-by-name {:documents docs} schema-name))

(defn has-schema? [schema] (fn [docs] (find-by-schema? docs schema)))

(facts filter-repeating-party-docs
  (filter-party-docs 1 ["a" "b" "c"] true) => (just "a")
  (provided
    (schemas/get-schema 1 "a") => {:info {:type :party :repeating true}}
    (schemas/get-schema 1 "b") => {:info {:type :party :repeating false}}
    (schemas/get-schema 1 "c") => {:info {:type :foo :repeating true}}))

(facts required-link-permits
  (fact "Muutoslupa"
    (required-link-permits {:permitSubtype "muutoslupa"}) => 1)
  (fact "Aloitusilmoitus"
    (required-link-permits {:primaryOperation {:name "aloitusoikeus"}}) => 1)
  (fact "Poikkeamis"
    (required-link-permits {:primaryOperation {:name "poikkeamis"}}) => 0)
  (fact "ya-jatkoaika, primary"
    (required-link-permits {:primaryOperation {:name "ya-jatkoaika"}}) => 1)
  (fact "ya-jatkoaika, secondary"
    (required-link-permits {:secondaryOperations [{:name "ya-jatkoaika"}]}) => 1)
  (fact "ya-jatkoaika x 2"
    (required-link-permits {:secondaryOperations [{:name "ya-jatkoaika"} {:name "ya-jatkoaika"}]}) => 2)
  (fact "muutoslupa+ya-jatkoaika"
    (required-link-permits {:permitSubtype "muutoslupa" :secondaryOperations [{:name "ya-jatkoaika"}]}) => 2)
  (fact "muutoslupa+aloitusilmoitus+ya-jatkoaika"
    (required-link-permits {:permitSubtype "muutoslupa" :primaryOperation {:name "aloitusoikeus"} :secondaryOperations [{:name "ya-jatkoaika"}]}) => 3))

(facts "Add operation allowed"
       (let [not-allowed-for #{;; R-operations, adding not allowed
                               :raktyo-aloit-loppuunsaat :jatkoaika :aloitusoikeus :suunnittelijan-nimeaminen
                               :tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :aiemmalla-luvalla-hakeminen
                               ;; KT-operations, adding not allowed
                               :rajankaynti
                               ;; YL-operations, adding not allowed
                               :pima
                               ;; YM-operations, adding not allowed
                               :muistomerkin-rauhoittaminen :jatteen-keraystoiminta :lannan-varastointi
                               :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan
                               :koeluontoinen-toiminta :maa-ainesten-kotitarveotto :ilmoitus-poikkeuksellisesta-tilanteesta}
        error {:ok false :text "error.add-operation-not-allowed"}]

    (doseq [operation op/operations]
      (let [[op {permit-type :permit-type}] operation
            application {:primaryOperation {:name (name op)} :permitSubtype nil}
            operation-allowed (add-operation-allowed? {:application application})]
        (fact {:midje/description (name op)}
          (if (or (not (contains? #{"R" "KT" "P" "YM"} permit-type)) (not-allowed-for op))
            (fact "Add operation not allowed" operation-allowed => error)
            (fact "Add operation allowed" operation-allowed => nil?)))))

    (fact "Add operation not allowed for :muutoslupa"
      (add-operation-allowed? {:application {:primaryOperation {:name "kerrostalo-rivitalo"} :permitSubtype :muutoslupa}}) => error)))

(fact "validate-has-subtypes"
  (validate-has-subtypes {:application {:permitType "P"}}) => nil
  (validate-has-subtypes {:application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}}) => nil
  (validate-has-subtypes {:application {:permitType "R"}}) => {:ok false :text "error.permit-has-no-subtypes"}
  (validate-has-subtypes nil) => {:ok false :text "error.permit-has-no-subtypes"})

(facts "State transitions"
  (let [pena {:username "pena", :firstName "Pena" :lastName "Panaani"}]
    (fact "update"
      (state-transition-update :open 1 {:created 0 :permitType "R"} pena) => {$set {:state :open, :opened 1, :modified 1}, $push {:history {:state :open, :ts 1, :user pena}}}
      (state-transition-update :open 1 {:opened nil  :permitType "R"} pena) => {$set {:state :open, :opened 1, :modified 1}, $push {:history {:state :open, :ts 1, :user pena}}}
      (state-transition-update :submitted 2 {:created 0 :opened 1  :permitType "R"} pena) => {$set {:state :submitted, :submitted 2, :modified 2}, $push {:history {:state :submitted, :ts 2, :user pena}}}
      (state-transition-update :verdictGiven 3 {:created 0 :opened 1 :submitted 2  :permitType "R"} pena) => {$set {:state :verdictGiven, :modified 3}, $push {:history {:state :verdictGiven, :ts 3, :user pena}}})

    (fact "re-update"
      (state-transition-update :open 4 {:opened 3  :permitType "R"} pena) => {$set {:state :open, :modified 4}, $push {:history {:state :open, :ts 4, :user pena}}}
      (state-transition-update :submitted 5 {:submitted 4 :permitType "R"} pena) => {$set {:state :submitted, :modified 5}, $push {:history {:state :submitted, :ts 5, :user pena}}}
      (state-transition-update :constructionStarted 6 {:started 5 :permitType "R"} pena) => {$set {:state :constructionStarted, :modified 6}, $push {:history {:state :constructionStarted, :ts 6, :user pena}}})))

(fact "Valid permit types pre-checker"
      (let [error {:ok false :text "error.unsupported-permit-type"}
            m1 {:R [] :P :all}
            m2 {:R ["tyonjohtaja-hakemus"] :P :all}
            m3 {:R ["tyonjohtaja-hakemus" :empty]}]
        (permit/valid-permit-types m1 {:application {:permitType "R"}}) => nil
        (permit/valid-permit-types m1 {:application {:permitType "P"}}) => nil
        (permit/valid-permit-types m1 {:application {:permitType "R" :permitSubtype "tyonjohtaja-hakemus"}}) => error
        (permit/valid-permit-types m1 {:application {:permitType "P" :permitSubtype "foo"}}) => nil
        (permit/valid-permit-types m2 {:application {:permitType "R"}}) => error
        (permit/valid-permit-types m2 {:application {:permitType "P"}}) => nil
        (permit/valid-permit-types m2 {:application {:permitType "R" :permitSubtype "tyonjohtaja-hakemus"}}) => nil
        (permit/valid-permit-types m2 {:application {:permitType "R" :permitSubtype "foobar"}}) => error
        (permit/valid-permit-types m2 {:application {:permitType "P" :permitSubtype "foo"}}) => nil
        (permit/valid-permit-types m3 {:application {:permitType "R"}}) => nil
        (permit/valid-permit-types m3 {:application {:permitType "P"}}) => error
        (permit/valid-permit-types m3 {:application {:permitType "R" :permitSubtype "tyonjohtaja-hakemus"}}) => nil
        (permit/valid-permit-types m3 {:application {:permitType "R" :permitSubtype "foobar"}}) => error
        (permit/valid-permit-types m3 {:application {:permitType "P" :permitSubtype "foo"}}) => error))

(facts "Previous app state"
  (let [user {:username "pena"}
        now (now)
        state-seq [:one :two :three :four :five]]

    (dotimes [i (count state-seq)]
      (let [prev-state (get-previous-app-state
                         {:history (map
                                     #(history-entry % now user)
                                     (take (+ i 1) state-seq))})]
        (if (= i 0)
          (fact "no previous state" prev-state => nil)
          (fact {:midje/description prev-state}
            prev-state => (nth state-seq (- i 1))))))

    (fact "no previous state if no history"
      (get-previous-app-state nil) => nil
      (get-previous-app-state []) => nil)))

(facts "Get previous state (history)"
  (let [state-seq [:one nil :two :three nil nil]
        now (now)
        history {:history
                 (map-indexed
                   (fn [i state] (history-entry state (+ now i) {:username "Pena"}))
                   state-seq)}]
    (fact "only entries with :state are regarded"
      (get-previous-app-state history) => :two)))

(facts "Primary operation prechecks"
       (let [app {:application {:primaryOperation {:name "foobar"}}}
             err {:ok false :text "error.unsupported-primary-operation"}]
         (fact "Allow"
               ((allow-primary-operations #{:hii :foobar}) app) => nil?
               ((allow-primary-operations #{:foobar}) app) => nil?
               ((allow-primary-operations #{:hii}) app) => err
               ((allow-primary-operations #{}) app) => err)
         (fact "Reject"
               ((reject-primary-operations #{:hii :foobar}) app) => err
               ((reject-primary-operations #{:foobar}) app) => err
               ((reject-primary-operations #{:hii}) app) => nil?
               ((reject-primary-operations #{}) app) => nil?)))

(facts validate-handler-id-in-application
  (fact "no handlers"
    (:ok (validate-handler-id-in-application {:data {:handlerId ..handler-id..} :application {:handlers []}})) => false)

  (fact "no matching handlers"
    (:ok (validate-handler-id-in-application {:data {:handlerId ..handler-id..} :application {:handlers [{:id ..another-handler-id..}]}})) => false)

  (fact "matching handlers"
    (validate-handler-id-in-application {:data {:handlerId ..handler-id..} :application {:handlers [{:id ..handler-id..}]}}) => nil)

  (fact "no handler id given"
    (validate-handler-id-in-application {:data {:handlerId nil} :application {:handlers [{:id ..handler-id..}]}}) => nil))

(facts validate-handler-in-organization
  (fact "handler not in organization"
    (:ok (validate-handler-in-organization {:data {:userId ..user-id..} :application {:organization "org-id"}})) => false
    (provided (lupapalvelu.mongo/select-one :users {:_id ..user-id.. :orgAuthz.org-id "authority" :enabled true}) => nil))

  (fact "handler found in organization"
    (validate-handler-in-organization {:data {:userId ..user-id..} :application {:organization "org-id"}}) => nil
    (provided (lupapalvelu.mongo/select-one :users {:_id ..user-id.. :orgAuthz.org-id "authority" :enabled true}) => {:id ..user-id.. :orgAuthz {:org-id ["authority"]}}))

  (fact "no user id given"
    (validate-handler-in-organization {:data {:userId nil} :application {:organization "org-id"}}) => nil))


(facts validate-handler-role
  (fact "no handler roles in org"
    (:ok (validate-handler-role {:data {:roleId ..role-id..} :organization (delay {:handler-roles []})})) => false)

  (fact "no mathing handler roles in org"
    (:ok (validate-handler-role {:data {:roleId ..role-id..} :organization (delay {:handler-roles [{:id ..another-role-id..}]})})) => false)

  (fact "mathing handler role"
    (validate-handler-role {:data {:roleId ..role-id..} :organization (delay {:handler-roles [{:id ..role-id..}]})}) => nil)

  (fact "no role id given"
    (validate-handler-role {:data {:roleId nil} :organization (delay {:handler-roles [{:id ..role-id..}]})}) => nil))

(facts validate-handler-role-not-in-use
  (fact "update existing handler"
    (validate-handler-role-not-in-use {:data {:roleId ..role-id.. :handlerId ..handler-id..} :application {:handlers [{:id ..handler-id.. :roleId ..role-id..}]}})
    => nil)

  (fact "trying create duplicate handler role"
    (:ok (validate-handler-role-not-in-use {:data {:roleId ..role-id.. :handlerId nil} :application {:handlers [{:id ..handler-id.. :roleId ..role-id..}]}}))
    => false)

  (fact "create new handler role"
    (validate-handler-role-not-in-use {:data {:roleId ..role-id.. :handlerId nil} :application {:handlers [{:id ..handler-id.. :roleId ..another-role-id..}]}})
    => nil)

  (fact "no role id given"
    (validate-handler-role-not-in-use {:data {:roleId nil :handlerId nil} :application {:handlers [{:id ..handler-id.. :roleId ..role-id..}]}})
    => nil))

(facts handler-upsert-updates
  (fact "new entry, no existing handlers"
    (handler-upsert-updates {:id ..new-id.. :info ..info..} [] ..created.. ..user..) =>
    {$push {:history {:handler {:id ..new-id.. :info ..info.., :new-entry true}, :ts ..created.., :user {}}},
     $set {:handlers.0 {:id ..new-id.. :info ..info..}, :modified ..created..}})

  (fact "new entry, one existing handler"
    (handler-upsert-updates {:id ..new-id..} [{:id ..id..}] ..created.. ..user..) =>
    {$push {:history {:handler {:id ..new-id.., :new-entry true}, :ts ..created.., :user {}}},
     $set {:handlers.1 {:id ..new-id..}, :modified ..created..}})

  (fact "update existing handler"
    (handler-upsert-updates {:id ..id-1..} [{:id ..id-0..} {:id ..id-1..} {:id ..id-2..}] ..created.. ..user..) =>
    {$push {:history {:handler {:id ..id-1..}, :ts ..created.., :user {}}},
     $set {:handlers.1 {:id ..id-1..}, :modified ..created..}}))

(facts multioperation-attachment-updates
  (fact "multioperation attachment update with op array"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op [] :groupType "operation"}])
    => {"$push" {:attachments.0.op {:id ..op-id.., :name ..op-name..}}}
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "asemapiirros"]]))

  (fact "multioperation attachment update with nil valued op"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op nil :groupType "operation"}])
    => {"$set" {:attachments.0.op [{:id ..op-id.., :name ..op-name..}]}}
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "asemapiirros"]]))

  (fact "multioperation attachment update with legacy op"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op {:id ..old-op-id.. :name ..old-op-name..} :groupType "operation"}])
    => {"$set" {:attachments.0.op [{:id ..old-op-id.. :name ..old-op-name..} {:id ..op-id.., :name ..op-name..}]}}
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "asemapiirros"]]))

  (fact "multioperation attachment update, existing group type does not match"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op [] :groupType nil}])
    => nil
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "asemapiirros"]]))

  (fact "multioperation attachment update without new op provided"
    (multioperation-attachment-updates nil ..org.. [{:type ..att-type.. :op [] :groupType "operation"}])
    => nil)

  (fact "no existing multioperation attachment"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "pohjapiirustus"} :op []}])
    => nil
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"]]))

  (fact "no multioperation attachment required for operation"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op [] :groupType "operation"}])
    => nil
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "pohjapiirustus"]]))

  (fact "multioperation attachment update - multiple attachments"
    (multioperation-attachment-updates {:id ..op-id.. :name ..op-name..} ..org.. [{:type {:type-group "paapiirustus" :type-id "pohjapiirustus"} :op [] :groupType "operation"}
                                                                                  {:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op [] :groupType "operation"}
                                                                                  {:type {:type-group "hakija" :type-id "valtakirja"} :op [] :groupType "parties"}
                                                                                  {:type {:type-group "paapiirustus" :type-id "asemapiirros"} :op [] :groupType "operation"}])
    => {"$push" {:attachments.1.op {:id ..op-id.., :name ..op-name..}
                 :attachments.3.op {:id ..op-id.., :name ..op-name..}}}
    (provided (org/get-organization-attachments-for-operation ..org.. {:id ..op-id.. :name ..op-name..})
              => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"]])))

(facts new-attachment-types-for-operation
  (fact "three attachments"
    (new-attachment-types-for-operation ..org.. ..op.. [])
    => [{:type-group :paapiirustus, :type-id :asemapiirros,   :metadata {:grouping :operation, :multioperation true}}
        {:type-group :paapiirustus, :type-id :pohjapiirustus, :metadata {:grouping :operation}}
        {:type-group :muut,         :type-id :luonnos}]
    (provided (org/get-organization-attachments-for-operation ..org.. ..op..) => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"] ["muut" "luonnos"]]))

  (fact "two attachments - one existing operation specific"
    (new-attachment-types-for-operation ..org.. ..op.. [{:type-group "paapiirustus" :type-id "pohjapiirustus"}])
    => [{:type-group :paapiirustus, :type-id :asemapiirros,   :metadata {:grouping :operation, :multioperation true}}
        {:type-group :paapiirustus, :type-id :pohjapiirustus, :metadata {:grouping :operation}}]
    (provided (org/get-organization-attachments-for-operation ..org.. ..op..) => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"]]))

  (fact "two attachments - one existing multioperation"
    (new-attachment-types-for-operation ..org.. ..op.. [{:type-group "paapiirustus" :type-id "asemapiirros"}])
    => [{:type-group :paapiirustus, :type-id :pohjapiirustus, :metadata {:grouping :operation}}]
    (provided (org/get-organization-attachments-for-operation ..org.. ..op..) => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"]]))

  (fact "three attachments - all exists"
    (new-attachment-types-for-operation ..org.. ..op.. [{:type-group "paapiirustus" :type-id "asemapiirros"} {:type-group "paapiirustus" :type-id "pohjapiirustus"} {:type-group "muut" :type-id "luonnos"}])
    => [{:type-group :paapiirustus, :type-id :pohjapiirustus, :metadata {:grouping :operation}}]
    (provided (org/get-organization-attachments-for-operation ..org.. ..op..) => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"] ["muut" "luonnos"]])))

(facts attachment-grouping-for-type
  (fact "operation grouping - attachment-op-selector->nil"
    (attachment-grouping-for-type {:name ..op-name..} {:metadata {:grouping :operation}}) => {:groupType :operation :operations [{:name ..op-name..}]}
    (provided (op/get-operation-metadata ..op-name.. :attachment-op-selector) => nil))

  (fact "operation grouping - attachment-op-selector->true"
    (attachment-grouping-for-type {:name ..op-name..} {:metadata {:grouping :operation}}) => {:groupType :operation :operations [{:name ..op-name..}]}
    (provided (op/get-operation-metadata ..op-name.. :attachment-op-selector) => true))

  (fact "operation grouping - attachment-op-selector->false"
    (attachment-grouping-for-type {:name ..op-name..} {:metadata {:grouping :operation}}) => nil
    (provided (op/get-operation-metadata ..op-name.. :attachment-op-selector) => false))

  (fact "parties grouping - attachment-op-selector->nil"
    (attachment-grouping-for-type {:name ..op-name..} {:metadata {:grouping :parties}}) => {:groupType :parties}
    (provided (op/get-operation-metadata ..op-name.. :attachment-op-selector) => nil))

  (fact "parties grouping - attachment-op-selector->false"
    (attachment-grouping-for-type {:name ..op-name..} {:metadata {:grouping :parties}}) => nil
    (provided (op/get-operation-metadata ..op-name.. :attachment-op-selector) => false))

  (fact "no metadata"
    (attachment-grouping-for-type {:name ..op-name..} nil) => nil
    (provided (op/get-operation-metadata ..op-name.. :attachment-op-selector) => false)))

(facts make-attachments
  (fact "no overlapping attachments"
    (->> (make-attachments (now) {:id "2d97ac643ef3b9683c89f60b" :name "some-operation"} ..org.. :sent nil)
         (map #(select-keys % [:op :groupType :type])))
       => [{:op [{:id "2d97ac643ef3b9683c89f60b", :name "some-operation"}], :groupType :operation, :type {:type-group :paapiirustus, :type-id :asemapiirros}}
           {:op [{:id "2d97ac643ef3b9683c89f60b", :name "some-operation"}], :groupType :operation, :type {:type-group :paapiirustus, :type-id :pohjapiirustus}}
           {:op nil,                                                         :groupType :parties,   :type {:type-group :hakija, :type-id :valtakirja}}]
    (provided (org/get-organization-attachments-for-operation ..org.. {:id "2d97ac643ef3b9683c89f60b" :name "some-operation"})
              => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"] ["hakija" "valtakirja"]]))

  (fact "existing operation specific attachment - all required attachments are created"
    (->> (make-attachments (now) {:id "2d97ac643ef3b9683c89f60b" :name "some-operation"} ..org.. :sent nil :existing-attachments-types [{:type-group "paapiirustus" :type-id "pohjapiirustus"}])
         (map #(select-keys % [:op :groupType :type])))
       => [{:op [{:id "2d97ac643ef3b9683c89f60b", :name "some-operation"}], :groupType :operation, :type {:type-group :paapiirustus, :type-id :asemapiirros}}
           {:op [{:id "2d97ac643ef3b9683c89f60b", :name "some-operation"}], :groupType :operation, :type {:type-group :paapiirustus, :type-id :pohjapiirustus}}
           {:op nil,                                                        :groupType :parties,   :type {:type-group :hakija, :type-id :valtakirja}}]
    (provided (org/get-organization-attachments-for-operation ..org.. {:id "2d97ac643ef3b9683c89f60b" :name "some-operation"})
              => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"] ["hakija" "valtakirja"]]))

  (fact "existing non-operation-specific attachment - existing type not created"
    (->> (make-attachments (now) {:id "2d97ac643ef3b9683c89f60b" :name "some-operation"} ..org.. :sent nil :existing-attachments-types [{:type-group "paapiirustus" :type-id "asemapiirros"}])
         (map #(select-keys % [:op :groupType :type])))
       => [{:op [{:id "2d97ac643ef3b9683c89f60b", :name "some-operation"}], :groupType :operation, :type {:type-group :paapiirustus, :type-id :pohjapiirustus}}
           {:op nil,                                                        :groupType :parties,   :type {:type-group :hakija, :type-id :valtakirja}}]
    (provided (org/get-organization-attachments-for-operation ..org.. {:id "2d97ac643ef3b9683c89f60b" :name "some-operation"})
              => [["paapiirustus" "asemapiirros"] ["paapiirustus" "pohjapiirustus"] ["hakija" "valtakirja"]]))

)


(facts person-id-masker-for-user
  (fact "handler authority - no masking"
    ((person-id-masker-for-user {:id ..id.. :role :authority} {:handlers [{:userId ..id..}]}) {:schema-info {:name "maksaja"}
                                                                                               :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})

  (fact "non handler authority"
    ((person-id-masker-for-user {:id ..id.. :role :authority :orgAuthz {:org-id #{:authority}}} {:organization "org-id" :handlers [{:userId ..other-id..}]})
     {:schema-info {:name "maksaja"}
      :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "010101-****"}}}}})

  (fact "authority in different organization"
    ((person-id-masker-for-user {:id ..id.. :role :authority :orgAuthz {:another-org-id #{:authority}}} {:organization "org-id" :handlers [{:userId ..other-id..}]})
     {:schema-info {:name "maksaja"}
      :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "******-****"}}}}})

  (fact "non authority user"
    ((person-id-masker-for-user {:id ..id.. :role :authority} {:handlers [{:userId ..other-id..}]}) {:schema-info {:name "maksaja"}
                                                                                                     :data {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})
    => {:schema-info {:name "maksaja"}
        :data {:henkilo {:henkilotiedot {:hetu {:value "******-****"}}}}}))

(facts "Validate digging permits linked agreement"
  (fact "Linked agreement have to be post verdict state"
    (validate-link-agreements-state {:state "submitted"}) => {:ok false, :text "error.link-permit-app-not-in-post-verdict-state"}
    (validate-link-agreements-state {:state "agreementPrepared"}) => nil
    (validate-link-agreements-state {:state "finished"}) => nil
    (validate-link-agreements-state {:state "verdictGiven"}) => nil)

  (fact "Linked agreement have to be signed"
    (validate-link-agreements-signature {:verdicts []}) => {:ok false, :text "error.link-permit-app-not-signed"}
    (validate-link-agreements-signature {:verdicts [{:signatures [:created "1490772437443" :user []]}]}) => nil)

  (fact "Is enought that only one agreements have signature"
    (validate-link-agreements-signature {:verdicts [{:id "123456789" :signatures [:created "1490772437443" :user []]},
                                                    {:id "987654321"}]}) => nil))

(facts "Making new application"
  (let [user {:id        ..user-id..
              :firstName ..first-name..
              :lastName  ..last-name..
              :role      ..user-role..}
        created 12345]
    (fact application-auth
      (application-auth user "kerrostalo-rivitalo")
      => [(assoc (usr/summary user) :role :owner :type :owner :unsubscribed false)]

      (application-auth user "aiemmalla-luvalla-hakeminen")
      => [(assoc (usr/summary user) :role :owner :type :owner :unsubscribed true)]

      (application-auth ..company-user.. "kerrostalo-rivitalo")
      => [(assoc (usr/summary ..company-user..) :role :owner :type :owner :unsubscribed false) ..company-auth..]

      (against-background
       ..company.. =contains=> {:id ..company-id..
                                :name ..company-name..
                                :y ..company-y..}
       (com/company->auth ..company..) => ..company-auth..
       ..company-user.. =contains=> {:id ..company-user-id..
                                     :firstName ..company-first-name..
                                     :lastName ..company-last-name..
                                     :role ..company-user-role..
                                     :company {:id ..company-id..
                                               :name ..company-name..
                                               :y ..company-y..}}
       (com/find-company-by-id ..company-id..) => ..company..))

    (fact application-comments
      (application-comments user ["message"] true created)
      => [{:created 12345, :roles [:applicant :authority :oirAuthority], :target {:type "application"}, :text "message", :to nil, :type ..user-role.., :user user}]

      (application-comments user ["message1" "message2"] false created)
      => [{:created 12345, :roles [:applicant :authority], :target {:type "application"}, :text "message1", :to nil, :type ..user-role.., :user user}
          {:created 12345, :roles [:applicant :authority], :target {:type "application"}, :text "message2", :to nil, :type ..user-role.., :user user}])

    (fact application-state
      (application-state ..any-user.. ..organization-id.. true) => :info

      (application-state ..user.. ..organization-id.. false) => :open
      (provided (usr/user-is-authority-in-organization? ..user.. ..organization-id..) => true)

      (application-state ..user.. ..organization-id.. false) => :draft
      (provided (usr/user-is-authority-in-organization? ..user.. ..organization-id..) => false
                (usr/rest-user? ..user..) => false))

    (fact permit-type-and-operation-map
      (permit-type-and-operation-map "poikkeamis" created)
      => {:permitSubtype :poikkeamislupa, :permitType "P", :primaryOperation {:created created, :description nil, :id "mongo-id", :name "poikkeamis"}}

      (permit-type-and-operation-map "kerrostalo-rivitalo" created)
      => {:permitSubtype nil, :permitType "R", :primaryOperation {:created created, :description nil, :id "mongo-id", :name "kerrostalo-rivitalo"}})

    (fact application-timestamp-map
      (application-timestamp-map {:state :open, :created created})  => {:opened created, :modified created}
      (application-timestamp-map {:state :info, :created created})  => {:opened created, :modified created}
      (application-timestamp-map {:state :draft, :created created}) => {:opened nil,     :modified created})

    (fact application-history-map
     ; TODO
          )

    (fact application-documents-map
      (let [application {:created created
                         :primaryOperation (make-op "kerrostalo-rivitalo" created)
                         :auth (application-auth user "kerrostalo-rivitalo")
                         :schema-version 1}
            documents-map (application-documents-map application {} {})]
        (keys documents-map) => [:documents]
        (:documents documents-map) => seq?
        (map :created (:documents documents-map)) => (has every? #(= created %))
        (map :id (:documents documents-map)) => (has every? #(= "mongo-id" %))

        (:documents (application-documents-map (assoc application :infoRequest true) {} {})) => []))

    (fact application-attachments-map
      (let [application {:created created
                         :primaryOperation (make-op "kerrostalo-rivitalo" created)
                         :state :open
                         :schema-version 1}
            attachments-map (application-attachments-map application {})]
        (keys attachments-map) => [:attachments]
        (:attachments attachments-map) => seq?
        (map :created (:attachments attachments-map)) => (has every? #(= created %))
        (map :id (:attachments attachments-map)) => (has every? #(= "mongo-id" %))
        (:attachments (application-attachments-map (assoc application :infoRequest true) {})) => [])))
  (against-background (mongo/create-id) => "mongo-id"))
