(ns lupapalvelu.attachment.stamps
  (:require [sade.core :as score]
            [sade.util :as sutil]
            [schema.core :as sc]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as user]
            [lupapalvelu.attachment.stamp-schema :as stmpSc]))

(defn- first-non-nil [coll]
  (->> coll
       seq
       (remove nil?)
       first))

(defn- verdict->timestamp [verdict]
  (let [paatos->timestamps (fn [paatos]
                             (get-in paatos [:paivamaarat :anto]))]
    (->> verdict
        :paatokset
        (map paatos->timestamps)
        first-non-nil)))

(defn- get-verdict-date [{:keys [verdicts]}]
  (let [timestamp (->> verdicts
                       (map verdict->timestamp)
                       first-non-nil)]
    (sutil/to-local-date timestamp)))

(defn get-backend-id [verdicts]
  (->> verdicts
       (remove :draft)
       (some :kuntalupatunnus)))

(defn- tag-content [tag context]
  (let [value (case (keyword (:type tag))
                :current-date (sutil/to-local-date (score/now))
                :verdict-date (get-verdict-date (:application context))
                :backend-id (get-backend-id (get-in context [:application :verdicts]))
                :user (user/full-name (:user context))
                :organization (get-in context [:organization :name :fi])
                :application-id (get-in context [:application :id])
                :building-id (i18n/with-lang (or (get-in context [:user :language]) :fi)
                               (i18n/loc "stamp.building-id"))
                (or (:text tag) ""))]
    {:type (:type tag) :value value}))

(defn- fill-rows [stamp context]
  (let [fill-tag (fn [tag] (tag-content tag context))
        fill-row (fn [row] (mapv fill-tag row))]
    (mapv fill-row (:rows stamp))))

(defn- fill-stamp [stamp context]
  (update stamp :rows (fn [rows] (fill-rows stamp context))))

(defn stamps [organization application user]
  (let [organization-stamp-templates (:stamps organization)
        context {:organization organization
                 :application application :user user}
        fill-with-context (fn [stamp] (fill-stamp stamp context))]
    (map fill-with-context organization-stamp-templates)))

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

(defn- removev [pred coll]
  (filterv (complement pred) coll))

(defn dissoc-tag-by-type [rows type]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types type)]}
  (let [remove-tag? (fn [tag] (= type (:type tag)))
        remove-tags-from-row (fn [row] (removev remove-tag? row))]
    (->> rows
         (mapv remove-tags-from-row)
         (removev empty?))))

(defn stamp-rows->vec-of-string-value-vecs [rows]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)]}
  (mapv (fn [row] (mapv :value row)) rows))

(defn assoc-tag-by-type [rows type value]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types type)]}
  (let [assoc-in-tag (fn [tag]
                       (if (= type (keyword (:type tag)))
                         (assoc tag :value value)
                         tag))
        assoc-in-row (fn [row] (mapv assoc-in-tag row))]
    (mapv assoc-in-row rows)))
