(ns lupapalvelu.attachment.unzip-collection
  (:require [clojure.set :as set]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.logging :as log]
            [lupapalvelu.storage.file-storage :as storage]
            [sade.core :refer [fail?]]
            [schema-tools.core :as st]
            [schema.core :as sc])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

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
  (log/debug "Sending zip file" (.getName zip-file) "to laundry for processing")
  (let [file-id (str (random-uuid))]
    (try
      (let [metadata {:uploader-user-id user-id}
            file-id  (str (random-uuid))
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
            (log/info "Successfully extracted" (count attachments) "attachments from zip file")
            {:attachments attachments})

          (= status "ok")
          (log/info "No attachments found inside the zip file")

          :else
          (do
            (log/warn "Laundry failed to process the zip file:" error)
            (if (fail? result)
              (set/rename-keys result {:text :error})
              {:error error}))))
      (catch Exception ex
        (log/error ex)
        {:error "error.unzipping-error"})
      (finally
        (try
          (storage/delete-unlinked-file user-id file-id)
          (catch Throwable _))))))
