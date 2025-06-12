(ns lupapalvelu.admin.import-export.impl
  (:require [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.organization :as org]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.shared-schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defmulti -organization-query
  {:arglists '([mode target])}
  (fn [mode target]
    [mode target]))

(sc/defn organization-query :- {org/OrgId org/OrgName}
  "Query suitable targets for import/export for given target"
  {:always-validate true}
  [mode :- model/Modes
   target :- model/Targets]
  (-organization-query mode target))

(defmulti -item-query
  {:arglists '([target org-id item-ids])}
  (fn [target _ _]
    target))

(sc/defn item-query :- sc/Any
  "Query exportable items for organization"
  {:always-validate true}
  [target :- model/Targets
   org-id :- org/OrgId
   item-ids :- (sc/maybe #{ssc/NonBlankStr})]
  (-item-query target org-id item-ids))

(def ^:private datetime-fmt (date/pattern-formatter "YYYYMMdd_HHmmss"))

(defschema Export
  {:data     model/ImportFile
   :filename ssc/NonBlankStr})

(sc/defn produce-export :- Export
  "Produce data we can export as .json"
  {:always-validate true}
  [target :- model/Targets
   org-id :- org/OrgId
   item-ids :- (sc/cond-pre ssc/NonBlankStr
                            [ssc/NonBlankStr])]
  {:data     {:target target
              :org-id org-id
              :env    env/target-env
              :items  (item-query target org-id (if (string? item-ids)
                                                  #{item-ids} (set item-ids)))}
   :filename (format "export_%s_%s_%s.json"
                     (date/zoned-format (now) datetime-fmt)
                     (name target)
                     (name org-id))})

(defmulti -import!
  {:arglists '([target org-id document replace?])}
  (fn [target _ _ _]
    target))

(defschema Response
  (st/open-schema
    {:ok sc/Bool}))

(sc/defn import! :- Response
  "Run import"
  {:always-validate true}
  [target :- model/Targets
   org-id :- org/OrgId
   document :- model/ImportFile
   replace? :- sc/Bool]
  (-import! target org-id document replace?))
