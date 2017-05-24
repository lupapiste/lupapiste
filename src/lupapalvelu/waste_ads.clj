(ns lupapalvelu.waste-ads
  (:require [monger.operators :refer :all]
            [cheshire.core :as json]
            [hiccup.core :as hiccup]
            [clj-rss.core :as rss]
            [sade.strings :as ss]
            [sade.util :refer [fn->>]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.waste-schemas :as waste-schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]))

(defmulti waste-ads (fn [org-id & [fmt lang]] fmt))

(defn- waste-ads-from-mongo [org-id]
  "Get ads that contain construction waste reports, have some materials available and something
   in the contact field. Returns ads that have only relevant fields."
  (mongo/select
    :applications
    {:organization (if (ss/blank? org-id)
                     {$exists true}
                     org-id)
     :documents    {$elemMatch {:schema-info.name        {$in waste-schemas/construction-waste-report-schemas}
                                :data.availableMaterials {$exists true}
                                :data.contact            {$nin ["" nil]}}}
     :state        {$nin ["draft" "open" "canceled"]}}
    {:documents.schema-info.name        1
     :documents.data.contact            1
     :documents.data.availableMaterials 1
     :municipality                      1}))

(defn- construction-waste-report? [doc]
  (-> doc :schema-info :name waste-schemas/construction-waste-report-schemas))

(defn max-modified
  "Returns the max (latest) modified value of the given document part
  or list of parts."
  [m]
  (cond
    (:modified m) (:modified m)
    (map? m) (max-modified (vals m))
    (sequential? m) (apply max (map max-modified (cons 0 m)))
    :default 0))

(defn- get-materials-info [municipality {data :data :as doc}]
  (let [{contact :contact materials :availableMaterials} (-> (select-keys data [:contact :availableMaterials])
                                                             tools/unwrapped)]
    {:contact      contact
     ;; Material and amount information are mandatory. If the information
     ;; is not present, the row is not included.
     :materials    (->> (tools/rows-to-list materials)
                        (filter (fn->> ((juxt :aines :maara)) (not-any? ss/blank?))))
     :modified     (max-modified data)
     :municipality municipality}))

(defn- create-materials-contact-modified-municipality-map [{:keys [municipality documents]}]
  (->>
    documents
    (filter construction-waste-report?)
    (map #(get-materials-info municipality %))))

(defn- valid-materials-info? [{{:keys [name phone email]} :contact
                               materials                  :materials}]
  (and (ss/not-blank? name)
       (or (ss/not-blank? phone) (ss/not-blank? email))
       (not-empty materials)))

(def max-number-of-ads 100)

(defmethod waste-ads :default [org-id & _]
  (->>
    ;; 1. Every application that maybe has available materials.
    (waste-ads-from-mongo org-id)
    ;; 2. Create materials, contact, modified, municipality map.
    (mapcat create-materials-contact-modified-municipality-map)
    ;; 3. We only check the contact validity. Name and either phone or email
    ;;    must have been provided and (filtered) materials list cannot be empty.
    (filter valid-materials-info?)
    ;; 4. Sorted in the descending modification time order.
    (sort-by (comp - :modified))
    ;; 5. Cap the size of the final list
    (take max-number-of-ads)))


;; Constructing RSS-feed items

(defmethod waste-ads :rss [org-id _ lang]
  (let [ads (waste-ads org-id)
        columns (map :name waste-schemas/availableMaterialsRow)
        loc (fn [prefix term] (if (ss/blank? term)
                                term
                                (i18n/with-lang lang (i18n/loc (str prefix term)))))
        col-value (fn [col-key col-data]
                    (let [k (keyword col-key)
                          v (k col-data)]
                      (case k
                        :yksikko (loc "jateyksikko." v)
                        v)))
        col-row-map (fn [fun]
                      (->> columns (map fun) (concat [:tr]) vec))
        items (for [{:keys [contact materials municipality]} ads
                    :let [{:keys [name phone email]} contact
                          municipality-text (i18n/with-lang lang (i18n/loc "municipality" municipality))
                          html (hiccup/html [:div [:span (ss/join " " [name phone email municipality-text])]
                                             [:table
                                              (col-row-map #(vec [:th (loc "available-materials." %)]))
                                              (for [m materials]
                                                (col-row-map #(vec [:td (col-value % m)])))]])]]
                {:title       "Lupapiste"
                 :link        "http://www.lupapiste.fi"
                 :author      name
                 :description (str "<![CDATA[ " html " ]]>")})]
    (rss/channel-xml {:title (str "Lupapiste:" (i18n/with-lang lang (i18n/loc "available-materials.contact")))
                      :link  "" :description ""}
                     items)))

(defmethod waste-ads :json [org-id & _]
  (json/generate-string (waste-ads org-id)))
