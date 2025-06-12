(ns lupapalvelu.remote-excel-reader
  (:require [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.storage.file-storage :as storage]
            [pantomime.mime :as mime]
            [schema.core :as sc])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(sc/defn ^:always-validate xls-to-rows :- [[sc/Str]]
  "Uploads the provided Excel tempfile (something readable by Apache POI) into GCS and asks laundry to process it.
   Returns a list of rows on the first sheet of the workbook. Each row is a list of strings representing column values."
  [{:keys [content-type tempfile filename]} :- {:content-type sc/Str
                                                :tempfile     (sc/pred #(instance? File %))
                                                :filename     sc/Str
                                                sc/Keyword    sc/Any}
   user-id :- sc/Str]
  (let [metadata                    {:uploader-user-id user-id}
        file-id                     (str (random-uuid))
        {:keys [bucket object-key]} (storage/upload file-id
                                                    filename
                                                    content-type
                                                    tempfile
                                                    metadata)]
    (try
      (laundry-client/read-spreadsheet bucket object-key)
      (finally
        (try
          (storage/delete-unlinked-file user-id file-id)
          (catch Throwable _))))))

(defn read-xlsx-rows-fast
  "Calls Laundry `read-sheet-rows-fast` endpoint for the contents of `file`. The return
  value is map with `:rows` and `:window1904` keys. The latter is true if the date
  calculation should use 1904 windowing. Each cell value is returned as string and the
  actual typing (e.g. number -> date) must be done on the client side."
  ([user-id ^File file filename content-type]
   (let [file-id              (str (random-uuid))
         {:keys [bucket
                 object-key]} (storage/upload file-id
                                              filename
                                              content-type
                                              file
                                              {:uploader-user-id user-id})]
     (try
       (laundry-client/read-spreadsheet-fast bucket object-key)
       (finally
         (try
           (storage/delete-unlinked-file user-id file-id)
           (catch Exception _))))))
  ([user-id ^File file filename]
   (read-xlsx-rows-fast user-id file filename (mime/mime-type-of filename)))
  ([user-id ^File file]
   (read-xlsx-rows-fast user-id file (.getName file))))
