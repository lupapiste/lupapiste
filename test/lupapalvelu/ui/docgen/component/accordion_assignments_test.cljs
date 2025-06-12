(ns lupapalvelu.ui.docgen.component.accordion-assignments-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.component.accordion-assignments :as accordion-assignments]
            [lupapalvelu.ui.docgen.feature.assignments :as assignments]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]
            [sade.util :as util]))

(use-fixtures :once test-utils/js-services-fixture)

(def some-doc
  {:id     "some-doc"
   :schema {:info {:name "just-an-example"}
            :body []}
   :data   {}})

(def app (assoc test-utils/app :documents [some-doc]))
(def details test-utils/details)
(def app-id test-utils/app-id)
(def doc-id (:id some-doc))

(deftest accordion-assignments-test
  (rf-test/run-test-sync
    (let [{:keys [expect-hub-event handler]} (test-utils/make-mock-hub)
          assignment-id                      (str "assignment-" (random-uuid))]

      (rf/reg-fx :hub/send handler)

      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "feature provides no assignments yet"
        (is (empty? (<sub [::assignments/document-assignments])))
        (is (empty? (<sub [::assignments/document-assignments-ids doc-id]))))

      (let [description  (str "some description:" (random-uuid))
            recipient-id (str "recipient-" (random-uuid))
            recipient    {:id        recipient-id
                          :firstName "Veikko"
                          :lastName  "Vastaanottaja"}
            creator      {:id        "creator-1"
                          :firstName "Teuvo"
                          :lastName  "Tekijä"}
            target       {:group "documents"
                          :id    doc-id}
            assignment   {:id           assignment-id
                          :description  description
                          :createdState {:user creator}
                          :currentState {:type "created"}
                          :targets      [target]
                          :recipient    recipient}]
        (>evt [::assignments/new-assignments [assignment
                                              ;; assignment targeting some other doc -> ignore
                                              (-> assignment
                                                  (assoc :id (str (random-uuid)))
                                                  (assoc-in [:targets 0 :id] "other-doc"))
                                              ;; a completed assignment -> ignore
                                              (-> assignment
                                                  (assoc :id (str (random-uuid)))
                                                  (assoc-in [:currentState :type] "completed"))]])

        (testing "feature provides assignment data"
          (is (= [assignment-id] (<sub [::assignments/document-assignments-ids doc-id])))
          (is (= description (<sub [::assignments/description doc-id assignment-id])))
          (is (= recipient (<sub [::assignments/recipient doc-id assignment-id])))
          (is (= creator (<sub [::assignments/creator doc-id assignment-id])))
          (is (= target (<sub [::assignments/target doc-id assignment-id]))))

        (testing "editor UI"
          (is (= description (<sub [::accordion-assignments/current-description doc-id assignment-id])))
          (is (= recipient-id (<sub [::accordion-assignments/current-recipient-id doc-id assignment-id])))
          (is (<sub [::accordion-assignments/assignment-ok? doc-id assignment-id])))

        (testing "open editor"
          (is (false? (<sub [::accordion-assignments/editing? doc-id assignment-id])))
          (>evt [::accordion-assignments/toggle-editing doc-id assignment-id])
          (is (true? (<sub [::accordion-assignments/editing? doc-id assignment-id]))))

        (testing "clear description"
          (>evt [::accordion-assignments/description-edited doc-id assignment-id ""])

          (is (= "" (<sub [::accordion-assignments/current-description doc-id assignment-id])))
          (is (false? (<sub [::accordion-assignments/assignment-ok? doc-id assignment-id]))))

        (testing "clear recipient"
          (>evt [::accordion-assignments/recipient-id-edited doc-id assignment-id ""])

          (is (= "" (<sub [::accordion-assignments/current-recipient-id doc-id assignment-id])))
          (is (false? (<sub [::accordion-assignments/assignment-ok? doc-id assignment-id]))))

        (testing "set new description"
          (>evt [::accordion-assignments/description-edited doc-id assignment-id "new-description"])

          (is (= "new-description" (<sub [::accordion-assignments/current-description doc-id assignment-id])))
          (is (true? (<sub [::accordion-assignments/assignment-ok? doc-id assignment-id]))))

        (testing "save changes"
          (expect-hub-event {:event "assignmentService::saveAssignment"
                             :data  {:id           app-id
                                     :assignmentId assignment-id
                                     :recipientId  ""
                                     :targets      [target]
                                     :description  "new-description"}})
          (>evt [::accordion-assignments/save-assignment (util/mmap {:description  "new-description"
                                                                     :recipient-id ""}
                                                                    doc-id
                                                                    assignment-id
                                                                    target)])

          (is (false? (<sub [::accordion-assignments/editing? doc-id assignment-id]))))))))
