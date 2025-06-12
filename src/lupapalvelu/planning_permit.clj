(ns lupapalvelu.planning-permit
  "Functions related to planning permits (sijoittamislupa) and creating execution permits from planning permits"
  (:require [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [monger.operators :refer [$set]]
            [sade.util :as util]))

(defn is-planning-permit? [application]
  (-> application
      :primaryOperation
      :name
      (op/get-operation-metadata :planning-permit)))

(defn- merge-op-document [document planning-permit]
  (let [permit-data      (->> planning-permit
                              :documents
                              (util/find-first #(= (doc/get-schema-name %) (:operation-name planning-permit)))
                              :data)
        permit-type      (-> permit-data :hankkeen-tyyppi :value keyword)
        clean-transition (-> permit-data permit-type :puhtaan-siirtyman-lupa)
        data-to-merge    (-> permit-data
                             permit-type
                             (merge {:puhtaan-siirtyman-lupa clean-transition})
                             (assoc-in [:show-clean-transition :value]
                                       (or (-> permit-data :show-clean-transition :value)
                                           (-> clean-transition :puhtaan-siirtyman-lupa-enabled :value))))]
    (assoc document :data data-to-merge)))

(defn merge-planning-permit-with-execution-permit [planning-permit created-application operation]
  (let [planning-permit-docs (map #(-> % :schema-info :name) (:documents planning-permit))
        op-schema-name       (op/get-operation-metadata operation :schema)]
    (-> created-application
        (update :documents
                (partial mapv
                         (fn [document]
                           (let [schema-name (doc/get-schema-name document)]
                             (cond
                               (= schema-name op-schema-name)             (merge-op-document document planning-permit)
                               (some #{schema-name} planning-permit-docs) (util/find-first
                                                                           #(= (doc/get-schema-name %) schema-name)
                                                                           (:documents planning-permit))
                               :else                                        document))))))))

(defn- clear-not-selected-planning-permit-fields
  "Planning permit type applications can have different 'subtypes' (hankkeen-tyyppi). This function clears all fields belonging to
  non selected 'subtypes' and returns the updated documents vector"
  [application]
  (mapv (fn [doc]
          (if (->> doc :schema-info :name (= "sijoittamislupa-v2"))
            (let [selected-permit-type (keyword (-> doc :data :hankkeen-tyyppi :value))
                  keys-to-clear        (filterv (partial not= selected-permit-type) [:rakennelma :rakennus :alue])]
              (reduce (fn [acc path]
                        (update-in acc [:data path]
                                   #(util/convert-values
                                     %
                                     (fn [k _] (= k :value))
                                     (fn [v] (cond
                                               (boolean? v) false
                                               (string? v)  "")))))
                      doc
                      keys-to-clear))
            doc))
        (:documents application)))

(defn clear-planning-permit-fields!
  "Planning permits can be of different types, this functions clears data from non-selected types"
  [{application :application :as command}]
  (if (is-planning-permit? application)
    (do (update-application command {$set {:documents (clear-not-selected-planning-permit-fields application)}})
        (mongo/by-id :applications (:id application)))
    application))
