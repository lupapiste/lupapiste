(ns lupapalvelu.ui.docgen.feature.whitelist
  (:require [lupapalvelu.ui.user :as user]))

(defn- missing-requirements?
  [{:keys [roles permitType]} current-permit-type current-role]
  (or
    ;; if the users role (wrt. to the application) doesn't match what's required
    (and (seq roles)
         (not (contains? (set roles) current-role)))
    ;; or the permit type doesn't match what's required
    (and (seq permitType)
         (not (contains? (set permitType) current-permit-type)))))

(defn schema-hidden?
  "Hide the schema according to the whitelist requirement."
  [{:keys [whitelist]} permit-type]
  (when-let [{:keys [otherwise]} whitelist]
    (and (= "hidden" otherwise)
         (missing-requirements? whitelist
                                permit-type
                                (some-> (user/current-user)
                                        (.applicationRole))))))

(defn schema-disabled?
  "Disable the schema according to the whitelist requirement."
  ;; NOTE: Backend calculates the `:whitelist-action`, which we already check
  ;; in the `:lupapalvelu.ui.docgen.feature.documents/field-whitelist-action`.
  ;; It is unclear if we ever actually need to check this whitelist disabled case.
  [{:keys [whitelist]} permit-type]
  (when-let [{:keys [otherwise]} whitelist]
    (and (= "disabled" otherwise)
         (missing-requirements? whitelist
                                permit-type
                                (some-> (user/current-user)
                                        (.applicationRole))))))
