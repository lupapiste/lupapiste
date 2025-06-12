(ns lupapalvelu.supplement-request
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.quill :as quill]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

(def supplement-request-states
  [:draft :requested :replied :done])

(defschema SupplementRequest
  "Schema for a supplement request."
  (st/merge {:id          ssc/ObjectIdStr
             :state       (apply sc/enum supplement-request-states)
             :due-date    ssc/Timestamp
             :text        [quill/QuillOp]
             :modified    ssc/Timestamp
             :modified-by usr/SummaryUser}
            (st/optional-keys {:replied                ssc/Timestamp
                               :replied-by             usr/SummaryUser
                               :due-date-reminder-sent ssc/Timestamp})))

(def ^:private valid-supplement-request! (sc/validator SupplementRequest))

(defn create-supplement-request [modified user due-date text]
  (valid-supplement-request!
    (util/strip-nils
      {:id          (mongo/create-id)
       :state       :draft
       :due-date    due-date
       :text        text
       :modified    modified
       :modified-by (usr/summary user)})))

(defn update-supplement-request [request modified user due-date text]
  (valid-supplement-request!
    (merge request
           {:due-date    due-date
            :text        text
            :modified    modified
            :modified-by (usr/summary user)})))

(defn send-supplement-request [request requested user]
  (valid-supplement-request!
    (merge request
           {:state       :requested
            :modified    requested
            :modified-by (usr/summary user)})))

(defn withdraw-supplement-request [request modified user]
  (valid-supplement-request!
    (merge request
           {:state       :draft
            :modified    modified
            :modified-by (usr/summary user)})))

(defn reply-supplement-request [request replied user]
  (valid-supplement-request!
    (merge request
           {:state      :replied
            :replied    replied
            :replied-by (usr/summary user)})))

(defn mark-supplement-request-done [request marked user]
  (valid-supplement-request!
    (merge request
           {:state       :done
            :modified    marked
            :modified-by (usr/summary user)})))
