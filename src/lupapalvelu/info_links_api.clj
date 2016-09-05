(ns lupapalvelu.info-links-api
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.info-links :as info-links]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]))

(defn- application-link [lang role full-path]
  (str (env/value :host) "/app/" lang "/" (user/applicationpage-for role) "#!" full-path))


;;
;; API
;;

;; FIXME: saako käyttäjä päivittää itse?

(defcommand info-link-upsert
  {:description "Add or update application-specific info-link"
   :user-roles #{:authority :applicant}
   :org-authz-roles #{:authority}
   :states      states/all-states}
  [command]
  (println "info-link-upsert command " command)
  (ok "dummy"))

(defquery info-links
  {:description "Return a list of application-specific info-links"
   :parameters []
   :user-roles #{:authority :applicant}}
  [command]
  (let [app (:application command)]
     (ok (info-links/app-info-links app))))


