(ns lupapalvelu.ui.docgen.feature.command
  (:require [clojure.set :as set]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn update-doc-fx
  [{:keys [db]} [_ {:keys [app-id doc-id field-paths values on-success on-error on-timeout]}]]
  {:pre [(string? app-id) (string? doc-id)
         (vector? field-paths) (not-empty field-paths)
         (vector? values) (not-empty values)
         (some? on-success)]}
  (let [{update-cmd :update
         coll       :collection} (docs/get-document-commands db doc-id)
        updates                  (mapv (fn [p v] [(path/path->str p) v])
                                       field-paths
                                       values)]
    {:fx [[:action/command (util/assoc-when
                             {:name    update-cmd
                              :params  (-> {:id      app-id
                                            :doc     doc-id
                                            :updates updates}
                                           (util/assoc-when :collection coll))
                              :success on-success}
                             :error on-error
                             :timed-out on-timeout)]]}))

(rf/reg-event-fx ::update-doc
  update-doc-fx)

(defn update-doc-identifier-fx
  [_ [_ data]]
  {:pre [(:id data) (:doc-id data) (:identifier data) (:value data) (:on-success data)]}
  {:fx [[:action/command (util/assoc-when
                           {:name    :update-doc-identifier
                            :params  (-> (select-keys data [:id :doc-id :identifier :value])
                                         (set/rename-keys {:doc-id :doc}))
                            :success (:on-success data)}
                           :error (:on-error data)
                           :timed-out (:on-timeout data))]]})

(defn update-doc-approval-fx
  "Approve or reject the document."
  [{:keys [db]} [_{:keys [app-id doc-id path approved?] :as data}]]
  {:pre [(string? app-id) (string? doc-id) (boolean? approved?)
         (:on-success data)]}
  (let [{coll :collection} (docs/get-document-commands db doc-id)]
    {:fx [[:action/command (util/assoc-when
                             {:name    (if approved?
                                         :approve-doc
                                         :reject-doc)
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :path       (or path "")
                                        :collection coll}
                              :success (:on-success data)}
                             :error (:on-error data)
                             :timed-out (:on-timeout data))]]}))

(rf/reg-event-fx ::update-doc-approval
  update-doc-approval-fx)

(defn reject-doc-note-fx
  "Store the rejection note using `:reject-doc-note`."
  [{:keys [db]} [_{:keys [app-id doc-id path note] :as data}]]
  {:pre [(string? app-id) (string? doc-id) (string? note)]}
  (let [{coll :collection} (docs/get-document-commands db doc-id)]
    {:fx [[:action/command (util/assoc-when
                             {:name   :reject-doc-note
                              :params {:id         app-id
                                       :doc        doc-id
                                       :path       (or path "")
                                       :collection coll
                                       :note       note}}
                             :success (:on-success data)
                             :error (:on-error data)
                             :timed-out (:on-timeout data))]]}))

(rf/reg-event-fx ::reject-doc-note
  reject-doc-note-fx)

(defn remove-document-data-fx
  "Remove an element (e.g. a group) from the document"
  [{:keys [db]} [_ {:keys [app-id doc-id path] :as data}]]
  {:pre [(string? app-id) (string? doc-id) (vector? path)]}
  (let [{coll :collection} (docs/get-document-commands db doc-id)]
    {:fx [[:action/command (util/assoc-when
                             {:name   :remove-document-data
                              :params {:id         app-id
                                       :doc        doc-id
                                       :path       path
                                       ;; Not sure if `collection` is required anymore
                                       :collection coll}}
                             :success (:on-success data)
                             :error (:on-error data)
                             :timed-out (:on-timeout data))]]}))

(rf/reg-event-fx ::remove-document-data
  remove-document-data-fx)
