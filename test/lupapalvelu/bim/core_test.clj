(ns lupapalvelu.bim.core-test
  (:require [clojure.test :as t]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.bim.bimmer.bimmer-to-lp :as btl]
            [lupapalvelu.bim.checking-plan.core :as bim-cp]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.job :as job]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.system.util-components :as components]
            [lupapiste-pubsub.protocol :as pubsub]
            [matcher-combinators.test]))

(def ^:private primary-operation-id "aaaa00000000000000000001")
(def ^:private secondary-operation-id "aaaa00000000000000000002")

(def ^:private test-application
  {:id                  "LP-753-2024-00001"
   :primaryOperation    {:id   primary-operation-id
                         :name "kerrostalo-rivitalo"}
   :secondaryOperations [{:id   secondary-operation-id
                          :name "laajentaminen"}]})

(defn- call-post-process-linked-file!
  "Invokes post-process-linked-file with a minimal linked-file targeting `operation-id`,
  with all side-effecting dependencies mocked out. Returns the schema-name args
  that op/get-operation-schema was called with."
  [operation-id]
  (let [schema-name-calls (atom [])
        sent-contexts     (atom [])]
    (with-redefs [domain/get-application-no-access-checking (fn [_] test-application)
                  app-utils/operation-by-id                 (fn [app op-id]
                                                              (->> (concat [(:primaryOperation app)]
                                                                           (:secondaryOperations app))
                                                                   (filter #(= op-id (:id %)))
                                                                   first))
                  op/get-operation-schema                   (fn [op-name app]
                                                              (swap! schema-name-calls conj op-name)
                                                              (case op-name
                                                                "kerrostalo-rivitalo" "uusiRakennus"
                                                                "laajentaminen"       "rakennuksen-laajentaminen"
                                                                nil))
                  linked-files/latest-version               (fn [_] {:bucket           "test-bucket"
                                                                     :file-version-id  "version-1"
                                                                     :metadata         {:size 1000}
                                                                     :target-file-path "path/to/model.ifc"})
                  gcs/actual-bucket-name                    (fn [b] b)
                  bim-cp/default-application-checking-plans (fn [& _] [])
                  org/check-org-flag-by-app-id              (fn [& _] false)
                  btl/assoc-ifc-data-query                  (fn [ctx]
                                                              (swap! sent-contexts conj ctx)
                                                              (assoc ctx :query {}))
                  job/start                                 (fn [_ subtasks]
                                                              {:id "job-1" :value subtasks})
                  pubsub/publish                            (fn [& _] nil)
                  components/config->actual-topic-name      (fn [_config topic-id]
                                                              (name topic-id))]
      (linked-files/post-process-linked-file
        {:target-entity {:application-id "LP-753-2024-00001"
                         :file-id        "file-abc"
                         :operation-id   operation-id
                         :target-type    "bim-model-file"}
         :metadata      {:model-type  "architectural"
                         :model-phase "design-time"}}
        {:config {}})
      {:schema-name-calls @schema-name-calls
       :sent-contexts     @sent-contexts})))

(t/deftest post-process-linked-file-schema-name-uses-linked-operation
  (t/testing "schema-name in base-context is derived from the linked operation, not primaryOperation"
    (t/is (match? {:schema-name-calls ["laajentaminen"]
                   :sent-contexts     [{:schema-name "rakennuksen-laajentaminen"}]}
                  (call-post-process-linked-file! secondary-operation-id))))

  (t/testing "schema-name is correct when the linked operation is the primary operation"
    (t/is (match? {:schema-name-calls ["kerrostalo-rivitalo"]
                   :sent-contexts     [{:schema-name "uusiRakennus"}]}
                  (call-post-process-linked-file! primary-operation-id)))))
