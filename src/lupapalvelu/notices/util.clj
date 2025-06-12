(ns lupapalvelu.notices.util
  (:require [lupapalvelu.operations :as ops]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.notice-utils :as notice-utils]
            [sade.core :refer [fail]]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(def resolution-key
  "The frontend should be able to access the notice resolution only through the specific
  endpoints."
  :_noticeResolution)

(defn resolution-kw-path
  "Kw-path with the `resolution-key`. For example,

  `(resolution-kw-path :hello :world)` ->  `:noticeResolution.hello.world`."
  [& args]
  (apply util/kw-path resolution-key args))

(defn notice?
  "True if the primary operation metadata has the `:notice` flag."
  [application]
  (boolean (some-> application ops/get-primary-operation-metadata
                   :flags :notice)))

(defn is-notice
  "Pre-checker that fails if the application IS NOT notice."
  [{:keys [application]}]
  (when-not (notice? application)
    (fail :error.not-notice-application)))

(defn is-not-notice
  "Pre-checker that fails if the application IS notice."
  [{:keys [application]}]
  (when (notice? application)
    (fail :error.notice-application)))

(defn draft-resolution
  "Pre-checker that fails if the application IS NOT notice or it has PUBLISHED resolution."
  [{:keys [application] :as command}]
  (or (is-notice command)
      (when (some-> application resolution-key :published)
        (fail :error.notice-resolution-published))))

(defn published-resolution
  "Pre-checker that fails if the application IS NOT notice or it has NOT PUBLISHED
  resolution."
  [{:keys [application] :as command}]
  (or (is-notice command)
      (when-not (some-> application resolution-key :published)
        (fail :error.notice-resolution-draft))))

(defn demolition-notice-document? [doc]
  (when-let [schema-name (get-in doc [:schema-info :name])]
    (let [schema (schemas/get-schema {:name schema-name})]
      (notice-utils/demolition-notice-document? {:schema schema}))))

(defn purkamisilmoitus-doc
  "Purkamisilmoitus document or nil if not found."
  [application-or-documents]
  (let [documents (:documents application-or-documents application-or-documents)]
    (util/find-first demolition-notice-document? documents)))

(defn building-demolition?
  "True if the `:buildingDemolition` is checked in the application purkamisilmoitus
  document."
  [application]
  (boolean (some->> application
                    purkamisilmoitus-doc
                    :data :buildingDemolition tools/unwrapped)))

(defn is-building-demolition
  "Pre-check that fails if the application IS NOT a notice with building demolition selected."
  [{:keys [application]}]
  (when-not (building-demolition? application)
    (fail :error.not-building-demolition)))
