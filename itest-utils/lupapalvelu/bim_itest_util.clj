(ns lupapalvelu.bim-itest-util
  (:require [clojure.java.io :as io]
            [clojure.test :refer [is testing]]
            [lupapalvelu.bim.core :as bim]
            [lupapalvelu.crypto :as crypto]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest]
            [matcher-combinators.test :refer [match?]]
            [sade.strings :as ss])
  (:import [java.io File]
           [java.nio.file Files]))

(set! *warn-on-reflection* true)

(defn upload-bim
  "Helper function for uploading arbitrary files as-if they were BIM models using the `linked-files` API

  The uploaded file does not necessarily have to be a valid BIM file,
  we will mock the pubsub communication so it will not be sent to the Bimmer.

  Options:
  * `application-id` -- Application ID of the target application
  * `filename` -- Name of the file to upload. Should be on the classpath.
  * `model-phase` -- `submission-time`, `construction-time`,  or `completion-time`. Defaults to `submission-time`.
  * `model-type` -- `architectural`, `structural`, `location`, or `technical`. Defaults to `architectural`.
  * `operation-id` -- ID of the primary operation
  * `target-type` -- Target type, see `LinkedFileTargetTypes`. Defaults to `bim-model-file`.
  * `user` -- The user performing the upload"
  [{:keys [application-id filename model-phase model-type operation-id target-type user]
    :or   {target-type "bim-model-file"
           model-phase "submission-time"
           model-type  "architectural"}}]
  {:pre [(string? application-id)
         (string? operation-id)
         (string? filename)
         (string? target-type)]}

  (testing "Step 1/3: Ask for upload link"
    (let [^File file           (is (io/file (io/resource filename)))
          bytes                (is (-> file .toPath Files/readAllBytes))
          md5                  (is (crypto/calculate-md5-base64 bytes))
          {:keys [content-type
                  file-id
                  file-version-id
                  ok
                  upload-url]} (itest/command user :init-resumable-upload-for-application
                                              ;; NOTE: The `id` is only used for authorization,
                                              ;; the link is NOT tied to a particular application.
                                              ;; NOTE: The `filename` is only for resolving the content
                                              ;; type. It is not stored anywhere.
                                              {:id         application-id
                                               :filename   filename
                                               :md5-digest md5})]
      (is (true? ok))
      (is (not (ss/blank? file-id)))
      (is (not (ss/blank? file-version-id)))
      (is (not (ss/blank? upload-url)))
      (is (not (ss/blank? content-type)))

      (testing "Step 2/3: Upload file contents to GCS"
        (let [{:keys [status]} (itest/http-put upload-url {:throw-exceptions false
                                                           :content-type     content-type
                                                           :body             (io/input-stream bytes)})]
          (is (= 200 status))))

      (testing "Step 3/3: Link the uploaded file to an application"
        (let [*outgoing (atom [])]
          ;; We don't want to publish messages to the bimmer; instead, mock the publishing
          (with-redefs [bim/send-bim-job-pubsub (fn [{:keys [_request-fn
                                                             _handler-kw
                                                             _response-topic
                                                             _context
                                                             _job
                                                             _system]
                                                      :as   message}]
                                                  (swap! *outgoing conj (dissoc message :system))
                                                  nil)]
            (testing "perform linking"
              (let [{:keys [ok
                            result]} (itest/command user :link-resumable-upload-for-application
                                                    {:id                 application-id
                                                     :file-version-id    file-version-id
                                                     :linked-file-target {:target-type    target-type
                                                                          :file-id        file-id
                                                                          :application-id application-id
                                                                          :operation-id   operation-id}
                                                     :file-metadata      {:model-phase model-phase
                                                                          :model-type  model-type}
                                                     :version-metadata   {:content-type content-type
                                                                          :name         filename
                                                                          :size         (count bytes)}})]
                (is (true? ok))
                (is (= :added-doc-and-version (keyword result)))))

            (testing "verify outgoing pubsub"
              ;; NOTE: In addition to the snapshot and XTK conversion messages below,
              ;; the generated messages may also include messages related to the validation
              ;; and compliance checks (= if target org has validation or compliance plans).
              ;; Should you need to use this helper function in such a situation, you will need
              ;; to expand the checking below to handle those messages too.
              (let [[snapshot-message xkt-message :as outgoing] (deref *outgoing)]
                (is (= 2 (count outgoing)))
                (let [common-payload {:context    {:application-id application-id
                                                   :operation-id   operation-id
                                                   :file-id        file-id
                                                   :bucket         string?
                                                   :object-key     string?
                                                   :status         :initialized}
                                      :job        {:id      string?
                                                   :version 0
                                                   :status  :running
                                                   :value   {:xkt-generation {:application-id application-id
                                                                              :operation-id   operation-id
                                                                              :file-id        file-id
                                                                              :bucket         string?
                                                                              :object-key     string?
                                                                              :status         :initialized}}
                                                   :data    {:application-id   application-id
                                                             :target-file-path string?}}
                                      :request-fn fn?}]
                  (testing "verify IFC data snapshot -message"
                    (is (match? (merge common-payload
                                       {:handler-kw     :get-model-properties
                                        :response-topic (test-system/get-topic-name :bim/extraction-response)})
                                snapshot-message)))
                  (testing "verify BIM->XKT -message"
                    (is (match? (merge common-payload
                                       {:handler-kw     :generate-xkt-from-model
                                        :response-topic (test-system/get-topic-name :bim/ifc-to-xkt-response)})
                                xkt-message)))))))))

      (testing "verify linkage"
        ;; NOTE: the linked files have an `id` field, but none of the APIs calls above
        ;; return it. It seems the caller should just rely on the `target-entity` to
        ;; find relevant uploads?
        (let [{:keys [ok data]} (itest/query user :fetch-linked-file-metadatas-for-application
                                             :id application-id
                                             :target-type "bim-model-file")
              uploads           (->> data
                                     (filterv (fn [{:keys [target-entity]}]
                                                (= target-entity {:application-id application-id
                                                                  :file-id        file-id
                                                                  :operation-id   operation-id
                                                                  :target-type    target-type}))))]
          (is (true? ok))
          (is (= 1 (count uploads))))))))
