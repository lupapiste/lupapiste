(ns lupapalvelu.docstore
  (:require [lupapalvelu.organization :as org]
            [lupapalvelu.quill :as quill]
            [lupapalvelu.shared-docstore :refer [docstore-languages
                                                 docstore-language?]]
            [monger.operators :refer :all]
            [sade.core :refer [fail]]
            [sade.util :as util]))

(defn valid-instructions
  "Input validator for `set-document-request-info`."
  [command]
  (some (fn [[lang value]]
          (if-not (docstore-language? lang)
            (fail :error.unsupported-language)
            (some-> value quill/check-operations fail)))
        (some-> command :data :instructions)))

(defn get-docstore-info-for-organization! [org-or-id]
  (:docstore-info (cond-> org-or-id
                    (string? org-or-id)
                    (org/get-organization [:docstore-info]))))

(defn- type-info [allowed-set attachment-type]
  {:type attachment-type
   :enabled (boolean (allowed-set attachment-type))})

(defn- populate-attachment-structure [allowed-types]
  (fn [[group types]]
    [group
     (mapv (partial type-info
                    (set allowed-types))
           types)]))

(defn- allowed-docterminal-attachment-types-for-organization
  "Returns a structure that contains all possible docterminal attachment types
  grouped by the attachment groups.

  [[<group name> [{:type <attachment type>
                   :enabled <is the type enabled for organization?>}
                  ...]
   ...]
   [<group name> [...]]]"
  [allowed-types]
  (->> org/allowed-attachments-by-group
       (mapv (populate-attachment-structure allowed-types))))

(defn allowed-docterminal-attachment-types [key org-id]
  (-> org-id
      get-docstore-info-for-organization!
      (get key)
      allowed-docterminal-attachment-types-for-organization))

(defn check-docstore-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  get-docstore-info-for-organization!
                  :docStoreInUse)
      (fail :error.docstore-not-enabled))))

(defn check-docterminal-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  get-docstore-info-for-organization!
                  :docTerminalInUse)
      (fail :error.docterminal-not-enabled))))

(defn check-docdepartmental-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  get-docstore-info-for-organization!
                  :docDepartmentalInUse)
      (fail :error.docdepartmental-not-enabled))))

(defn set-allowed-attachment-type
  [org-id attachment-type key allowed?]
  (if (= attachment-type "all")
    (if allowed?
      (org/update-organization org-id {$set {key org/allowed-attachments}})
      (org/update-organization org-id {$set {key []}}))
    (if allowed?
     (org/update-organization org-id {$addToSet {key attachment-type}})
     (org/update-organization org-id {$pull {key attachment-type}}))))

(defn set-allowed-docterminal-attachment-type
  [org-id attachment-type allowed?]
  (set-allowed-attachment-type org-id attachment-type :docstore-info.allowedTerminalAttachmentTypes allowed?))

(defn set-allowed-docdepartmental-attachment-type
  [org-id attachment-type allowed?]
  (set-allowed-attachment-type org-id attachment-type :docstore-info.allowedDepartmentalAttachmentTypes allowed?))

(defn read-instructions [instructions]
  (->> (select-keys instructions docstore-languages)
       (map (fn [[k v]]
              [k (util/pcond->> v
                   sequential? quill/inflate
                   string?     (assoc-in [{}] [0 :insert]))]))
       (into {})))

(defn- write-instructions [instructions]
  (->> (select-keys instructions docstore-languages)
       (map (fn [[k v]]
              [k (-> v quill/coerce-operations! quill/deflate)]))
       (into {})))

(defn document-request-info [org-or-id]
  (-> org-or-id
      get-docstore-info-for-organization!
      (get :documentRequest)
      (update :instructions read-instructions)))

(defn set-document-request-info
  [org-id enabled email instructions]
  (org/update-organization org-id
                           {$set {:docstore-info.documentRequest.enabled enabled
                                  :docstore-info.documentRequest.instructions (write-instructions instructions)
                                  :docstore-info.documentRequest.email email}}))
