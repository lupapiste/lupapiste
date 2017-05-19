(ns lupapalvelu.attachment.stamps
  (:require [sade.core :as score]
            [sade.util :as sutil]
            [schema.core :as sc]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as user]
            [lupapalvelu.attachment.stamp-schema :as stmpSc]
            [sade.strings :as str]))

(defn- get-paatospvm [{:keys [verdicts]}]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (map (fn [pt] (map :paatospvm (:poytakirjat pt))) paatokset)))
                (flatten)
                (remove nil?)
                (sort)
                (last))]
    (sutil/to-local-date ts)))

(defn get-backend-id [verdicts]
  (->> verdicts
       (remove :draft)
       (some :kuntalupatunnus)))

(defn- get-section [{:keys [verdicts]}]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (map (fn [pt] (map :pykala (:poytakirjat pt))) paatokset)))
       (flatten)
       (map str)
       (remove str/blank?)
       (first)))

(defn- tag-content [tag context]
  (let [value (case (keyword (:type tag))
                :current-date (sutil/to-local-date (score/now))
                :verdict-date (get-paatospvm (:application context))
                :backend-id (get-backend-id (get-in context [:application :verdicts]))
                :user (user/full-name (:user context))
                :organization (get-in context [:organization :name :fi])
                :application-id (get-in context [:application :id])
                :building-id (i18n/with-lang (or (get-in context [:user :language]) :fi) (i18n/loc "stamp.building-id"))
                :section (get-section (:application context))
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

(defn value-by-type [rows type]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types type)]}
  (->> rows
       (map (fn [row] (filter #(= type (:type %)) row)))
       (flatten)
       (first)
       :value))

(defn row-value-by-type [stamp type]
  {:pre [(sc/validate stmpSc/Stamp stamp)
         (contains? stmpSc/all-field-types type)]}
  (value-by-type (:rows stamp) type))

(defn dissoc-tag-by-type [rows type]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types type)]}
  (->> rows
       (mapv (fn [rows] (filterv #(not (= type (:type %))) rows)))
       (remove empty?)
       (into [])))

(defn stamp-rows->vec-of-string-value-vecs [rows]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)]}
  (mapv (fn [row] (mapv :value row)) rows))

(defn assoc-tag-by-type [rows type value]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types type)]}
  (->> rows
       (mapv (fn [rows] (mapv (fn [row] (if (= type (keyword (:type row)))
                                            (assoc (dissoc row :value) :value value)
                                            row)) rows)))))
