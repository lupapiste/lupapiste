(ns lupapalvelu.application-test
  (:require [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application :refer :all]
            [lupapalvelu.application-api]
            [lupapalvelu.building-attributes :as ba]
            [lupapalvelu.company :as com]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$set $push]]
            [sade.core :refer [now]]
            [swiss.arrows :refer :all]))

(fact "update-document"
  (update-application {:application ..application.. :data {:id ..id..}} {$set {:jee "jee"}}) => nil
  (provided
    ..application.. =contains=> {:id ..id..}
    (mongo/update-by-query :applications {:_id ..id..} {$set {:jee "jee"}}) => 1))

(testable-privates lupapalvelu.application-api add-operation-allowed? validate-handler-role validate-handler-role-not-in-use validate-handler-id-in-application validate-handler-in-organization)

(testable-privates lupapalvelu.application count-required-link-permits
                   attachment-grouping-for-type person-id-masker-for-user enrich-tos-function-name
                   enrich-single-doc-disabled-flag remove-draft-foreman-links)

(testable-privates lupapalvelu.ya validate-link-agreements-signature validate-link-agreements-state)


(facts "mark-indicators-seen-updates"
  (let [timestamp 123
        expected-seen-bys (-<>> ["comments" "statements" "verdicts"
                                 "authority-notices" "info-links"]
                               (map (partial format "_%s-seen-by.pena"))
                               (zipmap <> (repeat timestamp)))
        expected-attachment (assoc expected-seen-bys :_attachment_indicator_reset timestamp)
        expected-docs (assoc expected-attachment "documents.0.meta._indicator_reset.timestamp" timestamp)]
    (mark-indicators-seen-updates {:application {} :user {:id "pena"} :created timestamp :permissions #{}}) => expected-seen-bys
    (mark-indicators-seen-updates {:application {:documents []} :user {:id "pena", :role "authority"} :created timestamp :permissions #{:document/approve :attachment/approve}}) => expected-attachment
    (mark-indicators-seen-updates {:application {:documents [{}]} :user {:id "pena", :role "authority"} :created timestamp :permissions #{:document/approve :attachment/approve}}) => expected-docs))

(defn find-by-schema? [docs schema-name]
  (domain/get-document-by-name {:documents docs} schema-name))

(defn has-schema? [schema] (fn [docs] (find-by-schema? docs schema)))

(facts count-required-link-permits
  (fact "Muutoslupa"
    (count-required-link-permits {:permitSubtype "muutoslupa"}) => 1)
  (fact "Aloitusilmoitus"
    (count-required-link-permits {:primaryOperation {:name "aloitusoikeus"}}) => 1)
  (fact "Poikkeamis"
    (count-required-link-permits {:primaryOperation {:name "poikkeamis"}}) => 0)
  (fact "ya-jatkoaika, primary"
    (count-required-link-permits {:primaryOperation {:name "ya-jatkoaika"}}) => 1)
  (fact "ya-jatkoaika, secondary"
    (count-required-link-permits {:secondaryOperations [{:name "ya-jatkoaika"}]}) => 1)
  (fact "ya-jatkoaika x 2"
    (count-required-link-permits {:secondaryOperations [{:name "ya-jatkoaika"} {:name "ya-jatkoaika"}]}) => 2)
  (fact "muutoslupa+ya-jatkoaika"
    (count-required-link-permits {:permitSubtype "muutoslupa" :secondaryOperations [{:name "ya-jatkoaika"}]}) => 2)
  (fact "muutoslupa+aloitusilmoitus+ya-jatkoaika"
    (count-required-link-permits {:permitSubtype "muutoslupa" :primaryOperation {:name "aloitusoikeus"} :secondaryOperations [{:name "ya-jatkoaika"}]}) => 3))

(facts "Add operation allowed"
       (let [not-allowed-for #{;; R-operations, adding not allowed
                               :raktyo-aloit-loppuunsaat :jatkoaika :aloitusoikeus :suunnittelijan-nimeaminen
                               :tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :aiemmalla-luvalla-hakeminen
                               ;; KT-operations, adding not allowed
                               :rajankaynti
                               ;; YL-operations, adding not allowed
                               :pima
                               ;; YM-operations, adding not allowed
                               :muistomerkin-rauhoittaminen :jatteen-keraystoiminta :lannan-varastointi :lannan-varastointi-v2
                               :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan
                               :koeluontoinen-toiminta
                               :konversio :maa-ainesten-kotitarveotto :ilmoitus-poikkeuksellisesta-tilanteesta
                               :ymparistoluvan-selventaminen
                               :talousjatevesien-kasittelysta-poikkeaminen
                               :yhteiskasittely-maa-aines-ja-ymparistoluvalle
                               }
        error {:ok false :text "error.add-operation-not-allowed"}]

    (doseq [operation op/operations]
      (let [[op {permit-type :permit-type}] operation
            application {:primaryOperation {:name (name op)} :permitSubtype nil}
            operation-allowed (add-operation-allowed? {:application application})]
        (fact {:midje/description (name op)}
          (if (or (not (contains? #{"R" "KT" "YM"} permit-type)) (not-allowed-for op))
            (fact "Add operation not allowed" operation-allowed => error)
            (fact "Add operation allowed" operation-allowed => nil?)))))

    (fact "Add operation not allowed for poikkeamis"
      (add-operation-allowed? {:application {:primaryOperation {:name "poikkeamis"} :permitSubtype nil}}) => error)

    (fact "Add operation not allowed for :muutoslupa"
      (add-operation-allowed? {:application {:primaryOperation {:name "kerrostalo-rivitalo"} :permitSubtype :muutoslupa}}) => error)))

(fact "validate-has-subtypes"
  (validate-has-subtypes {:application {:permitType "P"}}) => nil
  (validate-has-subtypes {:application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}}) => nil
  (validate-has-subtypes {:application {:permitType "R"}}) => {:ok false :text "error.permit-has-no-subtypes"}
  (validate-has-subtypes nil) => {:ok false :text "error.permit-has-no-subtypes"})

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
     $set {:handlers.0 {:id ..new-id.. :info ..info..}
           :modified   ..created..}})

  (fact "new entry, one existing handler"
    (handler-upsert-updates {:id ..new-id..} [{:id ..id..}] ..created.. ..user..) =>
    {$push {:history {:handler {:id ..new-id.., :new-entry true}, :ts ..created.., :user {}}},
     $set {:handlers.1 {:id ..new-id..}
           :modified   ..created..}})

  (fact "update existing handler"
    (handler-upsert-updates {:id ..id-1..} [{:id ..id-0..} {:id ..id-1..} {:id ..id-2..}] ..created.. ..user..) =>
    {$push {:history {:handler {:id ..id-1..}, :ts ..created.., :user {}}},
     $set {:handlers.1 {:id ..id-1..}
           :modified   ..created..}}))

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

(facts "Validate digging permits linked agreement"
  (fact "Linked agreement have to be post verdict state"
    (validate-link-agreements-state {:state "submitted"}) => {:ok false, :text "error.link-permit-app-not-in-post-verdict-state"}
    (validate-link-agreements-state {:state "agreementPrepared"}) => nil
    (validate-link-agreements-state {:state "finished"}) => nil
    (validate-link-agreements-state {:state "verdictGiven"}) => nil)

  (fact "Linked agreement have to be signed"
    (fact "without subtype success"
      (validate-link-agreements-signature {:verdicts []}) => nil
      (validate-link-agreements-signature {:verdicts [{:signatures [:created "1490772437443" :user []]}]}) => nil)
    (fact "with subtype actual check is done"
      (validate-link-agreements-signature {:verdicts [] :permitSubtype "sijoitussopimus"}) => {:ok false, :text "error.link-permit-app-not-signed"}
      (validate-link-agreements-signature {:verdicts [{:signatures [:created "1490772437443" :user []]}]
                                           :permitSubtype "sijoitussopimus"}) => nil))

  (fact "Is enought that only one agreements have signature"
    (validate-link-agreements-signature {:verdicts [{:id "123456789" :signatures [:created "1490772437443" :user []]},
                                                    {:id "987654321"}]
                                         :permitSubtype "sijoitussopimus"}) => nil))

(facts "Making new application"
  (let [user {:id        ..user-id..
              :firstName ..first-name..
              :lastName  ..last-name..
              :role      ..user-role..}
        created 12345]
    (fact application-auth
      (application-auth user "kerrostalo-rivitalo")
      => [(assoc (usr/summary user) :role :writer :unsubscribed false)]

      (application-auth user "aiemmalla-luvalla-hakeminen")
      => [(assoc (usr/summary user) :role :writer :unsubscribed true)]

      (application-auth ..company-user.. "kerrostalo-rivitalo")
      => [{:id ..company-id..
           :name ..company-name..
           :y ..company-y..
           :unsubscribed false}]

      (against-background
       ..company.. =contains=> {:id ..company-id..
                                :name ..company-name..
                                :y ..company-y..}
       (com/company->auth ..company..) => {:id ..company-id..
                                           :name ..company-name..
                                           :y ..company-y..}
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
      (application-state ..any-user.. ..organization-id.. true "kerrostalo-rivitalo") => :info

      (application-state ..any-user.. ..organization-id.. false "archiving-project") => :open

      (application-state ..user.. ..organization-id.. false "kerrostalo-rivitalo") => :open
      (provided
        (usr/user-has-role-in-organization? ..user.. ..organization-id..
                                            roles/reader-org-authz-roles) => true)

      (application-state ..user.. ..organization-id.. false "kerrostalo-rivitalo") => :draft
      (provided
        (usr/user-has-role-in-organization? ..user.. ..organization-id..
                                            roles/reader-org-authz-roles) => false
        (usr/rest-user? ..user..) => false)
      (application-state ..user.. ..organization-id.. false "aiemmalla-luvalla-hakeminen") => :verdictGiven)

    (fact permit-type-and-operation-map
      (permit-type-and-operation-map "poikkeamis" "753-R" created)
      => {:permitSubtype :poikkeamislupa, :permitType "P", :primaryOperation {:created created, :description nil, :id "mongo-id", :name "poikkeamis"}}

      (permit-type-and-operation-map "kerrostalo-rivitalo" "753-R" created)
      => {:permitSubtype nil, :permitType "R", :primaryOperation {:created created, :description nil, :id "mongo-id", :name "kerrostalo-rivitalo"}}

      (permit-type-and-operation-map "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen" "091-YA" created)
      => {:permitSubtype :sijoitussopimus, :permitType "YA", :primaryOperation {:created created, :description nil, :id "mongo-id", :name "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"}}

      (permit-type-and-operation-map "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen" "753-R" created)
      => {:permitSubtype :sijoituslupa, :permitType "YA", :primaryOperation {:created created, :description nil, :id "mongo-id", :name "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"}})

    (fact application-timestamp-map
      (application-timestamp-map {:state :open, :created created})  => {:opened created, :modified created}
      (application-timestamp-map {:state :info, :created created})  => {:opened created, :modified created}
      (application-timestamp-map {:state :draft, :created created}) => {:opened nil,     :modified created})

    (fact application-history-map
      (let [application {:created created
                         :organization "753-R"
                         :state :draft
                         :history [:old :history :is :irrelevant]}
            user {:firstName "Creating"
                  :lastName "User"
                  :id "creating-user"
                  :role "applicant"}
            history-map (application-history-map application user)]
        history-map => {:history [{:state (:state application)
                                   :ts (:created application)
                                   :user (usr/summary user)}]}))

    (fact application-documents-map
      (let [application {:created created
                         :primaryOperation (make-op "kerrostalo-rivitalo" created)
                         :auth (application-auth user "kerrostalo-rivitalo")
                         :schema-version 1}
            documents-map (application-documents-map application {} {} {})]
        (keys documents-map) => [:documents]
        (:documents documents-map) => seq?
        (map :created (:documents documents-map)) => (has every? #(= created %))
        (map :id (:documents documents-map)) => (has every? #(= "mongo-id" %))

        (:documents (application-documents-map (assoc application :infoRequest true) {} {} {})) => []))

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

(facts enrich-tos-function-name
  (fact "no tos-function"
    (enrich-tos-function-name {:id ..app-id.. :organization "753-R"}) => {:id ..app-id.. :organization "753-R" :tosFunctionName nil}
    (provided (lupapalvelu.tiedonohjaus/available-tos-functions anything) => irrelevant :times 0))

  (fact "one matching tos-function"
    (-> {:id ..app-id.. :organization "753-R" :tosFunction "12 13 14 15"}
        enrich-tos-function-name
        :tosFunctionName) => ..tos-name..
    (provided (lupapalvelu.tiedonohjaus/available-tos-functions "753-R") => [{:code "12 13 14 15" :name ..tos-name..}]))

  (fact "no matching tos-function"
    (-> {:id ..app-id.. :organization "753-R" :tosFunction "12 13 14 15"}
        enrich-tos-function-name
        :tosFunctionName) => nil
    (provided (lupapalvelu.tiedonohjaus/available-tos-functions "753-R") => [{:code "00 00 00 00" :name ..tos-name..}]))

  (fact "multiple tos-functions for organization"
    (-> {:id ..app-id.. :organization "753-R" :tosFunction "12 13 14 15"}
        enrich-tos-function-name
        :tosFunctionName) => ..tos-name-2..
    (provided (lupapalvelu.tiedonohjaus/available-tos-functions "753-R") => [{:code "12 13 14 14" :name ..tos-name-1..}
                                                                             {:code "12 13 14 15" :name ..tos-name-2..}
                                                                             {:code "12 13 14 16" :name ..tos-name-3..}
                                                                             {:code "12 13 14 17" :name ..tos-name-4..}
                                                                             {:code "12 13 14 18" :name ..tos-name-5..}
                                                                             {:code "12 13 14 19" :name ..tos-name-6..}])))

(facts "whitelist-action"
  (let [doc {:schema-info {:name "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"}
             :data {:mitat {:tilavuus {:value "foo"}}}}]
    (fact "authority is passed (no restrictions added)"
      (enrich-single-doc-disabled-flag {:role "authority"} {:permitType "R"} doc) => doc)
    (fact "applicant gets flagged out with whitelist-acion :disabled"
      (let [result (enrich-single-doc-disabled-flag {:role "applicant"} {:permitType "R"} doc)]
        result =not=> doc
        (vals (get-in result [:data :mitat])) => (has every? #(= (dissoc % :value) {:whitelist-action :disabled}))))))

(facts canceled-app-context
  (fact "app is canceled by the current user"
    (-> (canceled-app-context {:user {:id "1"}
                               :application {:state "canceled" :history [{:user {:id "1"} :state "open"}
                                                                         {:user {:id "1"} :state "canceled"}]}})
        :permissions)
    => #{:application/undo-cancelation})

  (fact "app is canceled by some other user"
    (-> (canceled-app-context {:user {:id "1"}
                               :application {:state "canceled" :history [{:user {:id "1"} :state "open"}
                                                                         {:user {:id "2"} :state "canceled"}]}})
        :permissions)
    => empty?)

  (fact "last state not canceled"
    (-> (canceled-app-context {:user {:id "1"}
                               :application {:state "canceled" :history [{:user {:id "1"} :state "open"}
                                                                         {:user {:id "1"} :state "submitted"}]}})
        :permissions)
    => empty?)

  (fact "no application in command"
    (-> (canceled-app-context {:user {:id "1"}})
        :permissions)
    => empty?))

(fact "jatkoaika-application?"
  (jatkoaika-application? {:primaryOperation {:name "raktyo-aloit-loppuunsaat"}}) => true
  (jatkoaika-application? {:primaryOperation {:name "jatkoaika"}}) => true
  (jatkoaika-application? {:primaryOperation {:name "ya-jatkoaika"}}) => true
  (jatkoaika-application? {:primaryOperation {:name "rivitalo-kerrostalo"}}) => false
  (jatkoaika-application? {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}) => false
  (jatkoaika-application? {:primaryOperation {:name ""}}) => false
  (jatkoaika-application? nil) => false)

;; LPK-3856
(fact "remove draft foreman applinks"
  (fact "normal case"
    (remove-draft-foreman-links
      {:id 1234 :username "pena"}
      {:appsLinkingToUs [{:operation "tyonjohtajan-nimeaminen-v2" :state "draft"}]})
    => {:appsLinkingToUs [{:operation "tyonjohtajan-nimeaminen-v2" :state "draft"}]}
    (provided
      (lupapalvelu.authorization/application-authority? anything anything) => false
      (lupapalvelu.authorization/has-auth-role? anything 1234 :writer) => true))
  (fact "remove draft from authorities"
    (remove-draft-foreman-links
      {:id 2345 :username "sonja"}
      {:appsLinkingToUs [{:operation "tyonjohtajan-nimeaminen-v2" :state "draft"}
                         {:operation "tyonjohtajan-nimeaminen-v2" :state "approved"}]})
    => {:appsLinkingToUs [{:operation "tyonjohtajan-nimeaminen-v2" :state "approved"}]}
    (provided
      (lupapalvelu.authorization/application-authority? anything anything) => true
      (lupapalvelu.authorization/has-auth-role? anything 2345 :writer) => false))
  (fact "do not remove if user is writer"
    (remove-draft-foreman-links
      {:id 2345 :username "sonja"}
      {:appsLinkingToUs [{:operation "tyonjohtajan-nimeaminen-v2" :state "draft"}
                         {:operation "tyonjohtajan-nimeaminen-v2" :state "approved"}]})
    => {:appsLinkingToUs [{:operation "tyonjohtajan-nimeaminen-v2" :state "draft"}
                          {:operation "tyonjohtajan-nimeaminen-v2" :state "approved"}]}
    (provided
      (lupapalvelu.authorization/application-authority? anything anything) => true
      (lupapalvelu.authorization/has-auth-role? anything 2345 :writer) => true)))

(facts "with-municipality-permit-ids"
  (with-municipality-permit-ids {:verdicts [{:kuntalupatunnus "AAA" :draft true}
                                            {:kuntalupatunnus "BBB"}]})
  => {:verdicts              [{:kuntalupatunnus "AAA" :draft true}
                              {:kuntalupatunnus "BBB"}]
      :municipalityPermitIds ["BBB"]}

  (fact "ARK: include drafts"
    (with-municipality-permit-ids {:permitType "ARK"
                                   :verdicts   [{:kuntalupatunnus "AAA" :draft true}
                                                {:kuntalupatunnus "BBB"}]})
    => {:permitType            "ARK"
        :verdicts              [{:kuntalupatunnus "AAA" :draft true}
                                {:kuntalupatunnus "BBB"}]
        :municipalityPermitIds ["AAA" "BBB"]})

  (fact "Converted: include drafts"
    (with-municipality-permit-ids {:facta-imported true
                                   :verdicts       [{:kuntalupatunnus "AAA" :draft true}
                                                    {:kuntalupatunnus "BBB"}]})
    => {:facta-imported        true
        :verdicts              [{:kuntalupatunnus "AAA" :draft true}
                                {:kuntalupatunnus "BBB"}]
        :municipalityPermitIds ["AAA" "BBB"]}))

(def dummy-party
  {:created 1546007280653
   :data {:henkilotiedot {:etunimi {:value "Aulikki"}
                          :hetu {:value nil}
                          :sukunimi {:value "Ananas"}}
          :osoite {:katu {:value "Metallimiehenraitti 59"}
                   :maa {:value "FIN"}
                   :postinumero {:value "01650"}
                   :postitoimipaikannimi {:value "VANTAA"}}
          :patevyys {:fise {:value ""}
                     :fiseKelpoisuus {:value nil}
                     :kokemus {:value ""}
                     :koulutus {:value ""}
                     :koulutusvalinta {:value "rakennusinsin\u00f6\u00f6ri"}
                     :patevyys {:value ""}
                     :patevyysluokka {:value nil}
                     :valmistumisvuosi {:value "1984"}}
          :suunnittelutehtavanVaativuusluokka {:value nil}
          :userId {:value nil}
          :yhteystiedot {:email {:value "aulikki@pineapplemail.com"}
                         :puhelin {:value "040-40129944"}}
          :yritys {:liikeJaYhteisoTunnus {:value ""} :yritysnimi {:value ""}}}
   :id "5c2632f03b4cd66463121e79"})

(fact "anonymization works without breaking documents"
      (anonymize-parties dummy-party) => {:created 1546007280653
                                          :data {:henkilotiedot {:etunimi {:value "Pena"}
                                                                 :hetu {:value nil}
                                                                 :sukunimi {:value "Panaani"}}
                                                 :osoite {:katu {:value "Paapankuja 1 A 1"}
                                                          :maa {:value "FIN"}
                                                          :postinumero {:value "01650"}
                                                          :postitoimipaikannimi {:value "VANTAA"}}
                                                 :patevyys {:fise {:value ""}
                                                            :fiseKelpoisuus {:value nil}
                                                            :kokemus {:value ""}
                                                            :koulutus {:value ""}
                                                            :koulutusvalinta {:value "rakennusinsin\u00f6\u00f6ri"}
                                                            :patevyys {:value ""}
                                                            :patevyysluokka {:value nil}
                                                            :valmistumisvuosi {:value "1984"}}
                                                 :suunnittelutehtavanVaativuusluokka {:value nil}
                                                 :userId {:value nil}
                                                 :yhteystiedot {:email {:value "pena@example.com"}
                                                                :puhelin {:value "012-3456789"}}
                                                 :yritys {:liikeJaYhteisoTunnus {:value ""} :yritysnimi {:value ""}}}
                                          :id "5c2632f03b4cd66463121e79"})

(fact "enrich-buildings"
  (fact "does nothing when application does not contain buildings"
    (with-redefs [ba/fetch-buildings (fn [org & other-conditions] [])]
      (let [application {:organization "753-R" :buildings []}]
        (enrich-buildings application) => application)))

  (fact "adds empty metadata when application contains buildings but they don't have any extra attributes defined"
    (with-redefs [ba/fetch-buildings (fn [org & other-conditions] [])]
      (let [application {:organization "753-R"
                         :buildings [{:nationalId "182736459F"}
                                     {:nationalId "100012345N"}]}]
        (enrich-buildings application) => {:organization "753-R"
                                           :buildings [{:nationalId "182736459F" :metadata {}}
                                                       {:nationalId "100012345N" :metadata {}}]})))

  (fact "adds extra metadata when application contains buildings which has extra metadata"
    (with-redefs [ba/fetch-buildings (fn [org & other-conditions]
                                       [{:publicity "salainen" :vtjprt "182736459F" :visibility "viranomainen" :id "1" :modified 123}
                                        {:publicity "osittain-salassapidettava" :vtjprt "100012345N" :visibility "asiakas-ja-viranomainen" :id "2" :modified 123}
                                        {:publicity "julkinen" :vtjprt "1234567892" :visibility "julkinen" :myyntipalvelussa true :id "2" :modified 123}
                                        {:publicity "julkinen" :vtjprt "ei-kuulu-asiaan" :visibility "julkinen" :id "00" :modified 123}])]
      (let [application {:organization "753-R"
                         :buildings [{:nationalId "182736459F"}
                                     {:nationalId "100012345N"}
                                     {:nationalId "1234567892"}]}]
        (enrich-buildings application) => {:organization "753-R"
                                           :buildings [{:nationalId "182736459F"
                                                        :metadata {:julkisuusluokka "salainen"
                                                                   :nakyvyys "viranomainen"
                                                                   :myyntipalvelu false}}
                                                       {:nationalId "100012345N"
                                                        :metadata {:julkisuusluokka "osittain-salassapidettava"
                                                                   :nakyvyys "asiakas-ja-viranomainen"
                                                                   :myyntipalvelu false}}
                                                       {:nationalId "1234567892"
                                                        :metadata {:julkisuusluokka "julkinen"
                                                                   :nakyvyys "julkinen"
                                                                   :myyntipalvelu true}}]}))))
