(ns lupapalvelu.suomifi
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [sade.util :as util]
            [hiccup.form :as form]
            [hiccup.core :as core]
            [sade.env :as env]))

(def header-translations
  {:suomifi_nationalidentificationnumber                      :personId
   :suomifi_cn                                                :fullName
   :suomifi_firstname                                         :firstName
   :suomifi_givenname                                         :givenName
   :suomifi_sn                                                :lastName
   :suomifi_mail                                              :email
   :suomifi_vakinainenkotimainenlahiosoites                   :streetAddress
   :suomifi_vakinainenkotimainenlahiosoitepostinumero         :postalCode
   :suomifi_vakinainenkotimainenLahiosoitepostitoimipaikkas   :city})

(defpage "/from-shib/:command" {command :command}
  (let [headers (get-in (request/ring-request) [:headers])
        ; because HTTP headers are case insensitive
        headers (zipmap (map (comp keyword str/lower-case) (keys headers)) (vals headers))
        relevant-headers (select-keys headers (keys header-translations))
        user-data (util/map-keys header-translations relevant-headers)]
    (response/json {:command command
                    :user user-data})))

(env/in-dev
  (defpage [:get "/dev/saml-login"] []
    (core/html [:html
                [:head [:title "SAML Dev Login"]]
                [:body {:style "background-color: #ab0000; color: white; padding: 4em"}
                 [:h3 "SAML Dev Login"]
                 [:div
                  (form/form-to {} [:post "/dev/saml-login"]
                    [:p "Etunimi: " (form/text-field :firstName)]
                    [:p "Sukunimi: " (form/text-field :lastName)]
                    [:p "Hetu: " (form/text-field :personId)]
                    [:p (form/submit-button {:id "btn"} "Lähetä")])]]]))

  (defpage [:post "/dev/saml-login"] []
    (let [request (request/ring-request)
          form-params (:form-params request)
          session (:session request)]
     (response/json {:session session
                     :data form-params}))))
