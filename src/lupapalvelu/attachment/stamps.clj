(ns lupapalvelu.attachment.stamps
  (:require [lupapalvelu.attachment.stamp-schema :as stmpSc]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.user :as user]
            [sade.core :as score]
            [sade.date :as date]
            [sade.strings :as ss]
            [schema.core :as sc]))

(defn- add-section-mark [section-str]
  (when (string? section-str)
    (if (ss/contains? section-str "\u00a7")
      section-str
      (str \u00a7 " " section-str))))

(defn- tag-content [tag {:keys [verdict] :as context}]
  (let [value (case (keyword (:type tag))
                :current-date   (date/finnish-date (score/now) :zero-pad)
                :verdict-date   (date/finnish-date (or (vc/verdict-date verdict) (score/now))
                                                   :zero-pad)
                :backend-id     (vc/verdict-municipality-permit-id verdict)
                :user           (user/full-name (:user context))
                :organization   (get-in context [:organization :name :fi])
                :application-id (get-in context [:application :id])
                :building-id    (i18n/with-lang (or (get-in context [:user :language]) :fi)
                               (i18n/loc "stamp.building-id"))
                :section        (add-section-mark (vc/verdict-section verdict))
                (or (:text tag) ""))]
    {:type (:type tag) :value value}))

(defn- fill-rows [stamp context]
  (let [fill-tag (fn [tag] (tag-content tag context))
        fill-row (fn [row] (mapv fill-tag row))]
    (mapv fill-row (:rows stamp))))

(defn- fill-stamp [stamp context]
  (assoc stamp :rows (fill-rows stamp context)))

(defn stamps [organization application user]
  (let [organization-stamp-templates (:stamps organization)
        context                      {:organization organization
                                      :application  application
                                      :user         user
                                      :verdict      (vif/latest-published-verdict {:application application})}
        fill-with-context            (fn [stamp] (fill-stamp stamp context))]
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

(defn dissoc-tag-by-type [rows tag-type]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types tag-type)]}
  (->> rows
       (mapv (fn [row] (filterv #(not= (keyword tag-type) (keyword (:type %))) row)))
       (remove empty?)
       (into [])))

(defn- kw-row [row]
  (map #(assoc % :type (keyword (:type %))) row))

(defn non-empty-stamp-rows->vec-of-string-value-vecs [rows]
  (let [processed-rows (->> (map kw-row rows)
                            (map (fn [row] (remove #(ss/blank? (:value %)) row)))
                            (remove empty?))]

    (doseq [row processed-rows]
      (sc/validate stmpSc/StampRow row))
    (mapv (fn [row] (mapv :value row)) processed-rows)))

(defn assoc-tag-by-type [rows type value]
  {:pre [(map (fn [row] (sc/validate stmpSc/StampRow row)) rows)
         (contains? stmpSc/all-field-types type)]}
  (let [assoc-in-tag (fn [tag]
                       (if (= type (keyword (:type tag)))
                         (assoc tag :value value)
                         tag))
        assoc-in-row (fn [row] (mapv assoc-in-tag row))]
    (mapv assoc-in-row rows)))

(def default-stamp-data
  {:name "Oletusleima"
   :position {:x 10 :y 200}
   :background 0
   :page :first
   :qrCode true
   :rows [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type "current-date"}]
          [{:type :backend-id}]
          [{:type :section}]
          [{:type :user}]
          [{:type :building-id}]
          [{:type :organization}]
          [{:type :extra-text
            :text ""}]]})
