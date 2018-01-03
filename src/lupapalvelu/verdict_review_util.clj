(ns lupapalvelu.verdict-review-util
  (:require [clojure.java.io :as io]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mime :as mime]
            [pandect.core :as pandect]
            [sade.common-reader :refer [to-timestamp]]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :as sc]
            [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.organization :as organization])
  (:import (java.net URL)
           (java.nio.charset StandardCharsets)))

(defn verdict-attachment-type
  "Function name is anachronistic. Currently also review attachment
  types are supported."
  ([application] (verdict-attachment-type application "paatosote"))
  ([{permit-type :permitType :as application} type-id]
   (let [resolved (reduce-kv (fn [acc k v]
                               (assoc acc k (name v)))
                             {}
                             (att-type/resolve-type permit-type type-id))]
     (cond
       (seq resolved)
       resolved

       (util/includes-as-kw? [:R :P :ARK] permit-type)
       {:type-group "paatoksenteko" :type-id type-id}

       :else
       {:type-group "muut" :type-id type-id}))))

(defmulti attachment-type-from-krysp-type
  (fn [{target-type :type} _] (keyword target-type)))

(defmethod attachment-type-from-krysp-type :default [{target-type :type} _]
  (errorf "Unknown krysp attachment target type: %s" target-type)
  "muu")

(defmethod attachment-type-from-krysp-type :verdict [_ type]
  (case (-> type ss/lower-case ss/scandics->ascii)
    "paatosote"  "paatosote"
    "lupaehto"   "muu"
    "paatos"))

(def task-attachment-types (set (->> (mapcat val att-type/attachment-types-by-permit-type)
                                     (filter (comp #{:katselmukset_ja_tarkastukset} :type-group))
                                     (map (comp name :type-id)))))

(defmethod attachment-type-from-krysp-type :task [_ type]
  (-> type
      ss/lower-case
      ss/scandics->ascii
      task-attachment-types
      (or "katselmuksen_tai_tarkastuksen_poytakirja")))

(defn- content-disposition-filename
  "Extracts the filename from the Content-Disposition header of the
  given response. Decodes string according to the Server information."
  [{headers :headers}]
  (when-let [raw-filename (some->> (get headers "content-disposition")
                                   (re-find #".*filename=\"?([^\"]+)")
                                   last)]
    (case (some-> (get headers "server") ss/trim ss/lower-case)
      "microsoft-iis/7.5" (-> raw-filename
                              (.getBytes StandardCharsets/ISO_8859_1)
                              (String. StandardCharsets/UTF_8))
      raw-filename)))

(defn- poytakirja-info-for-assignments [{:keys [user application attachment-id target]}]
  (let [targets [{:id attachment-id :trigger-type target}]]
    {:user             user
     :organization     (organization/get-organization (:organization application))
     :application      (domain/get-application-as (:id application) user)
     :targets          targets
     :assignment-group "attachments"
     :timestamp        (sc/now)}))

(defn- run-assignment-triggers-for-poytakirja [user application attachment-id attachment-type]
  (try
    ((assignment/run-assignment-triggers poytakirja-info-for-assignments)
      {:user          user
       :application   application
       :attachment-id attachment-id
       :target        (ss/join "." (vals attachment-type))})
    (catch Exception e
      (error "could not create assignment automatically for fetched attachment "
             (:id application) ": "(.getMessage e)))))

(defn download-and-store-poytakirja! [application user timestamp pk-urlhash {target-type :type verdict-id :id :as target} set-app-modified? att]
  (let [{url :linkkiliitteeseen attachment-time :muokkausHetki type :tyyppi description :kuvaus} att
        java-url        (URL. (URL. "http://") url) ; LPK-2903 HTTP is given as default URL context, if protocol is not defined
        url-filename    (-> java-url (.getPath) (ss/suffix "/"))
        resp            (http/get (.toString java-url) :as :stream :throw-exceptions false)
        header-filename (content-disposition-filename resp)
        filename        (mime/sanitize-filename (or header-filename url-filename))
        content-length  (util/->int (get-in resp [:headers "content-length"] 0))
        urlhash         (pandect/sha1 (.toString java-url))
        attachment-id      urlhash
        attachment-type    (verdict-attachment-type application (attachment-type-from-krysp-type target type))
        contents           (or description (if (= type "lupaehto") "Lupaehto"))
        target             (assoc target :urlHash pk-urlhash)
        ;; Reload application from DB, attachments have changed
        ;; if verdict has several attachments.
        current-application (domain/get-application-as (:id application) user)]
    ;; If the attachment-id, i.e., hash of the URL matches
    ;; any old attachment, a new version will be added
    (when (= content-length 0)
      (errorf "attachment link %s in poytakirja refers to an empty file, %s-id: %s"
              (.toString java-url) target-type verdict-id))
    (files/with-temp-file temp-file
                          (if (= 200 (:status resp))
                            (with-open [in (:body resp)]
                              ;; Copy content to a temp file to keep the content close at hand
                              ;; during upload and conversion processing.
                              (io/copy in temp-file)
                              (let [attachment-opts {:attachment-id attachment-id
                                                     :attachment-type attachment-type
                                                     :contents contents
                                                     :target target
                                                     :required false
                                                     :read-only true
                                                     :locked true
                                                     :created (or (if (string? attachment-time)
                                                                    (to-timestamp attachment-time)
                                                                    attachment-time)
                                                                  timestamp)
                                                     :state :ok
                                                     :set-app-modified? set-app-modified?}
                                    upload-result (attachment/upload-and-attach! {:application current-application :user user}
                                                                                 attachment-opts
                                                                                 {:filename filename
                                                                                  :size content-length
                                                                                  :content temp-file})]
                                (if upload-result
                                  (do
                                    (run-assignment-triggers-for-poytakirja user application urlhash attachment-type)
                                    1)
                                  0)))
                            (do
                              (error (str (:status resp) " - unable to download " url ": " resp))
                              0)))))

(defn get-poytakirja!
  "Fetches the verdict attachments listed in the verdict xml. If the
  fetch is successful, uploads and attaches them to the
  application. Returns pk (with urlHash assoced if upload and attach
  was successful).

  At least outlier verdicts (KT) poytakirja can have
  multiple attachments. On the other hand, traditional (e.g., R)
  verdict poytakirja can only have one attachment."
  [application user timestamp {target-type :type verdict-id :id :as target} pk
   & {:keys [set-app-modified?] :or {set-app-modified? true}}]
  (if-let [attachments (or (:liite pk) (:Liite pk))]
    (let [;; Attachments without link are ignored
          attachments (->> [attachments] flatten (filter #(-> % :linkkiliitteeseen ss/blank? false?)))
          ;; There is only one urlHash property in
          ;; poytakirja. If there are multiple attachments the
          ;; hash is verdict-id. This is the same approach as
          ;; used with manually entered verdicts.
          pk-urlhash (if (= (count attachments) 1)
                       (-> attachments first :linkkiliitteeseen pandect/sha1)
                       verdict-id)
          stored-files-count (reduce
                               (fn [acc att]
                                 (+ acc (download-and-store-poytakirja! application user timestamp pk-urlhash target set-app-modified? att)))
                               0 attachments)]
      (when-not (seq attachments)
        (warnf "no valid attachment links in poytakirja, %s-id: %s" target-type verdict-id))
      (cond-> pk
        true (dissoc :liite)
        (pos? stored-files-count) (assoc :urlHash pk-urlhash)))
    (do
      (warnf "no attachments ('liite' elements) in poytakirja, %s-id: %s" target-type verdict-id)
      pk)))
