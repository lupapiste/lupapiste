(ns lupapalvelu.backing-system.dynasty
  (:require [lupapalvelu.backing-system.schema :as schemas]
            [sade.shared-schemas :as ssc]
            [schema-tools.coerce :as stc]
            [schema.core :as sc]))

(def options
  "Public configurable Dynasty options.
   Note that we omit `:crypto-iv` here (and from the derived schemas!), as it is an internal field!"
  [{:option/name        :host
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.host
    :option/placeholder :auth-admin.integration.dynasty.host.placeholder}
   {:option/name        :auth-host
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.auth-host
    :option/placeholder :auth-admin.integration.dynasty.auth-host.placeholder}
   {:option/name   :client
    :option/type   :option/text
    :option/schema ssc/NonBlankStr
    :option/label  :auth-admin.integration.dynasty.client}
   {:option/name   :client-secret
    :option/type   :option/password
    :option/schema ssc/NonBlankStr
    :option/label  :auth-admin.integration.dynasty.client-secret}
   {:option/name        :organization
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.organization
    :option/placeholder :auth-admin.integration.dynasty.organization.placeholder}
   {:option/name        :owner
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.owner
    :option/placeholder :auth-admin.integration.dynasty.owner.placeholder}
   {:option/name        :case-base-id
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.case-base-id
    :option/placeholder :auth-admin.integration.dynasty.case-base-id.placeholder}
   {:option/name        :organization-name
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.organization-name
    :option/placeholder :auth-admin.integration.dynasty.organization-name.placeholder}
   {:option/name        :handler-id
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.handler-id
    :option/placeholder :auth-admin.integration.dynasty.handler-id.placeholder}
   {:option/name        :document-type
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.document-type
    :option/placeholder :auth-admin.integration.dynasty.document-type.placeholder}
   {:option/name        :document-phase
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.document-phase
    :option/placeholder :auth-admin.integration.dynasty.document-phase.placeholder}
   {:option/name        :document-subphase
    :option/type        :option/text
    :option/schema      sc/Str ; optional -> allow persisting ""
    :option/label       :auth-admin.integration.dynasty.document-subphase
    :option/placeholder :auth-admin.integration.dynasty.document-subphase.placeholder
    :option/optional?   true}
   {:option/name        :organization-hierarchy-id
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.organization-hierarchy-id
    :option/placeholder :auth-admin.integration.dynasty.organization-hierarchy-id.placeholder}
   {:option/name        :applicant-role
    :option/type        :option/text
    :option/schema      ssc/NonBlankStr
    :option/label       :auth-admin.integration.dynasty.applicant-role
    :option/placeholder :auth-admin.integration.dynasty.applicant-role.placeholder}])

(def plain-options
  "Options which can be stored as-is"
  (let [options (->> options (map :option/name) set)]
    (disj options :client-secret)))

(def schema (schemas/schema-with-options options))

(def checker (sc/checker schema))

(def coercer (stc/coercer
               ;; Mark all options as optional so that we can coerce partially
               ;; specified options in the UI (e.g. when adding new options later)
               (schemas/schema-with-options options true)
               stc/json-coercion-matcher))

(defmethod schemas/get-properties :dynasty
  [_]
  {:integration/name    :auth-admin.integration.dynasty
   :integration/options options
   :integration/schema  schema
   :integration/checker checker
   :integration/coercer coercer})
