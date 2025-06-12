(ns lupapalvelu.ui.docgen.component.approval-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.component.accordion-toolbar :as at]
            [lupapalvelu.ui.docgen.component.approval-common :as approval-common]
            [lupapalvelu.ui.docgen.component.reject-note :as reject-note]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [lupapalvelu.ui.user :as user]
            [re-frame.core :as rf]
            [sade.util :as util]))

(t/use-fixtures :once test-utils/js-services-fixture)

;; See `lupapalvelu.ui.docgen.feature.approvals` for some notes
;; on how this approval system seems to work.

(def default-allowed-actions
  {:approve-doc     {:ok true}
   :reject-doc      {:ok true}
   :reject-doc-note {:ok true}})

(def doc-random
  {:id             "doc-random"
   :schema         {:info {:name "some-random-doc"}
                    :body []}
   :allowedActions (assoc default-allowed-actions :approve-doc {:ok false :reason "error.missing-parameters"})
   :data           {}})

(def doc-rejected
  "Authority has rejected the whole document, but has not touched individual groups"
  {:id             "doc-rejected"
   :schema         {:info {:name       "some-doc-rejected"
                           :approvable true}
                    :body [{:name       "someGroup"
                            :type       "group"
                            :approvable true
                            :body       []}
                           {:name "otherGroup"
                            :type "group"
                            :body []}]}
   :allowedActions default-allowed-actions
   :data           {}})

(def doc-approved
  "Authority has approved the whole document, but has not touched individual groups"
  {:id             "doc-approved"
   :schema         {:info {:name       "some-doc-approved"
                           :approvable true}
                    :body [{:name       "someGroup"
                            :type       "group"
                            :approvable true
                            :body       []}
                           {:name "otherGroup"
                            :type "group"
                            :body []}]}
   :allowedActions default-allowed-actions
   :data           {}})

(def app (assoc test-utils/app :documents [doc-random
                                           doc-rejected
                                           doc-approved]))
(def app-id test-utils/app-id)
(def details (assoc test-utils/details
                    :applicationApprovals {:doc-rejected {:_root     {:timestamp 1759383799986
                                                                      :value     "rejected"
                                                                      :note      "Kaikki pilalla"
                                                                      :user      {:firstName "Veikko"
                                                                                  :lastName  "Viranomainen"}}
                                                          :someGroup {:timestamp 0
                                                                      :override  "rejected"}}
                                           :doc-approved {:_root     {:timestamp 1759227998159
                                                                      :value     "approved"
                                                                      :note      "Kaikki ok"
                                                                      :user      {:firstName "Veikko"
                                                                                  :lastName  "Viranomainen"}}
                                                          ;; This group was automatically approved then the
                                                          ;; authority user approved the document
                                                          :someGroup {:timestamp 0
                                                                      :override  "approved"}}}))

(deftest approval-subscripts-at-startup-test
  (rf-test/run-test-sync
    ;; Override authority check to avoid referencing `js/lupapisteApp.models.currentUser`
    (with-redefs [user/authority? (constantly true)]
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "approval/rejection status"
        (testing "approved?"
          (is (not (<sub [::approvals/approved? "doc-random"])))
          (is (not (<sub [::approvals/approved? "doc-rejected"])))
          (is (<sub [::approvals/approved? "doc-approved"])))
        (testing "rejected?"
          (is (not (<sub [::approvals/rejected? "doc-random"])))
          (is (<sub [::approvals/rejected? "doc-rejected"]))
          (is (not (<sub [::approvals/rejected? "doc-approved"]))))

        (testing "group"
          (is (<sub [::approvals/approved? "doc-approved" [:someGroup]]))
          (is (<sub [::approvals/rejected? "doc-rejected" [:someGroup]]))))

      (testing "check OK / Reject buttons in the toolbar"
        (testing "approve?"
          (is (not (<sub [::at/may-approve-or-reason "doc-random"])))
          (is (<sub [::at/may-approve-or-reason "doc-rejected"]))
          (is (not (<sub [::at/may-approve-or-reason "doc-approved"]))))
        (testing "reject?"
          (is (not (<sub [::at/may-reject? "doc-random"])))
          (is (not (<sub [::at/may-reject? "doc-rejected"])))
          (is (<sub [::at/may-reject? "doc-approved"]))))

      (testing "approval/rejection details"
        (is (nil? (<sub [::approvals/details "doc-random"])))
        (is (= "\"document.rejected\" 2.10.2025 08:43: Veikko Viranomainen"
               (<sub [::approvals/details "doc-rejected"])))
        (is (= "\"document.approved\" 30.9.2025 13:26: Veikko Viranomainen"
               (<sub [::approvals/details "doc-approved"])))

        (testing "group"
          (is (nil? (<sub [::approvals/details "doc-rejected" [:someGroup]])))))

      (testing "rejection notes"
        (is (not (<sub [::reject-note/show-note? "doc-random"])))
        (is (<sub [::reject-note/show-note? "doc-rejected"]))
        (is (<sub [::reject-note/show-note? "doc-approved"]))

        (is (not (<sub [::reject-note/show-editor? "doc-random"])))
        (is (not (<sub [::reject-note/show-editor? "doc-rejected"])))
        (is (not (<sub [::reject-note/show-editor? "doc-approved"])))

        (is (= "Kaikki pilalla" (<sub [::reject-note/reject-note "doc-rejected"])))
        (is (= "Kaikki ok" (<sub [::reject-note/reject-note "doc-approved"])))))))

(deftest authority-rejects-group-test
  ;; Test case where the authority rejects a single group within an approved
  ;; document and then writes a note for it.
  (rf-test/run-test-sync
    ;; Override authority check to avoid referencing `js/lupapisteApp.models.currentUser`
    (with-redefs [user/authority? (constantly true)]
      (let [{:keys [expect-action
                    handler]} (test-utils/make-mock-command)
            doc-id            "doc-approved"
            path              [:someGroup]]

        (rf/reg-fx :action/command handler)

        (>evt [::docgen/app-loaded {:app     app
                                    :details details}])

        (testing "situation at startup"
          (testing "approval/rejection status => doc & group are approved"
            (is (<sub [::approvals/approved? doc-id]))
            (is (not (<sub [::approvals/rejected? doc-id])))
            (is (<sub [::approvals/approved? doc-id path]))
            (is (not (<sub [::approvals/rejected? doc-id path]))))

          (testing "check OK / Reject buttons in the toolbar"
            (is (not (<sub [::at/may-approve-or-reason doc-id])))
            (is (<sub [::at/may-reject? doc-id])))

          (testing "approval/rejection details"
            (is (= "\"document.approved\" 30.9.2025 13:26: Veikko Viranomainen"
                   (<sub [::approvals/details doc-id])))
            (is (nil? (<sub [::approvals/details doc-id path]))))

          (testing "rejection notes"
            (is (<sub [::reject-note/show-note? doc-id]))
            (is (not (<sub [::reject-note/show-editor? doc-id])))
            (is (= "Kaikki ok" (<sub [::reject-note/reject-note doc-id])))

            (is (not (<sub [::reject-note/show-note? doc-id path])))
            (is (not (<sub [::reject-note/show-editor? doc-id path])))
            (is (nil? (<sub [::reject-note/reject-note doc-id path])))))

        (testing "authority rejects the group"
          (expect-action {:name    :reject-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :path       "someGroup"
                                    :collection :documents}
                          :success [::approval-common/update-approval.success
                                    {:approved? false
                                     :app-id    app-id
                                     :doc-id    doc-id
                                     :path      path}]
                          :resp    {:ok                true
                                    :documentApprovals {:_root     {:timestamp 1759227998159
                                                                    :value     "approved"
                                                                    :note      "Kaikki ok"
                                                                    :user      {:firstName "Veikko"
                                                                                :lastName  "Viranomainen"}
                                                                    ;; `changed` indicates that the original
                                                                    ;; approval is no longer valid
                                                                    :changed   1234}
                                                        :someGroup {:timestamp 1759227998159
                                                                    :value     "rejected"
                                                                    :user      {:firstName "Veikko"
                                                                                :lastName  "Viranomainen"}}}}})
          ;; Simulate button press
          (let [callback (approval-common/on-reject (util/mmap app-id doc-id path))]
            (callback))

          (testing "approval/rejection status"
            ;; document is now neutral
            (is (not (<sub [::approvals/approved? doc-id])))
            (is (not (<sub [::approvals/rejected? doc-id])))
            ;; group has been rejected
            (is (not (<sub [::approvals/approved? doc-id path])))
            (is (<sub [::approvals/rejected? doc-id path])))

          (testing "rejection notes"
            (is (<sub [::reject-note/show-note? doc-id]))
            (is (not (<sub [::reject-note/show-editor? doc-id])))
            (is (= "Kaikki ok" (<sub [::reject-note/reject-note doc-id])))

            ;; Editor shown for group but no note yet
            (is (not (<sub [::reject-note/show-note? doc-id path])))
            (is (<sub [::reject-note/show-editor? doc-id path]))
            (is (nil? (<sub [::reject-note/reject-note doc-id path]))))

          (testing "authority adds a rejection note to group using the editor"
            (let [note "some text"]
              (expect-action {:name    :reject-doc-note
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :path       "someGroup"
                                        :collection :documents
                                        :note       note}
                              :success [::reject-note/reject-doc-note.success
                                        {:doc-id doc-id
                                         :path   path
                                         :note   note}]
                              :resp    {:ok                true
                                        :documentApprovals {:_root     {:timestamp 1759227998159
                                                                        :value     "approved"
                                                                        :note      "Kaikki ok"
                                                                        :user      {:firstName "Veikko"
                                                                                    :lastName  "Viranomainen"}
                                                                        :changed   1234}
                                                            :someGroup {:timestamp 1759227998159
                                                                        :value     "rejected"
                                                                        :note      note
                                                                        :user      {:firstName "Veikko"
                                                                                    :lastName  "Viranomainen"}}}}})
              ;; Simulate blurring the editor
              (>evt [::reject-note/on-blur (util/mmap app-id doc-id path note)])

              (testing "rejection notes after writing note for group"
                (is (<sub [::reject-note/show-note? doc-id]))
                (is (not (<sub [::reject-note/show-editor? doc-id])))
                (is (= "Kaikki ok" (<sub [::reject-note/reject-note doc-id])))

                ;; Note shown for group
                (is (<sub [::reject-note/show-note? doc-id path]))
                (is (not (<sub [::reject-note/show-editor? doc-id path])))
                (is (= note (<sub [::reject-note/reject-note doc-id path])))))))))))
