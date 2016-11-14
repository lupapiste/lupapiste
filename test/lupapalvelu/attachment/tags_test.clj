(ns lupapalvelu.attachment.tags-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.tags :refer :all]
            [lupapalvelu.attachment.type :as att-type]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(testable-privates lupapalvelu.attachment.tags
                   type-groups-for-operation
                   application-state-filters
                   group-and-type-filters
                   not-needed-filters)

(facts attachment-tags

  (fact "submitted - not-needed"
        (attachment-tags {:applicationState "submitted" :notNeeded true}) => (just #{:preVerdict :general :notNeeded} :in-any-order :gaps-ok))

  (fact "verdict-given - needed"
        (attachment-tags {:applicationState "verdictGiven" :notNeeded false}) => (just #{:postVerdict :general :needed} :in-any-order :gaps-ok))

  (fact "defaults"
    (attachment-tags {}) => (just #{:preVerdict :general :needed} :in-any-order :gaps-ok))

  (fact "parties"
    (attachment-tags {:groupType "parties"}) => (just #{:preVerdict :parties :needed} :in-any-order :gaps-ok))

  (fact "operation"
    (attachment-tags {:groupType "operation" :op {:id "someOpId"}}) => (just #{:preVerdict :needed :operation "op-id-someOpId" :other} :in-any-order :gaps-ok))

  (fact "file"
    (attachment-tags {:latestVersion {:fileId "someFileId"}}) => (just #{:preVerdict :general :needed :hasFile} :in-any-order :gaps-ok))

  (fact "type"
    (attachment-tags {:type {:type-group "somegroup" :type-id "sometype"}}) => (just #{:preVerdict :general :needed :somegroup} :in-any-order :gaps-ok)
    (provided (att-type/tag-by-type {:type {:type-group "somegroup" :type-id "sometype"}}) => :somegroup))

  (fact "verdictGiven - RAM"
        (attachment-tags {:applicationState "verdictGiven" :ramLink "foobar"}) => (just #{:ram :general :needed} :in-any-order :gaps-ok)))

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


(facts "attachments-filters"

  (facts "application state"
    (fact "pre verdict - no post verdict attachments"
      (let [attachments  [{:applicationState "submitted"}
                          {:applicationState "open"}
                          {:applicationState "draft"}]
            application {:state "open"
                         :attachments attachments}]
        (application-state-filters application) =>  [{:tag :preVerdict :default true}]))

    (fact "pre verdict - with post verdict attachment"
      (let [attachments  [{:applicationState "submitted"}
                          {:applicationState "open"}
                          {:applicationState "verdictGiven"}]
            application {:state "submitted"
                         :attachments attachments}]
        (application-state-filters application) =>  [{:tag :preVerdict :default true}
                                                     {:tag :postVerdict :default false}]))

    (fact "post verdict"
      (let [attachments  [{:applicationState "submitted"}
                          {:applicationState "open"}
                          {:applicationState "verdictGiven"}]
            application {:state "verdictGiven"
                         :attachments attachments}]
        (application-state-filters application) =>  [{:tag :preVerdict :default false}
                                                     {:tag :postVerdict :default true}]))

    (fact "pre verdict - with ram"
      (let [attachments  [{:applicationState "verdictGiven" :ramLink "anyid"}
                          {:applicationState "open"}
                          {:applicationState "draft"}]
            application {:state "open"
                         :attachments attachments}]
        (application-state-filters application) =>  [{:tag :preVerdict :default true}
                                                     {:tag :ram :default true}]))

    (fact "post verdict - with ram"
      (let [attachments  [{:applicationState "verdictGiven" :ramLink "anyid"}
                          {:applicationState "open"}
                          {:applicationState "constructionStarted"}]
            application {:state "verdictGiven"
                         :attachments attachments}]
        (application-state-filters application) =>  [{:tag :preVerdict :default false}
                                                     {:tag :postVerdict :default true}
                                                     {:tag :ram :default true}])))

  (facts "group and type"
    (fact "with parties group"
      (let [attachments  [{:groupType "parties" :type {:type-group "somegroup" :type-id "sometype"}}]
            application {:attachments attachments}]

        (group-and-type-filters application) =>   [{:tag :parties :default false}]

        (provided (att-type/tag-by-type anything) => nil)))

    (fact "with parties and general group"
      (let [attachments  [{:groupType "parties"}
                          {:groupType "unknown"}
                          {:groupType nil}]
            application {:attachments attachments}]

        (group-and-type-filters application) =>   [{:tag :general :default false}
                                                   {:tag :parties :default false}]

        (provided (att-type/tag-by-type anything) => nil)))

    (fact "attachment type - one attachment"

      (let [attachments  [{:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "sometype"}}]
            application  {:attachments attachments}]

        (group-and-type-filters application) =>   [{:tag :iv_suunnitelma :default false}]

        ;; Correct type group tag names must be used, since function ensures the order of the tags by names
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)))

    (fact "attachment type - many attachments"

      (let [attachments  [{:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "sometype"}}
                          {:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "anothertype"}}
                          {:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "othertype"}}]
            application  {:attachments attachments}]

        (group-and-type-filters application) =>   [{:tag :paapiirustus :default false}
                                                   {:tag :iv_suunnitelma :default false}
                                                   {:tag :other :default false}]

        ;; Correct type group tag names must be used, since function ensures the order of the tags by names
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :paapiirustus)
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "othertype"}))) => :other)))

    (fact "all together"

      (let [attachments  [{:groupType "parties" :type {:type-group "somegroup" :type-id "sometype"}}
                          {:groupType "unknown" :type {:type-group "somegroup" :type-id "sometype"}}
                          {:type {:type-group "somegroup" :type-id "sometype"}}
                          {:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "sometype"}}
                          {:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "anothertype"}}
                          {:groupType "operation" :op {:id "op"} :type {:type-group "somegroup" :type-id "othertype"}}]
            application  {:attachments attachments}]

        (group-and-type-filters application) =>   [{:tag :general :default false}
                                                   {:tag :parties :default false}
                                                   {:tag :paapiirustus :default false}
                                                   {:tag :iv_suunnitelma :default false}
                                                   {:tag :other :default false}]

        ;; Correct type group tag names must be used, since function ensures the order of the tags by names
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :paapiirustus)
        (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "othertype"}))) => :other))))

  (facts "not needed"
    (fact "one attachment - not needed true"
      (let [attachments [{:notNeeded true :type {:type-group "somegroup" :type-id "sometype"}}]
            application {:attachments attachments}]

        (not-needed-filters application) => nil))

    (fact "one attachment - not needed false"
      (let [attachments [{:notNeeded false :type {:type-group "somegroup" :type-id "sometype"}}]
            application {:attachments attachments}]

        (not-needed-filters application) => nil))

    (fact "one true - other false"
      (let [attachments [{:notNeeded true  :type {:type-group "somegroup" :type-id "sometype"}}
                         {:notNeeded false :type {:type-group "somegroup" :type-id "sometype"}}]
            application {:attachments attachments}]

        (not-needed-filters application) => [{:tag :needed :default true}
                                             {:tag :notNeeded :default false}])))

  (fact "all filters"
    (let [attachments  [{:applicationState "verdictGiven"}
                        {:groupType "parties"}
                        {:groupType "unknown"}
                        {:groupType "operation" :type {:type-group "somegroup" :type-id "sometype"}}
                        {:groupType "operation" :type {:type-group "somegroup" :type-id "anothertype"}}
                        {:groupType "operation" :type {:type-group "somegroup" :type-id "othertype"}}
                        {:notNeeded true}]
          application {:attachments attachments :state "submitted"}]

      (attachments-filters application) =>   [[{:tag :preVerdict :default true}
                                               {:tag :postVerdict :default false}]
                                              [{:tag :general :default false}
                                               {:tag :parties :default false}
                                               {:tag :paapiirustus :default false}
                                               {:tag :iv_suunnitelma :default false}
                                               {:tag :other :default false}]
                                              [{:tag :needed :default true}
                                               {:tag :notNeeded :default false}]]

      ;; Correct type group tag namess must be used, since function ensures the order of the tags by names
      (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "sometype"}))) => :iv_suunnitelma)
      (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "anothertype"}))) => :paapiirustus)
      (provided (att-type/tag-by-type (as-checker #(= (:type %) {:type-group "somegroup" :type-id "othertype"}))) => :other)
      (provided (att-type/tag-by-type anything) => nil))))

(facts sort-by-tags
  (fact "one main group"
    (sort-by-tags [{:tags [:main-tag :some-tag]} {:tags [:main-tag :another-tag]}] [[:main-tag]])
    => [{:tags [:main-tag :some-tag]} {:tags [:main-tag :another-tag]}])

  (fact "empty group equals no sorting"
    (sort-by-tags [{:tags [:some-tag]} {:tags [:another-tag]} {:tags [:just-tag]}] [])
    => [{:tags [:some-tag]} {:tags [:another-tag]} {:tags [:just-tag]}])

  (fact "two main groups"
    (sort-by-tags [{:tags [:main-tag :some-tag]} {:tags [:another-main-tag]}] [[:another-main-tag] [:main-tag]])
    => [{:tags [:another-main-tag]} {:tags [:main-tag :some-tag]}])

  (fact "attachments not in a group are omitted"
    (sort-by-tags [{:tags [:some-tag]} {:tags [:main-tag :another-tag]}] [[:main-tag]])
    => [{:tags [:main-tag :another-tag]}])

  (fact "attachments not in any sub group are omitted"
    (sort-by-tags [{:tags [:main-tag :some-tag]} {:tags [:main-tag :another-tag]}] [[:main-tag [:some-tag]]])
    => [{:tags [:main-tag :some-tag]}])

  (fact "multiple attachments and multiple groups"
    (sort-by-tags [{:n 1 :tags [:main-tag :group1 :just-tag]}
                   {:n 2 :tags [:main-tag :group2 :deep2-tag :deep-group-tag :inner-group-tag]}
                   {:n 3 :tags [:another-main-tag :whatever-tag]}
                   {:n 4 :tags [:main-tag :group2 :deep2-tag :deep-group-tag :inner-group-tag]}
                   {:n 5 :tags [:main-tag :group1 :another-tag]}
                   {:n 6 :tags [:main-tag :group2 :deep1-tag :deep-group-tag :inner-group-tag]}]
                       [[:main-tag
                         [:group1 [:another-tag] [:just-tag]]
                         [:group2 [:inner-group-tag [:deep-group-tag [:deep1-tag] [:deep2-tag]]]]]
                        [:another-main-tag]])
    => [{:n 5 :tags [:main-tag :group1 :another-tag]}
        {:n 1 :tags [:main-tag :group1 :just-tag]}
        {:n 6 :tags [:main-tag :group2 :deep1-tag :deep-group-tag :inner-group-tag]}
        {:n 2 :tags [:main-tag :group2 :deep2-tag :deep-group-tag :inner-group-tag]}
        {:n 4 :tags [:main-tag :group2 :deep2-tag :deep-group-tag :inner-group-tag]}
        {:n 3 :tags [:another-main-tag :whatever-tag]}]))
