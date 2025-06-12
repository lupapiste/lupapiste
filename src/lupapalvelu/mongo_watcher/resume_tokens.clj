(ns lupapalvelu.mongo-watcher.resume-tokens
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongo-watcher.convert :as mw-convert]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [org.bson BsonDocument BsonString]))

(set! *warn-on-reflection* true)

(def ^:private token-document-id "mongo-change-stream")

(defn create-resume-tokens-document!
  "Creates a document for storing change stream resume tokens if one does not exist."
  []
  (when-not (mongo/any? :admin-config {:_id token-document-id})
    (mongo/insert :admin-config {:_id           token-document-id
                                 :resume-tokens {}})))

(defn store
  "Stores the resume token for the given collection.

  Each collection can have its own resume token, allowing change stream watchers
  to resume from the correct point after restarts or failures.

  Resume token IS NOT stored for collection :admin-config to prevent infinite
  loop where watcher is triggered by updating the resume-token.
  "
  [collection-name resume-token]
  (when-not (= collection-name :admin-config)
    (mongo/update-by-id :admin-config token-document-id
      {"$set" {(util/kw-path :resume-tokens collection-name) resume-token}})))

(defn fetch
  "Fetches the resume token for the given collection"
  [collection-name]
  (some-> (mongo/by-id :admin-config token-document-id)
          :resume-tokens
          (get (keyword collection-name))
          ss/blank-as-nil))

(defn resume-token->str [resume-token]
  (some-> resume-token (get "_data") mw-convert/from-bson))

(defn str->resume-token [str]
  (doto (BsonDocument.)
    (.append "_data" (BsonString. str))))
