(ns lupapalvelu.ui.auth-admin.backing-system.sftp
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.backing-system.common :as common]
            [lupapalvelu.ui.auth-admin.backing-system.components :as components]
            [lupapalvelu.ui.common :refer [loc]]))

(def permit-type->versions (js->clj js/LUPAPISTE.config.asianhallintaVersions))

;; Custom edit component as we need to find the correct version choices per permit type

(defmethod common/edit-option :option/sftp-version
  [{:option/keys [name label]} id]
  (let [{:keys [version]} (<sub [::common/options id])
        permit-type       (first (<sub [::common/current-targets-or-default id]))]
    [components/select-with-label
     {:id            (str "option-sftp-version-" name)
      :label-text    (loc label)
      :allow-empty?  true
      :value         (or version "")
      :options       (get permit-type->versions permit-type)
      :option->text  identity
      :option->value identity
      :on-change     (fn [version]
                       (>evt [::common/option-changed id name version]))}]))
