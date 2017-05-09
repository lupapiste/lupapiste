(ns lupapalvelu.ui.auth-admin.stamp.state
  (:require [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]))

(def empty-stamp   {:name     ""
                    :position {:x 0 :y 0}
                    :page     :first
                    :qr-code  true
                    :rows     [[]]})

(def empty-component-state {:stamps []
                            :view {:bubble-visible false
                                   :selected-stamp-id nil}
                            :editor {:drag-element nil
                                     :closest-element []
                                     :debug-data {}
                                     :rows [{:fields [{:type :application-id}
                                                      {:type :backend-id}]
                                             :is-dragged-over false}
                                            {:fields [{:type :custom-text
                                                       :text "Hiphei, laitoin tähän tekstiä"}
                                                      {:type :current-date}]
                                             :is-dragged-over false}
                                            {:fields []
                                             :is-dragged-over false}]}})

(defonce component-state  (atom empty-component-state))

(defn update-stamp-view [id]
  (swap! component-state assoc-in [:view :selected-stamp-id] id))

(def selected-stamp (rum-util/derived-atom
                      [component-state]
                      (fn [state]
                        (or (-> (get-in state [:view :selected-stamp-id])
                                (stamp-util/find-by-id (:stamps state)))
                            empty-stamp))))
