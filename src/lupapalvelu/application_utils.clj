(ns lupapalvelu.application-utils
  (:require [sade.strings :as ss]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.i18n :as i18n]
            [sade.strings :as s]
            [lupapalvelu.user :as usr]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

;; Operations

(defn- normalize-operation-name [i18n-text]
  (when-let [lc (ss/lower-case i18n-text)]
    (-> lc
        (s/replace #"\p{Punct}" "")
        (s/replace #"\s{2,}"    " "))))

(def operation-index
  (reduce
    (fn [ops k]
      (let [localizations (map #(i18n/localize % "operations" (name k)) ["fi" "sv"])
            normalized (map normalize-operation-name localizations)]
        (conj ops {:op (name k) :locs (remove ss/blank? normalized)})))
    []
    (keys operations/operations)))

(defn operation-names [filter-search]
  (let [normalized (normalize-operation-name filter-search)]
    (map :op
         (filter
           (fn [{locs :locs}] (some (fn [i18n-text] (ss/contains? i18n-text normalized)) locs))
           operation-index))))

(defn with-application-kind [{:keys [permitSubtype infoRequest] :as app}]
  (assoc app :kind (cond
                     (not (ss/blank? permitSubtype)) (str "permitSubtype." permitSubtype)
                     infoRequest "applications.inforequest"
                     :else       "applications.application")))

(defn enrich-applications-with-organization-name [applications]
  (let [application-org-ids    (map (comp :organization first) (partition-by :organization applications))
        organization-name-map  (zipmap application-org-ids
                                       (map #(org/with-organization % org/get-organization-name)
                                            application-org-ids))]
    (map (fn [app] (assoc app :organizationName (get organization-name-map (:organization app)))) applications)))

(defn with-organization-name [{:keys [organization] :as app}]
  (assoc app :organizationName (org/with-organization organization org/get-organization-name)))

(defn location->object [application]
  (let [[x y] (:location application)]
    (assoc application :location {:x x :y y})))

(defn make-area-query
  ([areas user]
   (make-area-query areas user nil))
  ([areas user key-prefix]
   {:pre [(sequential? areas)]}
   (let [orgs (usr/organization-ids-by-roles user #{:authority :commenter :reader})
         orgs-with-areas (mongo/select :organizations {:_id {$in orgs} :areas-wgs84.features.id {$in areas}} [:areas-wgs84])
         features (flatten (map (comp :features :areas-wgs84) orgs-with-areas))
         selected-areas (set areas)
         filtered-features (filter (comp selected-areas :id) features)
         target-kw (if (keyword? key-prefix)
                     (keyword (str (name key-prefix) ".location-wgs84"))
                     :location-wgs84)]
     (when (seq filtered-features)
       {$or (map (fn [feature] {target-kw {$geoWithin {"$geometry" (:geometry feature)}}}) filtered-features)}))))