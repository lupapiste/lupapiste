(ns lupapalvelu.ui.auth-admin.stamp.state
  (:require [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]))

(def empty-stamp   {:name     ""
                    :id       nil
                    :position {:x 100 :y 100}
                    :background 0
                    :page     :first
                    :qr-code  true
                    :rows     [{:fields []}
                               {:fields []}
                               {:fields []}]})

(def empty-component-state {:stamps []
                            :view {:bubble-visible false
                                   :selected-stamp-id nil}
                            :editor {:drag-element nil
                                     :closest-element []
                                     :stamp {}}})

(defonce component-state  (atom empty-component-state))

(defn update-stamp-view [id]
  (swap! component-state assoc-in [:view :selected-stamp-id] id))

(def selected-stamp (rum-util/derived-atom
                      [component-state]
                      (fn [state]
                        (or (-> (get-in state [:view :selected-stamp-id])
                                (stamp-util/find-by-id (:stamps state)))
                            empty-stamp))))
