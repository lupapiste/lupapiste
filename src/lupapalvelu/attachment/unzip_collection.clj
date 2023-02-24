(ns lupapalvelu.attachment.unzip-collection
  (:require [clj-uuid :as uuid]
            [clojure.set :as set]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [sade.core :refer [fail?]]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [taoensso.timbre :as timbre])
  (:import [java.io File]))


(def column-keyword-map
  {"tiedosto"       :filename
   "tiedostonimi"   :filename
   "tyyppi"         :localizedType
   "sisältö"        :contents
   "piirrosnumero"  :drawingNumber
   "liittyy"        :operation
   "fil"            :filename
   "filnamn"        :filename
   "typ"            :localizedType
   "innehåll"       :contents
   "ritningsnummer" :drawingNumber
   "ansluter sig"   :operation})


(def UnzipResponse (st/optional-keys {:attachments [(st/merge {:filename   sc/Str
                                                               :object-key sc/Str
                                                               :mime       sc/Str
                                                               :size       sc/Int}
                                                              (st/optional-keys (->> column-keyword-map
                                                                                     vals
                                                                                     (map (fn [k]
                                                                                            [k sc/Str]))
                                                                                     (into {}))))]
                                      :error       sc/Str}))


(sc/defn unzip-attachment-collection :- UnzipResponse
  [^File zip-file
   user-id :- sc/Str]
  (timbre/debug "Sending zip file" (.getName zip-file) "to laundry for processing")
  (let [file-id (str (uuid/v1))]
    (try
      (let [metadata {:uploader-user-id user-id}
            file-id  (str (uuid/v1))
            {:keys [bucket object-key]} (storage/upload file-id
                                                        (.getName zip-file)
                                                        "application/zip"
                                                        zip-file
                                                        metadata)
            {:keys [status attachments error] :as result} (laundry-client/unzip-attachments bucket
                                                                                            object-key
                                                                                            column-keyword-map)]
        (cond
          (and (= status "ok") (seq attachments))
          (do
            (timbre/info "Successfully extracted" (count attachments) "attachments from zip file")
            {:attachments attachments})

          (= status "ok")
          (timbre/info "No attachments found inside the zip file")

          :else
          (do
            (timbre/warn "Laundry failed to process the zip file:" error)
            (if (fail? result)
              (set/rename-keys result {:text :error})
              {:error error}))))
      (catch Exception ex
        (timbre/error ex)
        {:error "error.unzipping-error"})
      (finally
        (try
          (storage/delete-unlinked-file user-id file-id)
          (catch Throwable _))))))
