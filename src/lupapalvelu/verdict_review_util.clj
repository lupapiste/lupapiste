(ns lupapalvelu.verdict-review-util
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [pandect.core :as pandect]
            [clojure.java.io :as io]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mime :as mime]
            [sade.common-reader :refer [to-timestamp]]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.attachment :as attachment])
  (:import (java.net URL)
           (java.nio.charset StandardCharsets)))

(defn verdict-attachment-type
  ([application] (verdict-attachment-type application "paatosote"))
  ([{permit-type :permitType :as application} type]
   (if (#{:P :R} (keyword permit-type))
     {:type-group "paatoksenteko" :type-id type}
     {:type-group "muut" :type-id type})))

(defn- attachment-type-from-krysp-type [type]
  (case (ss/lower-case type)
    "paatosote" "paatosote"
    "lupaehto" "muu"
    "paatos"))

(defn- content-disposition-filename
  "Extracts the filename from the Content-Disposition header of the
  given respones. Decodes string according to the Server information."
  [{headers :headers}]
  (when-let [raw-filename (some->> (get headers "content-disposition")
                                   (re-find #".*filename=\"?([^\"]+)")
                                   last)]
    (case (some-> (get headers "server") ss/trim ss/lower-case)
      "microsoft-iis/7.5" (-> raw-filename
                              (.getBytes StandardCharsets/ISO_8859_1)
                              (String. StandardCharsets/UTF_8))
      raw-filename)))

(defn get-poytakirja
  "At least outlier verdicts (KT) poytakirja can have multiple
  attachments. On the other hand, traditional (e.g., R) verdict
  poytakirja can only have one attachment."
  [application user timestamp {target-type :type verdict-id :id :as target} pk]
  (if-let [attachments (or (:liite pk) (:Liite pk))]
    (let [;; Attachments without link are ignored
          attachments (->> [attachments] flatten (filter #(-> % :linkkiliitteeseen ss/blank? false?)))
          ;; There is only one urlHash property in
          ;; poytakirja. If there are multiple attachments the
          ;; hash is verdict-id. This is the same approach as
          ;; used with manually entered verdicts.
          pk-urlhash (if (= (count attachments) 1)
                       (-> attachments first :linkkiliitteeseen pandect/sha1)
                       verdict-id)]
      (when-not (seq attachments)
        (warnf "no valid attachment links in poytakirja, %s-id: %s" target-type verdict-id))
      (doall
        (for [att  attachments
              :let [{url :linkkiliitteeseen attachment-time :muokkausHetki type :tyyppi description :kuvaus} att
                    java-url        (URL. (URL. "http://") url) ; LPK-2903 HTTP is given as default URL context, if protocol is not defined
                    url-filename    (-> java-url (.getPath) (ss/suffix "/"))
                    resp            (http/get (.toString java-url) :as :stream :throw-exceptions false)
                    header-filename (content-disposition-filename resp)
                    filename        (mime/sanitize-filename (or header-filename url-filename))
                    content-length  (util/->int (get-in resp [:headers "content-length"] 0))
                    urlhash         (pandect/sha1 (.toString java-url))
                    attachment-id      urlhash
                    attachment-type    (verdict-attachment-type application (attachment-type-from-krysp-type type))
                    contents           (or description (if (= type "lupaehto") "Lupaehto"))
                    target             (assoc target :urlHash pk-urlhash)
                    ;; Reload application from DB, attachments have changed
                    ;; if verdict has several attachments.
                    current-application (domain/get-application-as (:id application) user)]]
          ;; If the attachment-id, i.e., hash of the URL matches
          ;; any old attachment, a new version will be added
          (files/with-temp-file temp-file
                                (if (= 200 (:status resp))
                                  (with-open [in (:body resp)]
                                    ; Copy content to a temp file to keep the content close at hand
                                    ; during upload and conversion processing.
                                    (io/copy in temp-file)
                                    (attachment/upload-and-attach! {:application current-application :user user}
                                                                   {:attachment-id attachment-id
                                                                    :attachment-type attachment-type
                                                                    :contents contents
                                                                    :target target
                                                                    :required false
                                                                    :locked true
                                                                    :created (or (if (string? attachment-time)
                                                                                   (to-timestamp attachment-time)
                                                                                   attachment-time)
                                                                                 timestamp)
                                                                    :state :ok}
                                                                   {:filename filename
                                                                    :size content-length
                                                                    :content temp-file}))
                                  (error (str (:status resp) " - unable to download " url ": " resp))))))
      (-> pk (assoc :urlHash pk-urlhash) (dissoc :liite)))
    (do
      (warnf "no attachments ('liite' elements) in poytakirja, %s-id: %s" target-type verdict-id)
      pk)))
