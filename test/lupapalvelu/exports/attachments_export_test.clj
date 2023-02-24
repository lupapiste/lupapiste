(ns lupapalvelu.exports.attachments-export-test
  (:require [lupapalvelu.exports.attachments-export :as attachments-export]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import (org.apache.poi.xssf.usermodel XSSFSheet XSSFWorkbook)
           (java.io PipedInputStream)))

(testable-privates lupapalvelu.exports.attachments-export
  header-row
  body-row
  get-zip-filename)

(def test-version
  {:storageSystem "gcs"
   :fileId        "file-id-00"
   :filename      "tiedosto.pdf"})

(def test-attachment
  {:id            "facecafe99"
   :app-id        "LP-001"
   :org-id        "753-R"
   :contents      "Liitesisältö"
   :type          {:type-group "suunnitelmat"
                   :type-id    "piha_tai_istutussuunnitelma"}
   :latestVersion test-version
   :versions      [test-version]})

(def test-attachments
  (->> [test-attachment
        (assoc test-attachment :id "abcdef")
        (assoc test-attachment :id "fedcba" :app-id "LP-002")]
       (map #(update-in % [:latestVersion :filename] (partial get-zip-filename (:app-id %) (:id %))))))

(def test-apps
  (let [cleaned-attachments (->> test-attachments
                                 (map #(dissoc % :app-id :org-id))
                                 (map #(assoc-in % [:latestVersion :filename] "tiedosto.pdf")))]
    [{:id           "LP-001"
      :organization "753-R"
      :attachments  (take 2 cleaned-attachments)}
     {:id           "LP-002"
      :organization "753-R"
      :attachments  (drop 2 cleaned-attachments)}]))

(facts "generate-metadata-excel"
  (fact "Localizations work correctly"
    (i18n/with-lang "fi" (header-row)) => ["LP-tunnus" "Sisältö" "Liiteryhmä" "Liitteen tyyppi" "Tiedosto"]
    (i18n/with-lang "sv" (header-row)) => ["LP-nummer" "Innehåll" "Bilagegrupp" "Bilagans typ" "Fil"])

  (fact "Attachment metadata rows are generated correctly"
    (i18n/with-lang "fi" (body-row test-attachment))
    => ["LP-001" "Liitesisältö" "Suunnitelmat" "Piha- tai istutussuunnitelma" "tiedosto.pdf"])

  (fact "Metadata Excel generation works as expected"
    (let [wb  (attachments-export/generate-metadata-excel "fi" test-attachments)
          sh  (.getSheetAt wb 0)]
      (type wb) => XSSFWorkbook
      (type sh) => XSSFSheet
      (-> sh (.getRow 0) (.getCell 3) (.getStringCellValue)) => "Liitteen tyyppi"
      (-> sh (.getRow 1) (.getCell 0) (.getStringCellValue)) => "LP-001"
      (-> sh (.getRow 2) (.getCell 4) (.getStringCellValue)) => "LP-001_abcdef_tiedosto.pdf"
      (-> sh (.getRow 3) (.getCell 0) (.getStringCellValue)) => "LP-002")))

(facts "attachments-zip-stream"
  (fact "ZIP stream works"
    (with-open [zip-stream (attachments-export/attachments-zip-stream "fi" test-attachments)]
      (type zip-stream) => PipedInputStream
      (empty? (.readAllBytes zip-stream)) => false)))

(facts "get-attachments"
  (fact "attachment data is enriched properly"
    (attachments-export/get-attachments "753-R") => test-attachments
    (provided
      (mongo/select :applications anything anything) => test-apps)))

