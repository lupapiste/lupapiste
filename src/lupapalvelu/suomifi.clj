(ns lupapalvelu.suomifi
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [sade.util :as util]
            [sade.env :as env]))

(def header-translations
  {:suomifi_nationalidentificationnumber                      :hetu
   :suomifi_cn                                                :fullName
   :suomifi_firstname                                         :firstName
   :suomifi_givenname                                         :givenName
   :suomifi_sn                                                :lastName
   :suomifi_mail                                              :email
   :suomifi_vakinainenkotimainenlahiosoites                   :streetAddress
   :suomifi_vakinainenkotimainenlahiosoitepostinumero         :postalCode
   :suomifi_vakinainenkotimainenLahiosoitepostitoimipaikkas   :city})

(env/in-dev
  (defpage "/from-shib/:command" {command :command}
    (let [headers (get-in (request/ring-request) [:headers])
          ; because HTTP headers are case insensitive
          headers (zipmap (map (comp keyword str/lower-case) (keys headers)) (vals headers))
          relevant-headers (select-keys headers (keys header-translations))
          user-data (util/map-keys header-translations relevant-headers)]
      (response/json {:command command
                      :user user-data
                      :allheaders headers}))))