(ns lupapalvelu.ui.docgen.view-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

(def doc-1 {:id     "doc-1"
            :schema {:info {}
                     :body [{:name "foo"}]}
            :data   {:foo {:value ""}}})

(def app (assoc test-utils/app :documents [doc-1]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(defn- dispatch-callback
  [[event & event-args] resp]
  (>evt (into [event resp] event-args)))

(deftest app-loaded-test
  (rf-test/run-test-sync

    (rf/reg-fx :action/command
      (fn [{:keys [_name params success _error]}]
        ;; mock success
        (let [updates (map (fn [[path value]] [[path] value])
                           (:updates params))]
          (dispatch-callback success {:ok true :updates updates}))))

    ;; Initialize feature(s)
    (>evt [::docgen/app-loaded {:app     app
                                :details details}])

    (testing "commands added to document automatically"
      (is (= (assoc doc-1
                    ::docs/commands {:update     :update-doc
                                     :remove     :remove-document-data
                                     :collection :documents})
             (<sub [::docs/document "doc-1"]))))

    (let [doc-id (:id doc-1)
          path   [:foo]]

      (let [backend-value (<sub [::committed/backend-submitted-value doc-id path])]
        (is (nil? backend-value)))

      (let [on-change (docgen-common/make-on-change-immediate {:app-id app-id
                                                               :doc-id doc-id
                                                               :path   path})]
        (testing "when user submits a value"
          (on-change "some new value")
          (let [backend-value (<sub [::committed/backend-submitted-value doc-id path])]
            (is (= "some new value" backend-value))))))))
