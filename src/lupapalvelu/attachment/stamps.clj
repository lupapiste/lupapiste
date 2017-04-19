(ns lupapalvelu.attachment.stamps
  (:require [sade.core :as score]
            [sade.schemas :as ssc]
            [sade.util :as sutil]
            [schema.core :as sc]
            [lupapalvelu.building :as building]
            [lupapalvelu.user :as user]))

(def simple-tag-types
  #{:current-date
    :verdict-date
    :backend-id
    :username
    :organization
    :agreement-id
    :building-id})

(sc/defschema SimpleTagType
  (apply sc/enum simple-tag-types))

(sc/defschema SimpleTag
  {:type SimpleTagType})

(sc/defschema TextTag
  {:type :custom-text
   :text sc/Str})

(sc/defschema Tag
  (sc/conditional #(contains? simple-tag-types
                              (:type %))
                  SimpleTag
                  #(= :custom-text (:type %))
                  TextTag))

(sc/defschema StampTemplateRow
  [Tag])

(sc/defschema StampName (sc/pred string?))

(sc/defschema StampTemplate
  {:name       StampName
   :id         ssc/ObjectIdStr
   :position   {:x ssc/Nat
                :y ssc/Nat}
   :background ssc/Nat
   :page       (sc/enum :first
                        :last
                        :all)
   :qr-code    sc/Bool
   :rows       [StampTemplateRow]})

(defn- get-verdict-date [{:keys [verdicts]}]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (->> (map #(get-in % [:paivamaarat :anto]) paatokset)
                            (remove nil?)
                            (first))))
                (remove nil?)
                (first))]
    (sutil/to-local-date ts)))

(defn get-backend-id [verdicts]
  (->> verdicts
       (remove :draft)
       (some :kuntalupatunnus)))

(defn- tag-content [tag context]
  (let [value (case (keyword (:type tag))
                :current-date (sutil/to-local-date (score/now))
                :verdict-date (get-verdict-date (:application context))
                :backend-id (get-backend-id (get-in context [:application :verdicts]))
                :username (user/full-name (:user context))
                :organization (get-in context [:organization :name :fi])
                :agreement-id (get-in context [:application :id])
                :building-id (building/building-ids (:application context))
                (or (:text tag) ""))]
    {:type (:type tag) :value value}))

(defn- rows [stamp context]
  (mapv (fn [row] (mapv (fn [tag] (tag-content tag context)) row)) (:rows stamp)))

(defn- fill-stamp-tags [stamp context]
  (let [filled-rows (rows stamp context)]
    (assoc (dissoc stamp :rows) :rows filled-rows)))

(defn stamps [organization application user]
  (let [organization-stamp-templates (:stamps organization)
        context {:organization organization :application application :user user}]
    (map (fn [stamp] (fill-stamp-tags stamp context)) organization-stamp-templates)))
