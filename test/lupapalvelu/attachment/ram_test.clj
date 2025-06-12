(ns lupapalvelu.attachment.ram-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.generators :as gen]
            [lupapalvelu.attachment :refer [Attachment attachment-required-keys]]
            [lupapalvelu.attachment.ram :as ram]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(def ^:private required-keys-attachment (select-keys Attachment attachment-required-keys))

(deftest make-ram-attachment-test
  (let [application     {:tosFunction  nil
                         :verdicts     []
                         :organization "753-R"
                         :state        :verdictGiven}
        base-attachment (ssg/generate Attachment)
        ram-attachment  (ram/make-ram-attachment base-attachment application 12345)]
    (testing "attachment type is copied"
      (is (= (:type base-attachment) (:type ram-attachment))))

    (testing "ram-link is created"
      (is (= (:id base-attachment) (:ramLink ram-attachment))))

    (testing "ram-attachment is valid"
      (is (nil? (sc/check Attachment ram-attachment)))))

  (let [application     {:tosFunction  nil
                         :verdicts     []
                         :organization "753-R"
                         :state        :constructionStarted}
        base-attachment (assoc (ssg/generate required-keys-attachment) :contents "foo content" :size :A0 :scale :1:10)
        ram-attachment  (ram/make-ram-attachment base-attachment application 12345)]
    (testing "attachment meta is copied"
      (is (= "foo content" (:contents ram-attachment)))
      (is (= :A0 (:size ram-attachment)))
      (is (= :1:10 (:scale ram-attachment))))))

(deftest resolve-ram-linked-attachments-test
  (testing "backward ram link"
    (let [attachment1 (-> (ssg/generate required-keys-attachment) (assoc :ramLink nil))
          attachment2 (-> (ssg/generate required-keys-attachment) (assoc :ramLink (:id attachment1)))]
      (is (= [attachment1 attachment2]
             (ram/resolve-ram-links [attachment1 attachment2] (:id attachment2))))))

  (testing "forward ram link"
    (let [attachment1 (-> (ssg/generate required-keys-attachment) (assoc :ramLink nil))
          attachment2 (-> (ssg/generate required-keys-attachment) (assoc :ramLink (:id attachment1)))]
      (is (= [attachment1 attachment2]
             (ram/resolve-ram-links [attachment1 attachment2] (:id attachment1))))))

  (testing "no linking"
    (let [ids         (gen/sample ssg/object-id 5)
          attachments (mapv (fn [id] {:id id :ramLink nil}) ids)]
      (is (= [(nth attachments 2)]
             (ram/resolve-ram-links attachments (:id (nth attachments 2)))))))

  (testing "complete ram link chain"
    (let [ids         (gen/sample ssg/object-id 5)
          attachments (mapv (fn [[link id]] {:id id :ramLink link}) (partition 2 1 ids))]
      (is (= attachments
             (ram/resolve-ram-links attachments (nth ids 2))))))

  (testing "incomplete ram link chain"
    (let [ids         (gen/sample ssg/object-id 5)
          attachments (-> (mapv (fn [[link id]] {:id id :ramLink link}) (partition 2 1 ids))
                          (assoc-in [3 :ramLink] nil))]
      (is (= (take 3 attachments)
             (ram/resolve-ram-links attachments (nth ids 1))))))

  (testing "self-linked - should not get in endless loop"
    (let [id         (ssg/generate ssc/ObjectIdStr)
          attachment {:id id :ramLink id}]
      (is (= [attachment]
             (ram/resolve-ram-links [attachment] id)))))

  (testing "link ring - should not get in endless loop"
    (let [ids         (gen/sample ssg/object-id 5)
          attachments (mapv (fn [[link id]] {:id id :ramLink link}) (partition 2 1 ids ids))
          result      (ram/resolve-ram-links attachments (nth ids 2))]
      (is (= (count attachments) (count result)))
      (is (= (set attachments) (set result))))))

(deftest ram-pre-checkers-test
  (let [ram-fail (fn [code] {:ok false :text (name code)})
        att1     (assoc (dissoc (ssg/generate Attachment) :ramLink)
                        :versions [{:originalFileId "fileid1"}]
                        :latestVersion {:originalFileId "fileid1"})
        att2     (assoc (ssg/generate Attachment)
                        :ramLink (:id att1)
                        :versions [{:originalFileId "fileid2"}]
                        :latestVersion {:originalFileId "fileid2"})
        att3     (assoc (ssg/generate Attachment) :ramLink (:id att2))]

    (testing "attachment-status-ok"
      (is (= (ram-fail :error.attachment-not-approved)
             (ram/attachment-status-ok {:data        {:attachmentId (:id att2)}
                                        :application {:attachments [att1 (assoc-in att2 [:approvals :fileid2 :state] :requires_user_action)]}})))
      (is (nil? (ram/attachment-status-ok {:data        {:attachmentId (:id att2)}
                                           :application {:attachments [att1 (assoc-in att2 [:approvals :fileid2 :state] :ok)]}}))))

    (testing "ram-status-not-ok"
      (is (nil? (ram/ram-status-not-ok {:data        {:attachmentId (:id att1)}
                                        :application {:attachments [att1 att2]}})))
      (is (nil? (ram/ram-status-not-ok {:data        {:attachmentId (:id att1)}
                                        :application {:attachments [(assoc-in att1 [:approvals :fileid1 :state] :ok) att2]}})))
      (is (nil? (ram/ram-status-not-ok {:data        {:attachmentId (:id att2)}
                                        :application {:attachments [att1 (dissoc att2 :state)]}})))
      (is (= (ram-fail :error.ram-approved)
             (ram/ram-status-not-ok {:data        {:attachmentId (:id att2)}
                                     :application {:attachments [att1 (assoc-in att2 [:approvals :fileid2 :state] :ok)]}}))))

    (testing "ram-not-linked"
      (is (nil? (ram/ram-not-linked {:data        {:attachmentId (:id att3)}
                                     :application {:attachments [att1 att2 att3]}})))
      (is (= (ram-fail :error.ram-linked)
             (ram/ram-not-linked {:data        {:attachmentId (:id att2)}
                                  :application {:attachments [att1 att2 att3]}})))
      (is (= (ram-fail :error.ram-linked)
             (ram/ram-not-linked {:data        {:attachmentId (:id att1)}
                                  :application {:attachments [att1 att2 att3]}})))
      (is (nil? (ram/ram-not-linked {:data        {:attachmentId (:id att3)}
                                     :application {:attachments [att1 att2 att3]}})))
      (is (nil? (ram/ram-not-linked {:data        {}
                                     :application {:attachments [att1 att2 att3]}}))))

    (testing "attachment-type-allows-ram"
      (letfn [(params [type-group] {:data        {:attachmentId (:id att1)}
                                    :application {:attachments [(assoc-in att1 [:type :type-group] type-group)]}})]
        (is (= (ram-fail :error.ram-not-allowed)
               (ram/attachment-type-allows-ram (params "selvitykset"))))
        (is (nil? (ram/attachment-type-allows-ram (params "paapiirustus"))))))

    (testing "organization-allows-ram"
      (is (= (ram-fail :error.ram-disabled-in-organization)
             (ram/organization-allows-ram {:organization (delay {:ram {:disabled true}})})))
      (is (nil? (ram/organization-allows-ram {:organization (delay {:ram {:disabled false}})}))))))
