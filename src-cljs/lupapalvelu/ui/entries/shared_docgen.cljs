(ns lupapalvelu.ui.entries.shared-docgen
  "Views used to render docgen views or individual fields."
  (:require [lupapalvelu.ui.application.consent :as consent]
            [lupapalvelu.ui.application.foreman-actions :as foreman-actions]
            [lupapalvelu.ui.application.foreman-list :as foreman-list]
            [lupapalvelu.ui.docgen.view :as docgen]
            [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.property-list.view]))

(reload/register! :shared-docgen
                  [consent/mounter
                   docgen/mounter
                   foreman-actions/mounter
                   foreman-list/mounter])
