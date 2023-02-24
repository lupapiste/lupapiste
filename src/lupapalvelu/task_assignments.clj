(ns lupapalvelu.task-assignments
  (:require [lupapalvelu.assignment :as assi]
            [lupapalvelu.automatic-assignment.core :as automatic]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]))

(defn task-automatic-filters
  "Automatic assignment filters that match the given `taskname`. Nil on empty."
  [{:keys [organization] :as command} taskname]
  (when (some-> organization force :assignments-enabled)
    (not-empty (automatic/resolve-filters command :review-name taskname))))
