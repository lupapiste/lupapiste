(ns lupapalvelu.ident.suomifi
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [sade.util :as util]
            [hiccup.form :as form]
            [hiccup.core :as core]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]))

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(defn- generate-trid [] (apply str (repeatedly 20 #(rand-int 10))))

(def header-translations
  {:suomifi-nationalidentificationnumber                      :personId
   :suomifi-cn                                                :fullName
   :suomifi-firstname                                         :firstName
   :suomifi-givenname                                         :givenName
   :suomifi-sn                                                :lastName
   :suomifi-mail                                              :email
   :suomifi-vakinainenkotimainenlahiosoites                   :streetAddress
   :suomifi-vakinainenkotimainenlahiosoitepostinumero         :postalCode
   :suomifi-vakinainenkotimainenLahiosoitepostitoimipaikkas   :city})

(defpage "/from-shib/login/:trid" {trid :trid}
  (let [headers          (get-in (request/ring-request) [:headers])
        ; because HTTP headers are case insensitive
        headers          (zipmap (map (comp keyword str/lower-case) (keys headers)) (vals headers))
        relevant-headers (select-keys headers (keys header-translations))
        ident            (util/map-keys header-translations relevant-headers)]
    (info ident)
    (mongo/update-one-and-return :ident {:sessionid (session-id) :trid trid} {$set {:user ident}})
    (response/json {:trid trid
                    :user (lupapalvelu.ident.session/get-user trid)
                    :ident ident})))

(env/in-dev
  (defpage [:get "/dev/saml/init-login"] {:keys [success error cancel]}
    (let [sessionid (session-id)
          trid      (generate-trid)
          paths     {:success success
                     :error   error
                     :cancel  cancel}]
     (mongo/update :ident {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (java.util.Date.)} :upsert true)
     (response/json {:trid trid})))
  (defpage [:get "/dev/saml-login"] {:keys [success error cancel]}
    (let [sessionid (session-id)
          trid      (generate-trid)
          paths     {:success success
                     :error   error
                     :cancel  cancel}]
      (mongo/update :ident {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (java.util.Date.)} :upsert true)
      (core/html [:html
                  [:head [:title "SAML Dev Login"]]
                  [:body {:style "background-color: #ab0000; color: white; padding: 4em"}
                   [:h3 "SAML Dev Login"]
                   [:div
                    (form/form-to {} [:post "/dev/saml-login"]
                      [:p "Etunimi: " (form/text-field :firstName)]
                      [:p "Sukunimi: " (form/text-field :lastName)]
                      [:p "Hetu: " (form/text-field :userid)]
                      [:p "TRID: " (form/text-field :stamp trid)]
                      [:p (form/submit-button {:id "btn"} "Lähetä")])]]])))

  (defpage [:post "/dev/saml-login"] []
    (let [request     (request/ring-request)
          form-params (util/map-keys keyword (:form-params request))
          session     (:session request)
          trid        (:stamp form-params)
          ident       (select-keys form-params [:firstName :lastName :userid :stamp])
          data        (mongo/update-one-and-return :ident {:sessionid (:id session) :trid trid} {$set {:user ident}})
          uri         (get-in data [:paths :success])]
      (response/redirect uri))))
