(ns lupapalvelu.ui.docgen.test-utils
  "Helper functions and data for cljs tests"
  (:require [clojure.test :refer [is]]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.docgen.feature.assignments :as feature-assignments]
            [lupapalvelu.ui.docgen.feature.attachments :as feature-attachments]
            [sade.strings :as ss]))

(defn- dispatch-callback
  [[event & event-args] resp]
  (>evt (into [event resp] event-args)))

(defn- enrich-update-doc-response
  "The `update-doc` command will return a vector of updates made to the database.
  This function will enrich the response with the updates given as params when calling the :update-doc command"
  [name resp {{updates :updates} :params }]
  (if (and (= name :update-doc)
           (not (:updates resp))
           (seq updates))
    (assoc resp :updates (mapv (fn [[path value]]
                                 [(ss/split path #"\.") value])
                               updates))
    resp))

(defn make-mock-command
  "Returns functions for mocking `:action/command` effects."
  []
  (let [*state (atom #queue [])]
    {;; Queue an expected action
     :expect-action (fn mock-expect-action [val]
                      (swap! *state conj val))
     ;; Handle action and compare against expected value
     :handler       (fn mock-handler [{:keys [success error] :as args}]
                      (if-let [{:keys [resp name] :as expected} (ffirst (swap-vals! *state pop))]
                        (when (is (= (select-keys expected [:name :params :success :error])
                                     (select-keys args [:name :params :success :error])))
                          (if (:ok resp)
                            (dispatch-callback success (enrich-update-doc-response name resp args))
                            (dispatch-callback error resp)))
                        (is (nil? args) "unexpected call")))}))

(defn make-mock-hub
  "Returns functions for mocking `:hub/send` effects."
  []
  (let [*state (atom #queue [])]
    {;; Queue an expected events
     :expect-hub-event (fn mock-expect-event [val]
                         (swap! *state conj val))
     ;; Handle event and compare against expected value
     :handler          (fn mock-handler [args]
                         (if-let [{:keys [] :as expected} (ffirst (swap-vals! *state pop))]
                           (is (= (select-keys expected [:event :data])
                                  (select-keys args [:event :data])))
                           (is (nil? args) "unexpected call")))}))

;; JS services mock

(defn js-services-fixture
  "Temporary solution to handle docgen features that still call the old
  JS services."
  [f]
  (with-redefs [feature-attachments/initialize-attachments-subscription (fn [])
                feature-assignments/initialize-assignments-subscription (fn [])
                auth/application-auth?                                  (constantly true)]
    (f)))

;;; Sample data

(def app-id "LP-753-2025-90000")

(def app {:auth             []
          :documents        []
          :facta-imported   false
          :id               app-id
          :permitType       "R"
          :primaryOperation {:name                   "kerrostalo-rt-laaj"
                             :description            nil
                             :created                1752127675023
                             :id                     "686f58bb8c151b221da9d54b"
                             :attachment-op-selector true
                             :optional               ["hakijan-asiamies"]}})

(def details {:schemaFlags ["rakval-223"
                            "rakval-224"
                            "rakval-226"
                            "yht-2111"
                            "yht-219"]})
