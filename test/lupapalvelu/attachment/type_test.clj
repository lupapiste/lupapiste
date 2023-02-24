(ns lupapalvelu.attachment.type-test
  (:refer-clojure :exclude [contains?])
  (:require [clojure.set :as set]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.type-settings-schemas :as att-schemas]
            [lupapalvelu.attachment.type :refer :all]
            [lupapalvelu.attachment.metadata :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test :refer [is]]
            [schema.core :as sc]
            [sade.schema-generators :as ssg]
            [lupapalvelu.operations :as operations]))

(testable-privates lupapalvelu.attachment.type
                   attachment-types-by-operation
                   foreman-application-types
                   type-grouping
                   default-grouping)

(fact (parse-attachment-type "foo.bar") => {:type-group :foo, :type-id :bar})
(fact (parse-attachment-type "foo.") => nil)
(fact (parse-attachment-type "") => nil)
(fact (parse-attachment-type nil) => nil)

(facts "Facts about allowed-attachment-types-contain?"
  (let [allowed-types [["a" ["1" "2"]] [:b [:3 :4]]]]
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :a :type-id :1}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :a :type-id :2}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :3}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :4}) => truthy)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :b :type-id :5}) => falsey)
    (fact (allowed-attachment-types-contain? allowed-types {:type-group :c :type-id :1}) => falsey)))

(fact "attachment type IDs are unique"
  (let [known-duplicates (set (conj osapuolet
                                    :ote_asunto-osakeyhtion_kokouksen_poytakirjasta
                                    :ote_alueen_peruskartasta
                                    :ote_asemakaavasta
                                    :ote_kauppa_ja_yhdistysrekisterista
                                    :asemapiirros
                                    :ote_yleiskaavasta
                                    :jaljennos_perunkirjasta
                                    :valokuva :rasitesopimus
                                    :valtakirja
                                    :muu
                                    :paatos
                                    :paatosote))
        all-except-commons (remove known-duplicates all-attachment-type-ids)
        all-unique (set all-except-commons)]

    (count all-except-commons) => (count all-unique)))

(fact attachment-types-by-permit-type
      (let [testcases
            [["Attachment types for permit-type :R"
              :R
              [:johtokartta :valtakirja :cv :patevyystodistus :suunnittelijan_tiedot :tutkintotodistus :aitapiirustus
               :asemapiirros :julkisivupiirustus :leikkauspiirustus :pohjapiirustus :todistus_hallintaoikeudesta
               :ote_yhtiokokouksen_poytakirjasta :kiinteiston_lohkominen :sopimusjaljennos :karttaaineisto
               :ote_alueen_peruskartasta :perustamistapalausunto :rakennusoikeuslaskelma
               :energiataloudellinen_selvitys :energiatodistus :haittaaineselvitys]]
             ["Attachment types required by :ylijaamamaan-hyodyntaminen announcement in permit-type :YI"
              :YI
              [:valtakirja :karttaote :rakennekuva :asemapiirros]]
             ["Attachment types required by :jatteiden-hyodyntaminen-maarakentamisessa in permit-type :YI"
              :YI
              [:valtakirja :karttaote :rakennekuva :asemapiirros]]
             ["Attachment types required by :lannan-levittamisesta-poikkeustilanteessa in permit-type :YI"
              :YI
              [:karttaote :muu]]
             ["Attachment types required by :kirjallinen-vireillepano in permit-type :YI"
               :YI
               [:valokuva :asemapiirros :muu]]]]
        (doseq [[test-case permit-type expected-type-ids] testcases]
          (let [actual (set (map :type-id (attachment-types-by-permit-type permit-type)))
                expected (set expected-type-ids)
                missing-types (set/difference expected actual)]
            (fact {:midje/description test-case} missing-types => #{})))))

(facts "Mapping functions for attachment-type listing format conversions

        The same data in different places is presented in 5 different ways. These functions (see tests below)
        handles mapping between the different formats.

        As background:
        1) In lupapiste/common attachments are in plain vector where odd items are group and even are attachment
           collections.
        2) Allowed-attachment are stored in db as maps where key is group-id and value is collections of
           attachments. (For default attachments format #5 is used.)
        3) In some interfaces (and in frontend code) format is vector of maps where type-id and type-group are keys of
           a map. It is slighly different from the other format because the data can be enriched with metadata.
        4) In some interfaces (and in frontend code) format is vector of vector tuples of [group-id, attachment-id vector] is
           used instead of (#3 and #5).
        5) In some interfaces (and in frontend code) format is vector of vector tuples of [group-id, attachment-id] is used
           instead of (#3 and #4)."
  (fact plain-kv-pair-vector->tuple-vector
    (plain-kv-pair-vector->tuple-vector [:foo [:1 :2] :bar [:3 :4]]) => [[:foo [:1 :2]] [:bar [:3 :4]]])

  (fact plain-kv-pair-vector->attachment-type-array
    (plain-kv-pair-vector->attachment-type-array [:foo [:bar] :bis [:buz]]) =>
    [{:type-id    :bar
      :type-group :foo}
     {:type-id    :buz
      :type-group :bis}])

  (fact plain-kv-pair-vector->map
    (plain-kv-pair-vector->map [:foo [:1 :2] :bar [:3 :4]]) => {:foo [:1 :2] :bar [:3 :4]})

  (fact attachment-map->tuple-vector
    (attachment-map->tuple-vector {:foo [:bar] :biz [:buz]}) => [[:foo [:bar]] [:biz [:buz]]])

  (fact attachment-type-array->map
    (attachment-type-array->map [{:type-id :f1 :type-group :g1}])
    => {:g1 [:f1]}
    (attachment-type-array->map [{:type-id :f1 :type-group :g1} {:type-id :f2 :type-group :g1}])
    => {:g1 [:f1 :f2]}
    (attachment-type-array->map [{:type-id :f1 :type-group :g1}
                                 {:type-id :f2 :type-group :g1}
                                 {:type-id :f3 :type-group :g2}])
    => {:g1 [:f1 :f2] :g2 [:f3]})
  (facts attachment-tuple-vector->map
    (fact "simple case"
      (attachment-tuple-vector->map [[:foo [:bar]] [:biz [:buz]]]) => {:foo [:bar] :biz [:buz]})
    (fact "if same group-id is listed multiple times the values are merged and duplicates are removed"
      (attachment-tuple-vector->map [[:foo [:bar :biz]] [:foo [:bar :buz]]]) => {:foo [:bar :biz :buz]})
    (fact "tuple [:type-group :type-id] works alike [:type-group [:type-id]]"
      (attachment-tuple-vector->map [[:foo :bar] [:foo :biz]]) => {:foo [:bar :biz]})
    (fact "converts strings to keywords"
        (attachment-tuple-vector->map [["foo" "bar"] ["foo" ["biz" "bus"]]]) => {:foo [:bar :biz :bus]})))

(fact "get-organizations-attachment-types-for-operation-with-hardcoded-override should return list of
          attachments if operation is has at least one attachment with :for-operation containg operation id,
          and empty collection otherwise"
  (count (get-organizations-attachment-types-for-operations-with-hardcoded-override "tyonjohtajan-nimeaminen-v2"))
  => 7
  (count (get-organizations-attachment-types-for-operations-with-hardcoded-override "leirintaalueilmoitus"))
  => 0)

(facts get-organizations-attachment-types-for-operation
  (fact "when operation does not exists in the expanded organization-attachment-settings it should return consistently an empty collection"
    (get-organizations-attachment-types-for-operation {:operation-nodes {}} "non-existing-operation") => []
    (get-organizations-attachment-types-for-operation {:operation-nodes {}} "non-existing-operation" :operation-baseline-only? true) => [])

  (fact "foreman application has restricted set of attachment types. It should return tha hardcoded list also
         when operation-baseline-only? is used."
    (let [minimal-settings-map {:operation-nodes {:tyonjohtajan-nimeaminen-v2 {:permit-type :R}}}]
      (count (get-organizations-attachment-types-for-operation minimal-settings-map "tyonjohtajan-nimeaminen-v2"))
      => 7

      (count (get-organizations-attachment-types-for-operation minimal-settings-map "tyonjohtajan-nimeaminen-v2"
                                                               :operation-baseline-only? true))
      => 7))

  (fact "when operation-baseline-only? is true it should return permit type based listing any operation"
    (doseq [permit-type [:R :YI :YM :YA :P :A :VVVL :MAL :MM :KT :ARK]
            :let [case-name (str "permit-type: " permit-type)
                  settings {:operation-nodes {:an-operation {:permit-type permit-type}}}
                  expected-types (permit-type attachment-types-by-permit-type-as-attachment-map)
                  actual (get-organizations-attachment-types-for-operation
                           settings "an-operation" :operation-baseline-only? true)
                  actual-as-map (attachment-type-array->map actual)
                  ]]
      (fact {:midje/description case-name} actual-as-map => expected-types))))

(fact "All attachment types in default-grouping are valid"
  (->> (mapcat val @default-grouping)
       (remove (partial contains? (mapcat val attachment-types-by-permit-type)))) => empty?)

(fact "All attachment types in foreman-application-types are valid"
  (->> @foreman-application-types
       (remove (partial contains? (mapcat val attachment-types-by-permit-type)))) => empty?)

(fact "All attachment types in group-tag-mapping are valid"
  (->> (keys @type-grouping)
       (remove (partial contains? (mapcat val attachment-types-by-permit-type)))) => empty?)

(facts "predefined content mapping keys are valid"
  (doseq [{:keys [type-id type-group] :as type} (keys content-mapping)]
    (fact {:midje/description (str "type-id of " type)}
      (all-attachment-type-ids type-id) => truthy)
    (fact {:midje/description (str "type-group of " type)}
      (all-attachment-type-groups type-group) => truthy)))

(sc/defschema AType
  {(sc/optional-key :type-id)    (sc/enum :type1 :type2)
   (sc/optional-key :type-group) (sc/enum :group1 :group2)
   (sc/optional-key :kw)         sc/Keyword
   (sc/optional-key :str)        sc/Str
   (sc/optional-key :num)        sc/Num})

(defspec equals?-spec 50
  (prop/for-all [types (gen/vector (ssg/generator AType) 1 3)]
    (if (and (every? :type-id types)
             (every? :type-group types)
             (some->> (not-empty types) (map :type-id) (apply =))
             (some->> (not-empty types) (map :type-group) (apply =)))
      (is (apply equals? types))
      (is (not (apply equals? types))))))

(fact "equals compared two AttachemntType-like maps"
  (let [group1-foo-type {:type-id :foo :type-group :group1}
        group1-foo-type-with-meta {:type-id :foo :type-group :group1 :metadata {:for-operations #{}}}
        group1-bar-type {:type-id :bar :type-group :group1}
        group2-foo-type {:type-id :foo :type-group :group2}]
    (fact "arity 1 call of valid type is true"
      (equals? group1-foo-type) => true)
    (fact "compared any number of maps with type-id and type-group and ignored all other keys in comparison"
      (equals? group1-foo-type group1-foo-type-with-meta) => true
      (equals? group1-foo-type-with-meta group1-foo-type group1-foo-type group1-foo-type-with-meta) => true)
    (fact "false when group-id or type-id don't equal"
      (equals? group1-foo-type group1-bar-type) => false
      (equals? group1-foo-type group2-foo-type) => false)

    (fact "type-id and type-group can be keywords or strings even if AttachmentType says that they should be keywords;
           This makes it more convenient to compare values from db or rest-api without need for schema coersion"
      (equals? {:type-id :foo :type-group :group} {:type-id "foo" :type-group "group"})) => true
    (fact "arity 0 call should return false"
      (equals?) => false)
    (fact "When type-id or type-group are missing, it should return false"
      (equals? {:type-id :foo}) => false
      (equals? {:type-group :group}) => false)))

(defspec contains?-spec 30
  (prop/for-all [atype (ssg/generator AType)
                 types-coll (gen/vector (ssg/generator AType) 0 10)]
    (if (and (:type-id atype)
             (:type-group atype)
             (some->> (not-empty types-coll)
                      (map #(select-keys % [:type-id :type-group]))
                      (some #{(select-keys atype [:type-id :type-group])})))
      (is (contains? types-coll atype))
      (is (not (contains? types-coll atype))))))

(fact "resolve-type"
  (let [paatosote {:type-group :paatoksenteko, :type-id :paatosote}]
    (resolve-type "R" "paatosote") => paatosote
    (resolve-type :R :paatosote) => paatosote
    (resolve-type "YA" :paatosote) => {:type-group :muut, :type-id :paatosote}
    (resolve-type "R" "lasdkjflaskjdflakjsdf") => {}))

(fact "Every permit-type has attachments defined"
  (doseq [permit-type (keys (lupapalvelu.permit/permit-types))]
    (fact {:midje/description (str "Permit type " permit-type)}
      (attachment-types-by-permit-type (keyword permit-type)) => truthy)))

(defn count-operations [permit-type]
  (count (permit-type (operations/resolve-operation-names-by-permit-type true))))

(facts organization->organization-attachment-settings
  (facts "Case 1: When there is not data saved in the db, it should return attachment settings with convenient default values"
    (let [organization                            {:scope [{:permitType "R"} {:permitType "YI"}]}
          all-types-for-selected-scopes           (select-keys attachment-types-by-permit-type-as-attachment-map [:R :YI])
          expected-total-count-of-operation-nodes (+ (count-operations :R) (count-operations :YI))
          actual                                  (organization->organization-attachment-settings organization)]
      (fact ":defaults contains default values for all nodes. Currently, there is only one default value :allowed-types.

             :allowed-types value is basically hardcoded type listing for each permit-type from lupapiste/common."
        (get-in actual [:defaults :allowed-attachments]) => all-types-for-selected-scopes)

      (fact ":permit-type-nodes contains overridden attachemnt values per permit type.
              Because there are no overriddenv values each permit type in scope should have
              default value."
        (get-in actual [:permit-type-nodes])
        =>
        {:R  {:allowed-attachments {:mode :inherit :types {}}}
         :YI {:allowed-attachments {:mode :inherit :types {}}}})

      (fact ":operations-nodes should contain a node for all operations allowed by organization scope. "
        (count (get-in actual [:operation-nodes]))
        => expected-total-count-of-operation-nodes)

      (fact "For each operation-node operations should be empty and mode inherit.

             Note: Aggregation logic should be in frontend. Organisation-by-user query response is already
             realatively big it's better to not include any unnecessary redundant data in the response.
             Front end should, whatsoever, get all sufficient data for aggregation. In this case, default values
             are always handled by backend."
        (->> (get-in actual [:operation-nodes])
             (map (comp :allowed-attachments second))
             (distinct)) => [{:mode :inherit :types {}}])
      (facts "When organization have not overridden anything, get-organizations-attachment-types-for-operation should
              return permit type based attachment type listing for each operation unless operation has fixed attachment list
              (such as :tyonjohtajan-nimeaminen-v2 as in permit-type :R)"
        (doseq [[operation-id {:keys [permit-type]}] (:opration-nodes actual)
                :when                                (not (#{:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2} operation-id))
                :let                                 [test-case (str operation-id " should use permit-type " permit-type " attachment-type listing")
                                                      allowed-op-attachment (get-organizations-attachment-types-for-operation actual operation-id)
                                                      expected-permit-type-based-list (permit-type all-types-for-selected-scopes)

                                        ; attachment maps format is used for comparison in this test mainly because
                                        ; attachment-types-by-permit-type returns types with metadata and
                                        ; get-organizations-attachment-types-for-operation without metadata
                                                      actual-map (attachment-type-array->map allowed-op-attachment)]]
          (fact {:midje/description test-case} actual-map => expected-permit-type-based-list)))))

  (facts "Case 2: customer may override attachment setting on operation level."
    (let [organization                            {:scope [{:permitType "YI"}]
                                                   :operations-attachment-settings
                                                   {:operation-nodes
                                                    {:leirintaalueilmoitus {:allowed-attachments {:mode :set :types {:muut [:muu]}}}}}}
          all-types-for-selected-scopes           (select-keys attachment-types-by-permit-type-as-attachment-map [:YI])
          expected-total-count-of-operation-nodes (count-operations :YI)
          actual                                  (organization->organization-attachment-settings organization)]
      (fact ":defaults contains default values for all nodes. Currently there is only one default value :allowed-types.

             :allowed-types value is basically hardcoded type listing for each permit-type from lupapiste/common."
        (get-in actual [:defaults :allowed-attachments]) => all-types-for-selected-scopes)

      (fact ":operations nodes should contain all operations allowed by organization scope"
        (count (get-in actual [:operation-nodes])) => expected-total-count-of-operation-nodes)

      (fact "Only leirintäalueilmoitus (in this example) should have overridden allowed-attachment-type,
             and the all other nodes should have default value."
        (->> (get-in actual [:operation-nodes])
             (remove (fn [[operation _]] (= :leirintaalueilmoitus operation)))
             (map (comp :allowed-attachments second))
             (distinct)) => [{:mode :inherit :types {}}]

        (get-in actual [:operation-nodes :leirintaalueilmoitus :allowed-attachments])
        => {:mode :set :types {:muut [:muu]}})))

  (facts "Case 3: Customer can set value also on permit-type level"
    (let [organization                            {:scope [{:permitType "YI"} {:permitType "YM"}]
                                                   :operations-attachment-settings
                                                   {:permit-type-nodes {:YI {:allowed-attachments {:mode :set :types {:muut [:muu]}}}}}}
          all-types-for-selected-scopes           (select-keys attachment-types-by-permit-type-as-attachment-map [:YI :YM])
          expected-total-count-of-operation-nodes (+ (count-operations :YI) (count-operations :YM))
          actual                                  (organization->organization-attachment-settings organization)]
      (fact ":defaults should follow the same logic as in cases 1 and 2. I.e. value permit-types comes from lupapiste/common"
        (get-in actual [:defaults :allowed-attachments]) => all-types-for-selected-scopes)

      (fact ":operations-nodes should have the same defaults as in case 1. I.e. aggregation logic is not here but handled
             another function."
        (count (get-in actual [:operation-nodes]))
        => expected-total-count-of-operation-nodes

        (->> (get-in actual [:operation-nodes])
             (map (comp :allowed-attachments second))
             (distinct)) => [{:mode :inherit :types {}}])
      (fact "permit-type-node settings from dabase should be returned for :YI, :YM should have default value"
        (get-in actual [:permit-type-nodes])
        =>
        {:YM {:allowed-attachments {:mode :inherit :types {}}}
         :YI {:allowed-attachments {:mode :set :types {:muut [:muu]}}}}
        )

      (fact "Hidden operations are marked deprecated"
        (get-in actual [:operation-nodes :lannan-varastointi])
        => {:allowed-attachments {:mode :inherit :types {}}
            :default-attachments {}
            :deprecated?         true
            :permit-type         :YM})))

  (facts "Case 4: ARK permit type is exception to rule. It's not stored into scope but is
          deduced from two separate attributes in org settings permanent-archive-enabled and
          digitizer-tools-enabled (same as in digitalizer-api).

          When the both of them are on ARK permit type should be include in the organization-attachment-settings. After
          this the control flow is same as for any other permit type."
    (let [organization                            {:scope                     []
                                                   :permanent-archive-enabled true
                                                   :digitizer-tools-enabled   true}
          all-types-for-selected-scopes           (select-keys attachment-types-by-permit-type-as-attachment-map [:ARK])
          expected-total-count-of-operation-nodes (count-operations :ARK)
          actual                                  (organization->organization-attachment-settings organization)]
      (fact ":allowed-attachments should contain all attachment types allowed for ARK permit-type
             and nothing else."
        (get-in actual [:defaults :allowed-attachments]) => all-types-for-selected-scopes)

      (fact "Also ARK permit-type has settings node for allowed-attachement in the organization-attachment-settings
             data structure. This node says 'use all attachment types specified for ARK permit type by
             default'.

             Most likely it will never be overridden on the organization level even if it is technically possible."
        (get-in actual [:permit-type-nodes])
        =>
        {:ARK {:allowed-attachments {:mode :inherit :types {}}}})

      (fact ":operations-nodes should contain a node for all operations allowed by organization scope. Logic is same also
             to ARK permit type"
        (count (get-in actual [:operation-nodes]))
        => expected-total-count-of-operation-nodes)

      (fact "For each operation-node operations should be empty and mode inherit.

             Note: Aggregation logic should be in frontend. Organisation-by-user query response is already
             realatively big it's better to not include any unnecessary redundant data in the response.
             Front end should, whatsoever, get all sufficient data for aggregation. In this case, default values
             are always handled by backend."
        (->> (get-in actual [:operation-nodes])
             (map (comp :allowed-attachments second))
             (distinct)) => [{:mode :inherit :types {}}])))

  (facts "Case 5: :default-attachments-mandatory?, :default-attachments and :tos-function are aggregated to the same
          organization-attachment-settings datastructure from organization object.

          Ideally, also these settings would be also saved in the same organization-attachment-settings data structure
          in Mongo db. However, this change would require migration and changes to many places, where these settings are
          used, therefore it's not done at the moment (2022-11-14).

          Intent of data replication is make frontend code cleaner and simpler. The frontend does not need to know how
          data is saved to MongoDB, nor you need change front end if you migrate all attachment settings under the
          organization-attachment-settings  data structure in the future."
    (let [default-attachment            {:leirintaalueilmoitus [[:muu :muu]]}
          tos-function                  {:leirintaalueilmoitus "01 01 01 01"}
          default-attachments-mandatory ["leirintaalueilmoitus"]

          organization                            {:scope                         [{:permitType "YI"}]
                                                   :operations-attachments        default-attachment
                                                   :operations-tos-functions      tos-function
                                                   :default-attachments-mandatory default-attachments-mandatory}
          all-types-for-selected-scopes           (select-keys attachment-types-by-permit-type-as-attachment-map [:YI])
          expected-total-count-of-operation-nodes (count-operations :YI)
          actual                                  (organization->organization-attachment-settings organization)]

      (fact "Sanity check 1: It should have defaults for YI"
        (get-in actual [:defaults :allowed-attachments]) => all-types-for-selected-scopes)

      (fact "Sanity check 2: :operations-nodes should have the same nodes as in Case 1."
        (count (get-in actual [:operation-nodes]))
        => expected-total-count-of-operation-nodes

        (->> (get-in actual [:operation-nodes])
             (map (comp :allowed-attachments second))
             (distinct)) => [{:mode :inherit :types {}}])

      (fact "Given organization object is set up as `organization` above

             Leirintäalueilmoitus node should have
            - :default-attachments-mandatory? true, because operation is in :default-attachments-mandatory list in mongo
            - :tos-function \"01 01 01 01\" because operation has associated tosfunction value in mongo.
            - :default-attachment {:muu [:muu]} beucase attachemnt type muu.muu is associated to the opartaion in
              default-attachment map in mongo."
        (select-keys (get-in actual [:operation-nodes :leirintaalueilmoitus])
                     [:tos-function :default-attachments :default-attachments-mandatory?])
        => {:default-attachments-mandatory? true
            :tos-function                   "01 01 01 01"
            :default-attachments            {:muu [:muu]}})

      (fact "When there are no operation specific values set up in organization object for tos-function,
             default-attachments or default-attachments-mandatory, it sould use following defaults in operation node:

             - :default-attachments {} "
        (get-in actual [:operation-nodes :rekisterointi-ilmoitus ])
        => {:allowed-attachments {:mode :inherit :types {}}
            :default-attachments {}
            :permit-type         :YI}))))

(facts "apply-attachment-node-setup accumulates inheritable attachment data of map (:mode, :types)"
  (let [aggregation-state-node {:types {:g1 [:from-aggregation-state-node]}}
        child-node-1 {:mode :inherit :types {:g1 [:from-child-node-1]}}
        child-node-2 {:mode :set :types {:g1 [:from-child-node-2]}}]
    (fact ":mode :inherit"
      (apply-attachment-note-setup aggregation-state-node child-node-1)
      => {:types {:g1 [:from-aggregation-state-node]}})
    (fact ":mode :set"
      (apply-attachment-note-setup aggregation-state-node child-node-2)
      => {:types {:g1 [:from-child-node-2]}})))

(facts "get-organizations-attachment-types-for-operation /w its utility functions.

  NOTE: get-organizations-attachment-types-for-operation expects that settings given as input
   are correct. It does NOT care if child nodes allows types that are not included in
   parents sets or anything like that. This kind of business rules should be handles in
   UI or in commands."
  (let [defaults            {:allowed-attachments {:YI {:g1 [:default-for-YI]}
                                                   :YM {:g2 [:default-for-YM]}}}

        default-node-for-YI {:allowed-attachments {:mode :set
                                                   :types {:g1 [:default-for-YI]}}}
        default-node-for-YM {:allowed-attachments {:mode  :set
                                                   :types {:g2 [:default-for-YM]}}}

        permit-type-node-inherits  {:allowed-attachments {:mode :inherit
                                                          :types {}}}
        permit-type-node-overrides {:allowed-attachments {:mode :set
                                                          :types {:g1 [:overridden-by-permit-type]}}}
        op-node-1 {:permit-type         :YI
                   :allowed-attachments {:mode :set :types {:g1 [:from-op-1]}}}

        op-node-2 {:permit-type         :YM
                   :allowed-attachments {:mode :set :types {:g1 [:from-op-2]}}}
        op-node-3 {:permit-type         :YM
                   :allowed-attachments {:mode :inherit :types {}}}

        operation-settings-all {:defaults          defaults
                                :permit-type-nodes {:YI permit-type-node-inherits
                                                    :YM permit-type-node-overrides}
                                :operation-nodes   {:operation-1 op-node-1
                                                    :operation-2 op-node-2
                                                    :operation-3 op-node-3}}]
    (fact "validate-settings"
      (schema.core/validate att-schemas/ExpandedOperationsAttachmentSettings operation-settings-all)
      => operation-settings-all)

    (facts "collect-applicable-settings-nodes-for-operation should return vector of nodes where the root is the first node
            and the operation node is the last node. Group nodes are ordered for root to leaf (operation node)"
      (fact operation-settings-all
        (collect-applicable-settings-nodes-for-operation operation-settings-all :operation-1)
        => [default-node-for-YI permit-type-node-inherits op-node-1]
        (collect-applicable-settings-nodes-for-operation operation-settings-all :operation-2)
        => [default-node-for-YM permit-type-node-overrides op-node-2]
        (collect-applicable-settings-nodes-for-operation operation-settings-all :operation-3)
        => [default-node-for-YM permit-type-node-overrides op-node-3]))

    (facts "get-organizations-attachment-types-for-operation should apply all nodes and return attachments as
            attachment-type maps"
      (get-organizations-attachment-types-for-operation operation-settings-all :operation-1)
      => (attachment-map->attachment-type-array {:g1 [:from-op-1]}))

    (fact "get-attachment-types-for-application should return all attachment that are listed as allowed in
           the primary operation or in any of the secondary operations"
      (get-attachment-types-for-application operation-settings-all
                                            {:primaryOperation {:name "operation-1"}})
      => (attachment-map->attachment-type-array {:g1 [:from-op-1]})

      (set (get-attachment-types-for-application operation-settings-all
                                                 {:primaryOperation    {:name "operation-1"}
                                                  :secondaryOperations [{:name "operation-2"}
                                                                        {:name "operation-3"}]}))
      => (set (attachment-map->attachment-type-array {:g1 [:from-op-1 :from-op-2 :overridden-by-permit-type]})))

    (fact "get-all-allowed-attachment-types resoves and returns attahcment types of all operations in
           organiztion-attachment-settings maps"
      (set (get-all-allowed-attachment-types operation-settings-all))
      => (set (attachment-map->attachment-type-array {:g1 [:from-op-1 :from-op-2 :overridden-by-permit-type]})))))
