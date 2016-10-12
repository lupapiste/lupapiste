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
   :suomifi-vakinainenkotimainenlahiosoites                   :street
   :suomifi-vakinainenkotimainenlahiosoitepostinumero         :zip
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

(defpage "/api/saml/error" {relay-state :RelayState status :statusCode status2 :statusCode2 message :statusMessage}
  (if (str/contains? status2 "AuthnFailed")
    (warn "SAML endpoint rejected authentication")
    (error "SAML endpoint encountered an error:" status status2 message))
  (if-let [trid (re-find #"\d+$" relay-state)]
    (let [url (or (some-> (lupapalvelu.ident.session/get-user trid) (get-in [:paths :cancel])) "/")]
      (response/redirect url))))

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
                  [:head [:title "SAML Dev Login"]
                         [:script {:type "text/javascript" :src "https://ajax.aspnetcdn.com/ajax/jQuery/jquery-1.11.3.min.js"}]]
                  [:body {:style "background-color: #fbc742; padding: 4em; font-size: 14px; font-family: Courier"}
                   [:h3 "SAML Dev Login"]
                   [:div
                    (form/form-to {} [:post "/dev/saml-login"]
                      [:table
                       [:tr [:td "Etunimi: "]
                            [:td (form/text-field :firstName "Teemu")]]
                       [:tr [:td "Kutsumanimi: "]
                            [:td (form/text-field :givenName "Teemu")]]
                       [:tr [:td "Sukunimi: "]
                            [:td (form/text-field :lastName "Testaaja")]]
                       [:tr [:td "Hetu: "]
                            [:td (form/text-field :userid "010101-123N")]]
                       [:tr [:td "Katuosoite: "
                            [:td (form/text-field :street "Testikatu 23")]]]
                       [:tr [:td "Postinumero: "]
                            [:td (form/text-field :zip "90909")]]
                       [:tr [:td "Kaupunki: "]
                            [:td (form/text-field :city "Testikylä")]]
                       [:tr [:td "TRID: "]
                            [:td (form/text-field :stamp trid)]]
                       [:tr [:td {:colspan 2} (form/submit-button {:id "btn" :data-test-id "submit-button"} "Lähetä")]]
                       [:tr [:td {:colspan 2} (form/submit-button {:id "btn" :data-test-id "cancel-button" :name "cancel"} "Peru tapahtuma")]]])]]])))

  (defpage [:post "/dev/saml-login"] []
    (let [request     (request/ring-request)
          form-params (util/map-keys keyword (:form-params request))
          session     (:session request)
          trid        (:stamp form-params)
          ident       (select-keys form-params [:firstName :givenName :lastName :userid :street :zip :city :stamp])
          data        (mongo/update-one-and-return :ident {:sessionid (:id session) :trid trid} {$set {:user ident}})
          uri         (if (:cancel form-params)
                        (get-in data [:paths :cancel])
                        (get-in data [:paths :success]))]
      (response/redirect uri))))
