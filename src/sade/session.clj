(ns sade.session
  (:require [lupapalvelu.mongo :as mongo]
            [lupapiste-commons.ring.mongo-session :as mongo-session]
            [monger.operators :refer [$set]]))

(defn logout? [{:keys [session] :as response}]
  (and (contains? response :session) (nil? session)))

(defn merge-to-session
  "Merges map m to session, unless response session is marked for deletion.
   Returns response.

   When :rotate-session? in options map is true in m, sets Ring's :recreate
   metadata on the session, which causes the session store to issue a new
   session ID while preserving the data.

   Session rotation is performed to prevent session fixation attacks:
   https://owasp.org/www-community/attacks/Session_fixation

   A session should be rotated whenever a user's permissions change.
   Unnecessary session rotation is not inherently harmful, but it increases
   load on the database."
  [{request-session :session} {response-session :session :as response} m & {:keys [rotate-session?]}]
  (if (logout? response)
    response
    (cond-> (assoc response :session (merge (or response-session request-session) m))
            rotate-session? (update :session vary-meta assoc :recreate true))))

(defn session-store []
  (mongo-session/make-store
    (reify mongo-session/MongoAdapter
      (-get-session [_ id fields]
        (dissoc (mongo/by-id :sessions id fields) :id))
      (-update-session! [_ id session]
        (mongo/update-by-id :sessions id {$set session}))
      (-insert-session! [_ id session]
        (mongo/insert :sessions (assoc session :id id)))
      (-remove-session! [_ id]
        (mongo/remove-by-id :sessions id)))))

(defn delete-sessions-for-user!
  "Deletes all active sessions for the given user ID."
  [user-id]
  (when user-id
    (mongo/remove-many :sessions {:user-id user-id})
    nil))

(defn delete-other-sessions-for-user!
  "Deletes all active sessions for the given user ID except the session identified
   by current-session-data-id, which is the application-level :id stored in the
   session data map (set by session-id-to-mdc middleware, accessible via (:id session)
   in command handlers)."
  [user-id current-session-data-id]
  (when (and user-id current-session-data-id)
    (mongo/remove-many :sessions {:user-id user-id
                                  :data.id {"$ne" current-session-data-id}})))
