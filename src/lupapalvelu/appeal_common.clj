(ns lupapalvelu.appeal-common
  (:require [monger.operators :refer :all]
            [sade.core :refer :all]
            [schema.core :refer [defschema] :as sc]
            [lupapalvelu.action :as action]
            [lupapalvelu.attachment.appeal :as att-appeal]
            [lupapalvelu.attachment :as att]
            [sade.shared-schemas :as sssc]))

(defschema FrontendAppealFile
  "File presentation expected by the frontend from appeals query."
  {:fileId      sssc/FileId
   :filename    sc/Str
   :contentType sc/Str
   :size        sc/Num})

(defn delete-by-id
  "Deletes appeal/appealVerdict from Mongo. Also deletes the
  corresponding attachments."
  [{application :application :as command} appeal-id]
  (let [k (if (some #(= appeal-id (:id %)) (:appealVerdicts application))
            :appealVerdicts
            :appeals)
        removable-attachment-ids (map :id (att-appeal/appeals-attachments application [appeal-id]))]
    (att/delete-attachments! application removable-attachment-ids)
    (action/update-application command {$pull {k {:id appeal-id}}})))

(defn- id-list [vid xs]
  (->> xs (filter #(= vid (:target-verdict %))) (map :id)))

(defn delete-by-verdicts
  "Deletes every appeal/appealVerdict and their attachments for the
  given verdict ids."
  [{application :application :as command} verdict-ids]
  (let [appeal-ids               (mapcat #(id-list % (:appeals application)) verdict-ids)
        appeal-verdict-ids       (mapcat #(id-list % (:appealVerdicts application)) verdict-ids)
        removable-attachment-ids (map :id (att-appeal/appeals-attachments application (concat appeal-ids appeal-verdict-ids)))]
    (att/delete-attachments! application removable-attachment-ids)
    (action/update-application
     command
     {$pull {:appeals        {:id {$in appeal-ids}}
             :appealVerdicts {:id {$in appeal-verdict-ids}}}})))

(defn delete-by-verdict
  "Deletes every appeal/appealVerdict and their attachments for the
  given verdict id."
  [{application :application :as command} verdict-id]
  (delete-by-verdicts command [verdict-id]))
