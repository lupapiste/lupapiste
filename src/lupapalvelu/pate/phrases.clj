(ns lupapalvelu.pate.phrases
  (:require [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.util :as util]))

(defn valid-category
  "Input validator for category parameter."
  [{:keys [data]}]
  (when-not (util/includes-as-kw?
              (concat shared-schemas/phrase-categories
                      (->> (:custom-phrase-categories (org/get-organization (:org-id data)))
                           (keys)
                           (set)))
              (:category data))
    (fail :error.invalid-category)))

(defn phrase-id-exists [command]
  (when-not (util/find-by-id  (some-> command :data :phrase-id)
                              (:phrases (template/command->organization command)))
    (fail :error.phrase-not-found)))

(defn phrase-id-ok
  "Id either exists or is nil."
  [command]
  (when (some-> command :data :phrase-id)
    (phrase-id-exists command)))

(defn upsert-phrase [{:keys [data]}]
  (let [m                          (select-keys data [:category :tag :phrase])
        {:keys [phrase-id org-id]} data]
    (if phrase-id
      (mongo/update :organizations
                    {:_id     org-id
                     :phrases {$elemMatch {:id phrase-id}}}
                    {$set (util/map-keys #(util/kw-path :phrases.$ %) m)})
      (mongo/update-by-id :organizations
                          org-id
                          {$push {:phrases (assoc m :id (mongo/create-id))}}))))

(defn delete-phrase [org-id phrase-id]
  (mongo/update-by-id :organizations
                      org-id
                      {$pull {:phrases {:id phrase-id}}}))

(defn save-phrase-category [{:keys [data]}]
  (let [mongo-id (mongo/create-id)]
  (mongo/update-by-id :organizations
                      (:org-id data)
                      {$set {(util/kw-path :custom-phrase-categories mongo-id :fi) (get-in data [:categories :fi])
                             (util/kw-path :custom-phrase-categories mongo-id :sv) (get-in data [:categories :sv])
                             (util/kw-path :custom-phrase-categories mongo-id :en) (get-in data [:categories :en])}})))

(defn delete-phrase-category [org-id category-id]
  (mongo/update-by-id :organizations
                      org-id
                      {$unset {(util/kw-path :custom-phrase-categories category-id) 1}}))
