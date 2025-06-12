(ns lupapalvelu.ui.entries.suomifi
  "Views used after Suomi.fi authentication before logging in"
  (:require [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.suomifi-login.root :as suomifi-login-root]))

(reload/register! :suomifi
                  [suomifi-login-root/mounter])
