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
    (attachment-tags {:applicationState :submitted :notNeeded true}) => (just #{:preVerdict :notNeeded} :in-any-order :gaps-ok))

  (fact "verdict-given - needed"
    (attachment-tags {:applicationState :verdictGiven :notNeeded false}) => (just #{:postVerdict :needed} :in-any-order :gaps-ok))

  (fact "defaults"
    (attachment-tags {}) => (just #{:preVerdict :needed} :in-any-order :gaps-ok))

  (fact "parties"
    (attachment-tags {:groupType "parties"}) => (just #{:preVerdict :parties :needed} :in-any-order :gaps-ok))

  (fact "operation"
    (attachment-tags {:groupType "operation" :op {:id "some-op-id"}}) => (just #{:preVerdict :needed :operation "some-op-id"} :in-any-order :gaps-ok))

  (fact "type"
    (attachment-tags {:type {:type-group "somegroup" :type-id "sometype"}}) => (just #{:preVerdict :needed :somegroup} :in-any-order :gaps-ok)
    (provided (att-type/tag-by-type {:type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)))

(facts type-groups-for-operation
  (fact "one attachment - grouping stresshold not exceeded"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}]]

      (type-groups-for-operation attachments operation-id) => nil

      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)))


  (fact "one attachment per operation - grouping stresshold not exceeded"
    (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
          operation-id2 (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}]]

      (type-groups-for-operation attachments operation-id1) => nil

      (provided (att-type/tag-by-type {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)))


  (fact "two attachment - same group"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}]]

      (type-groups-for-operation attachments operation-id) => [:somegroup]

      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)))


  (fact "two attachment - same group - not grouping attachment type"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}]]

      (type-groups-for-operation attachments operation-id) => nil

      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}) => nil)))


  (fact "many attachment - many groups"
    (let [operation-id (ssg/generate ssc/ObjectIdStr)
          attachments  [{:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "anothertype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "anothertype"}}
                        {:op {:id operation-id} :type {:type-group "somegroup" :type-id "single-attachment-type"}}]]

      (type-groups-for-operation attachments operation-id) => [:somegroup :anothergroup]

      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "anothertype"}}) => :anothergroup)
      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "single-attachment-type"}}) => :single-group)
      (provided (att-type/tag-by-type {:op {:id operation-id} :type {:type-group "somegroup" :type-id "not-grouping-type"}}) => nil))))


(fact "tag-grouping-for-group-type - operation"
  (let [operation-id1 (ssg/generate ssc/ObjectIdStr)
        operation-id2 (ssg/generate ssc/ObjectIdStr)
        operation-id3 (ssg/generate ssc/ObjectIdStr)
        attachments  [{:groupType "parties" :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "single-attachment-type"}}]]

    (tag-grouping-for-group-type attachments :operation) => [[operation-id1]
                                                             [operation-id2 [:somegroup] [:default]]
                                                             [operation-id3 [:somegroup] [:anothergroup] [:default]]]

      (provided (att-type/tag-by-type {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
      (provided (att-type/tag-by-type {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
      (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
      (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}) => :anothergroup)
      (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "single-attachment-type"}}) => :single-group)))


(fact "attachment-tag-groups"
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
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "single-attachment-type"}}]]

    (attachment-tag-groups attachments) => [[:default]
                                            [:parties]
                                            [operation-id1]
                                            [operation-id2 [:somegroup] [:default]]
                                            [operation-id3 [:somegroup] [:anothergroup] [:default]]]

    (provided (att-type/tag-by-type {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
    (provided (att-type/tag-by-type {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
    (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
    (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}) => :anothergroup)
    (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "single-attachment-type"}}) => :single-group)))


(fact "attachments-filters"
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
                      {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "single-attachment-type"}}]]

    (attachments-filters attachments) =>   [[{:tag :preVerdict :default false}
                                             {:tag :postVerdict :default false}]
                                            [{:tag :somegroup :default false}
                                             {:tag :anothergroup :default false}
                                             {:tag :single-group :default false}]
                                            [{:tag :needed :default true}
                                             {:tag :notNeeded :default false}]]

    (provided (att-type/tag-by-type {:op {:id operation-id1} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
    (provided (att-type/tag-by-type {:op {:id operation-id2} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
    (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup)
    (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "anothertype"}}) => :anothergroup)
    (provided (att-type/tag-by-type {:op {:id operation-id3} :type {:type-group "somegroup" :type-id "single-attachment-type"}}) => :single-group)
    (provided (att-type/tag-by-type anything) => nil)))
