(ns lupapalvelu.attachment.version
  (:require [schema.core :refer [defschema] :as sc]
            [lupapalvelu.user :as usr]))

(defschema VersionNumber
  {:major sc/Int
   :minor sc/Int})

(defn next-attachment-version [{:keys [major minor] :or {major 0 minor 0}} user]
  (if (usr/authority? user)
    {:major major :minor (inc minor)}
    {:major (inc major) :minor 0}))
