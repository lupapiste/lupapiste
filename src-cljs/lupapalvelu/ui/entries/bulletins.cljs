(ns lupapalvelu.ui.entries.bulletins
  "Views used in the application bulletin view(s)"
  (:require [lupapalvelu.ui.bulletins.bulletin-preamble :as bulletin-preamble]
            [lupapalvelu.ui.bulletins.local-bulletins :as local-bulletins]
            [lupapalvelu.ui.entries.dev-reload :as reload]))

(reload/register! :bulletins
                  [bulletin-preamble/mounter
                   local-bulletins/mounter])
