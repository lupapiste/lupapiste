(ns lupapalvelu.conversion.import.shared
  "Shared definitions for various imports."
  (:require [lupapalvelu.logging :as log]
            [sade.strings :as ss]))

(defmulti import--items :op)

(defmethod import--items :default
  [cfg]
  (log/errorf "Import operation %s not supported." (:op cfg)))

(defn append-text-if-needed
  "Appends `s` to `old-text`, if it is not included already. Returns the updated trimmed
  text or nil. The default `separator` is two newlines."
  ([old-text s separator]
   (when-not (ss/blank? s)
     (when-not (ss/includes-i? old-text s)
       (ss/trim-join separator old-text s))))
  ([old-text s]
   (append-text-if-needed old-text s "\n\n")))
