(ns lupapalvelu.appeal-common
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]
            [sade.util :as util]
            [lupapalvelu.action :as action]))

(defschema FrontendAppealFile
  "File presentation expected by the frontend from appeals query."
  {:fileId      ssc/ObjectIdStr
   :filename    sc/Str
   :contentType sc/Str
   :size        sc/Num})

(defn delete-by-id
  "Deletes appeal/appealVerdict from Mongo. Also deletes the
  corresponding attachments."
  [{application :application :as command} appeal-id]
  (let [k (if (some #(= appeal-id (:id %)) (:appealVerdicts application))
            :appealVerdicts
            :appeals)]
    (action/update-application
     command
     {$pull {k           {:id appeal-id}
             :attachments {:target.id appeal-id}}})))

(defn- id-list [vid xs]
  (->> xs (filter #(= vid (:target-verdict %))) (map :id)))

(defn delete-by-verdict
  "Deletes every appeal/appealVerdict and their attachments for the
  given verdict."
  [{application :application :as command} verdict-id]
  (let [appeal-ids         (id-list verdict-id (:appeals application) )
        appeal-verdict-ids (id-list verdict-id (:appealVerdicts application))]
    (action/update-application
     command
     {$pull {:appeals        {:id {$in appeal-ids}}
             :appealVerdicts {:id {$in appeal-verdict-ids}}
             :attachments    {:target.id {$in (concat appeal-ids
                                                      appeal-verdict-ids)}}}})))

(defn delete-all
  "Deletes every appeal/appealVerdict and their attachments for the
  application."
  [{application :application :as command}]
  (action/update-application
     command
     {$set {:appeals        []
            :appealVerdicts []}
      $pull {:attachments {:target.type {$in [:appeal :appealVerdict]}}}}))
