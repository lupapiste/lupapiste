(ns lupapalvelu.attachment.tags-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.tags :refer :all]
            [lupapalvelu.attachment.type :as att-type]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(testable-privates lupapalvelu.attachment.tags type-groups-for-operation)

(facts attachment-tags

  (fact "submitted - not-needed"
    (attachment-tags {:applicationState :submitted :notNeeded true}) => (just #{:preVerdict :general :notNeeded} :in-any-order :gaps-ok))

  (fact "verdict-given - needed"
    (attachment-tags {:applicationState :verdictGiven :notNeeded false}) => (just #{:postVerdict :general :needed} :in-any-order :gaps-ok))

  (fact "defaults"
    (attachment-tags {}) => (just #{:preVerdict :general :needed} :in-any-order :gaps-ok))

  (fact "parties"
    (attachment-tags {:groupType "parties"}) => (just #{:preVerdict :parties :needed} :in-any-order :gaps-ok))

  (fact "operation"
    (attachment-tags {:groupType "operation" :op {:id "someOpId"}}) => (just #{:preVerdict :needed :operation "op-id-someOpId" :other} :in-any-order :gaps-ok))

  (fact "type"
    (attachment-tags {:type {:type-group "somegroup" :type-id "sometype"}}) => (just #{:preVerdict :general :needed :somegroup} :in-any-order :gaps-ok)
    (provided (att-type/tag-by-type {:type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)))

(facts type-groups-for-operation
  (fact "one attachment"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}]]

      (type-groups-for-operation attachments operation-id) => [:somegroup]

      (provided (att-type/tag-by-type anything) => :somegroup)))

  (fact "two attachment - same group"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}]]

      (type-groups-for-operation attachments operation-id) => [:somegroup]

      (provided (att-type/tag-by-type anything) => :somegroup)))


  (fact "two attachment - same group - not grouping attachment type"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}]]

      (type-groups-for-operation attachments operation-id) => [:other]

      (provided (att-type/tag-by-type anything) => nil)))


  (fact "many attachment - many groups"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "anothertype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "anothertype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "other-type"}}]]

      (type-groups-for-operation attachments operation-id) => [:other :somegroup :anothergroup]

    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :somegroup)
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :anothergroup)
    (provided (att-type/tag-by-type anything) => nil))))


(fact "tag-grouping-for-group-type - operation"
  (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
        operation-id2 (ssg/generate ssc/ObjectIdStr)
        operation-id3 (ssg/generate ssc/ObjectIdStr)
        attachments  [{:groupType "parties" :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "other-type"}}]
        application {:primaryOperation {:id operation-id1}
                     :secondaryOperations [{:id operation-id2} {:id operation-id3}]
                     :attachments attachments}]

    (tag-grouping-for-group-type application :operation) => [[(str "op-id-" operation-id1) [:somegroup]]
                                                             [(str "op-id-" operation-id2) [:somegroup] [:anothergroup]]
                                                             [(str "op-id-" operation-id3) [:somegroup] [:anothergroup] [:other]]]

    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :somegroup)
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :anothergroup)
    (provided (att-type/tag-by-type anything) => nil)))


(fact "attachment-tag-groups"
  (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
        operation-id2 (ssg/generate ssc/ObjectIdStr)
        operation-id3 (ssg/generate ssc/ObjectIdStr)
        attachments  [{:groupType "parties" :type {:type-group "somegroup" :type-id "sometype"}}
                      {:groupType "unknown" :type {:type-group "somegroup" :type-id "sometype"}}
                      {:type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "other-type"}}]
        application {:primaryOperation {:id operation-id1}
                     :secondaryOperations [{:id operation-id2} {:id operation-id3}]
                     :attachments attachments}]

    (attachment-tag-groups application) => [[:general]
                                            [:parties]
                                            [(str "op-id-" operation-id1) [:somegroup]]
                                            [(str "op-id-" operation-id2) [:somegroup] [:anothergroup]]
                                            [(str "op-id-" operation-id3) [:somegroup] [:anothergroup] [:other]]]

    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :somegroup)
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :anothergroup)
    (provided (att-type/tag-by-type anything) => nil)))


(fact "attachments-filters, operations and types"
  (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
        operation-id2 (ssg/generate ssc/ObjectIdStr)
        operation-id3 (ssg/generate ssc/ObjectIdStr)
        attachments  [{:groupType "parties" :type {:type-group "somegroup" :type-id "sometype"}}
                      {:groupType "unknown" :type {:type-group "somegroup" :type-id "sometype"}}
                      {:type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "other-type"}}]
        application {:primaryOperation {:id operation-id1}
                     :secondaryOperations [{:id operation-id2} {:id operation-id3}]
                     :attachments attachments}]

    (attachments-filters application) =>   [[{:tag :general :default false}
                                             {:tag :parties :default false}
                                             {:tag :paapiirustus :default false}
                                             {:tag :iv_suunnitelma :default false}
                                             {:tag :other :default false}]]

    ;; Correct type group tag namess must be used, since function ensures the order of the tags by names
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :paapiirustus)
    (provided (att-type/tag-by-type anything) => nil)))

(fact "attachments-filters, not needed"
  (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
        operation-id2 (ssg/generate ssc/ObjectIdStr)
        operation-id3 (ssg/generate ssc/ObjectIdStr)
        attachments  [{:type {:type-group "somegroup" :type-id "sometype"}}
                      {:notNeeded true :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "other-type"}}]
        application {:primaryOperation {:id operation-id1}
                     :secondaryOperations [{:id operation-id2} {:id operation-id3}]
                     :attachments attachments}]

    (attachments-filters application) =>   [[{:tag :general :default false}
                                             {:tag :iv_suunnitelma :default false}
                                             {:tag :other :default false}]
                                            [{:tag :needed :default true}
                                             {:tag :notNeeded :default false}]]

    ;; Correct type group tag namess must be used, since function ensures the order of the tags by names
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)
    (provided (att-type/tag-by-type anything) => nil)))

(fact "attachments-filters, application state"
  (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
        operation-id2 (ssg/generate ssc/ObjectIdStr)
        operation-id3 (ssg/generate ssc/ObjectIdStr)
        attachments  [{:applicationState :verdictGiven :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "other-type"}}]
        application {:primaryOperation {:id operation-id1}
                     :secondaryOperations [{:id operation-id2} {:id operation-id3}]
                     :attachments attachments
                     :state :submitted}]
    (attachments-filters application) =>   [[{:tag :preVerdict :default true}
                                             {:tag :postVerdict :default false}]
                                            [{:tag :general :default false}
                                             {:tag :iv_suunnitelma :default false}
                                             {:tag :other :default false}]]

    ;; Correct type group tag namess must be used, since function ensures the order of the tags by names
    (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)
    (provided (att-type/tag-by-type anything) => nil)))
