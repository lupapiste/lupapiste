(ns lupapalvelu.ui.entries.shared-lupapiste
  "Views used by logged in Lupapiste users (admin, auth admin, authority, applicant..)"
  (:require [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.mypage.sso :as mypage-sso]))

(reload/register! :shared-lp
                  [mypage-sso/mounter])
