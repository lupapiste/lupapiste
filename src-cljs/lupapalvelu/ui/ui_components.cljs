(ns lupapalvelu.ui.ui-components
  (:require [lupapalvelu.ui.components]
            [lupapalvelu.ui.inspection-summaries :as inspection-summaries]
            [lupapalvelu.ui.auth-admin.stamp.editor :as stamp-editor]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-view :as edit-authority-view]
            [lupapalvelu.ui.pate.verdict-templates :as verdict-templates]
            [lupapalvelu.ui.pate.verdicts :as verdicts]
            [lupapalvelu.ui.pate.verdict :as verdict]
            [lupapalvelu.ui.printing-order.composer :as printing-order-composer]
            [lupapalvelu.ui.bulletins.bulletin-preamble :as bulletin-preamble]
            [lupapalvelu.ui.bulletins.local-bulletins :as local-bulletins]
            [lupapalvelu.ui.company.reports :as company-reports]
            [lupapalvelu.ui.admin.users.create-authority-user]))

(defn reload-hook []

  (->> [inspection-summaries/mount-component
        stamp-editor/mount-component
        verdict-templates/mount-component
        verdicts/mount-component
        verdict/mount-component
        printing-order-composer/mount-component
        local-bulletins/mount-component
        bulletin-preamble/mount-component
        company-reports/mount-component
        edit-authority-view/mount-component]

       (run! (fn [mount-fn]
               (try (mount-fn)
                    (catch js/Error e
                      (when-not (re-find #"(?i)Target container is not a DOM element" (aget e "message"))
                        (throw e))))))))
