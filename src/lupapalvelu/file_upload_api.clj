(ns lupapalvelu.file-upload-api
  (:require [noir.response :as resp]
            [clojure.set :refer [rename-keys]]
            [sade.core :refer :all]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.action :refer [defcommand defraw]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.vetuma :as vetuma]))

(def file-upload-max-size 100000000)

(defn- file-size-legal [{{files :files} :data}]
  (when-not (every? #(< % file-upload-max-size) (map :size files))
    (fail :error.file-upload.illegal-upload-size)))

(defn- file-mime-type-accepted [{{files :files} :data}]
  (when-not (every? mime/allowed-file? (map :filename files))
    (fail :error.file-upload.illegal-file-type)))

(defraw upload-file
  {:user-roles #{:anonymous}
   :parameters [files]
   :input-validators [file-mime-type-accepted file-size-legal]}
  (let [file-info (pmap
                    #(file-upload/save-file % :sessionId (vetuma/session-id) :linked false)
                    (map #(rename-keys % {:tempfile :content}) files))]
    (->> {:files file-info :ok true}
         (resp/json)
         (resp/content-type "text/plain")
         (resp/status 200))))

(defn- file-upload-in-database
  [{{attachment-id :attachmentId} :data}]
  (let [file-found? (mongo/any? :fs.files {:_id attachment-id "metadata.sessionId" (vetuma/session-id)})]
    (when-not file-found?
      (fail :error.file-upload.not-found))))

(defn- attachment-not-linked
  [{{attachment-id :attachmentId} :data}]
  (when-let [{{bulletin-id :bulletinId} :metadata} (mongo/select-one :fs.files {:_id attachment-id "metadata.sessionId" (vetuma/session-id)})]
    (when-not (empty? bulletin-id)
      (fail :error.file-upload.already-linked))))

(defcommand remove-uploaded-file
  {:parameters [attachmentId]
   :input-validators [file-upload-in-database attachment-not-linked]
   :user-roles #{:anonymous}}
  (if-let [{file-id :id} (mongo/select-one :fs.files {:_id attachmentId "metadata.sessionId" (vetuma/session-id)})]
    (do
      (mongo/delete-file-by-id file-id)
      (ok :attachmentId attachmentId))
    (fail :error.file-upload.removing-file-failed)))
