(ns lupapalvelu.ui.auth-admin.stamp.state
  (:require [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]
            [lupapalvelu.ui.common :refer [query]]))

(def empty-stamp {:name       ""
                  :id         nil
                  :position   {:x 100 :y 100}
                  :background 0
                  :page       :first
                  :qrCode     true
                  :rows       []})

(def empty-component-state {:stamps            []
                            :selected-stamp-id nil
                            :editor            {:drag-element    nil
                                                :closest-element []
                                                :stamp           {}}})

(defonce component-state (atom empty-component-state))

(defn update-stamp-view [id]
  (swap! component-state (fn [state] (-> state
                                         (assoc :selected-stamp-id id)
                                         (assoc-in [:editor :stamp] (stamp-util/find-by-id id (:stamps state)))))))

(defn refresh
  ([] (refresh nil))
  ([cb]
   (query :stamp-templates
          (fn [data]
            (swap! component-state assoc :stamps (:stamps data))
            (when cb (cb data))))))
