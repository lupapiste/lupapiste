(ns lupapalvelu.bim.data-update-test
  (:require [clojure.test :as t]
            [lupapalvelu.bim.bim-extracted-data :as bim-extracted-data]
            [lupapalvelu.bim.data-update :as sut]
            [lupapalvelu.bim.protocol :as bim-proto]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.job :as job]
            [matcher-combinators.test]))

(defn- mock-extraction [properties]
  (reify bim-proto/BIMExtraction
    (-extraction-request [_ _] {})
    (-extraction-handler [_ _] nil)
    (-extraction-response [_ _] properties)))

(defn- make-snapshot-job-value [extra]
  (merge {:application-id                  "LP-123"
          :schema-name                     "uusiRakennuskohde"
          :operation-name                  "kerrostalo-rivitalo"
          :file-id                         "file-1"
          :object-key                      "path/to/file.ifc"
          :linked-metadata-file-version-id "version-abc"
          :query                           {}}
         extra))

(t/deftest store-ifc-data-snapshot!-nil-properties-sets-error-status
  (t/testing "when extraction response contains no properties, job subtask status is set to :error"
    (let [job-updates (atom [])
          instance    (mock-extraction nil)]
      (with-redefs [job/find-job     (fn [_] {:value {"ifc-data-snapshot" (make-snapshot-job-value {})}})
                    job/update-by-id (fn [_id subtask status-map]
                                       (swap! job-updates conj [subtask status-map]))]
        (sut/store-ifc-data-snapshot! instance {} {:response-to "job-1"})
        (t/is (match? [["ifc-data-snapshot" {:status :error}]]
                      @job-updates)))))

  (t/testing "when extraction response contains properties, job subtask status is set to :done"
    (let [job-updates (atom [])
          instance    (mock-extraction {"IfcBuilding" []})]
      (with-redefs [job/find-job                               (fn [_] {:value {"ifc-data-snapshot" (make-snapshot-job-value {})}})
                    job/update-by-id                           (fn [_id subtask status-map]
                                                                 (swap! job-updates conj [subtask status-map]))
                    schemas/get-schema                         (fn [_ _] {:info {:name "uusiRakennuskohde" :version 1} :body []})
                    bim-extracted-data/save-ifc-data-snapshot! (fn [_ _] nil)]
        (sut/store-ifc-data-snapshot! instance {} {:response-to "job-1"})
        (t/is (match? [["ifc-data-snapshot" {:status :done}]]
                      @job-updates))))))
