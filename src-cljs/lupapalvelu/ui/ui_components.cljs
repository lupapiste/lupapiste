(ns lupapalvelu.ui.ui-components
  (:require [lupapalvelu.ui.inspection-summaries :as inspection-summaries]
            [lupapalvelu.ui.auth-admin.stamp.editor :as stamp-editor]
            [lupapalvelu.ui.matti.verdict-templates :as verdict-templates]
            [lupapalvelu.ui.matti.verdicts :as verdicts]
            [lupapalvelu.ui.printing-order.composer :as printing-order-composer]))

(defn reload-hook []

  (->> [inspection-summaries/mount-component
        stamp-editor/mount-component
        verdict-templates/mount-component
        verdicts/mount-component
        printing-order-composer/mount-component]

       (run! (fn [mount-fn]
               (try (mount-fn)
                    (catch js/Error e
                      (when-not (re-find #"(?i)Target container is not a DOM element" (aget e "message"))
                        (throw e))))))))
