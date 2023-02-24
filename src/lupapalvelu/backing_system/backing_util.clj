(ns lupapalvelu.backing-system.backing-util
  (:require [lupapalvelu.attachment :as attachment]))

(defn get-bs-unsent?-function
  "Returns a function that checks if the given attachment's type and sent state are appropriate for the backing system"
  []
  (every-pred attachment/unsent? attachment/transmittable-to-krysp?))
