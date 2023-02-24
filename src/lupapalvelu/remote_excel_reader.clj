(ns lupapalvelu.remote-excel-reader
  (:require [clj-uuid :as uuid]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.storage.file-storage :as storage]
            [schema.core :as sc])
  (:import [java.io File]))

(sc/defn ^:always-validate xls-to-rows :- [[sc/Str]]
  "Uploads the provided Excel tempfile (something readable by Apache POI) into GCS and asks laundry to process it.
   Returns a list of rows on the first sheet of the workbook. Each row is a list of strings representing column values."
  [{:keys [content-type tempfile filename]} :- {:content-type sc/Str
                                                :tempfile     (sc/pred #(instance? File %))
                                                :filename     sc/Str
                                                sc/Keyword    sc/Any}
   user-id :- sc/Str]
  (let [metadata {:uploader-user-id user-id}
        file-id  (str (uuid/v1))
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
