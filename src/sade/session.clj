(ns sade.session)

(defn logout? [{:keys [session] :as response}]
  (and (contains? response :session) (nil? session)))

(defn merge-to-session
  "Merges map m to session, unless response session is marked for deletion.
   Returns response."
  [{request-session :session} {response-session :session :as response} m]
  (if (logout? response)
    response
    (assoc response :session (merge (or response-session request-session) m))))
