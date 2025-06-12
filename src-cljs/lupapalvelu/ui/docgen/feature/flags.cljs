(ns lupapalvelu.ui.docgen.feature.flags
  "Handle schema-flags, see `schema-flags-service.js`."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [details]}]]
  {:db (assoc db ::schema-flags (into #{} (map keyword) (:schemaFlags details)))})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::schema-flags)})

(rf/reg-sub ::schema-flags :-> ::schema-flags)

(defn include-schema?
  ([schema]
   (include-schema? schema (<sub [::schema-flags])))
  ([{:keys [schema-exclude schema-include] :as schema} flags]
   (and schema
        (or (not schema-include)
            (contains? flags (keyword schema-include)))
        (not (contains? flags (keyword schema-exclude))))))

(rf/reg-sub ::include-schema?
  :<- [::schema-flags]
  (fn [flags [_ schema]]
    (include-schema? schema flags)))
