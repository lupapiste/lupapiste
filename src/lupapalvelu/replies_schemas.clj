(ns lupapalvelu.replies-schemas
  (:require
   [lupapalvelu.user :as usr]
   [sade.schemas :as ssc]
   [schema-tools.core :as st]
   [schema.core :refer [defschema] :as sc]))

(defschema ReplyIssue
  {:id                                        ssc/ObjectIdStr
   :issue-type                                (sc/enum "statement" "bulletin" "neighbor")})

(defschema ReplyAnswer
  {:nothing-to-add            sc/Bool
   (sc/optional-key :created) ssc/Timestamp
   (sc/optional-key :user)    usr/SummaryUser
   (sc/optional-key :message) (sc/maybe sc/Str)})

(defschema Reply
  "Reply"
  (st/merge ssc/SheriffNotes
            {:id                              (sc/cond-pre ssc/ObjectIdStr (sc/enum "new"))
             :application-id                  ssc/ApplicationId
             (sc/optional-key :creator)       usr/SummaryUser
             (sc/optional-key :reply-created) ssc/Timestamp
             :reply-due-date                  ssc/Timestamp
             :related-issues                  [ReplyIssue]
             :message                         (sc/maybe sc/Str)
             (sc/optional-key :answer)        ReplyAnswer}))

(defschema LegacyReply
  "Old replies that are migrated to the new implementation
  do not always contain a reply-due-date"
  (st/optional-keys Reply [:reply-due-date]))

