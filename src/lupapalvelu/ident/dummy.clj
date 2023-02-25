(ns lupapalvelu.ident.dummy
  (:require [hiccup.core :as core]
            [hiccup.form :as form]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [monger.operators :refer [$set]]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as response]
            [sade.env :as env]
            [sade.util :as util]))

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(def dummy-person-id "010101-123N")

(env/in-dev
  (defpage [:get "/dev/saml/init-login"] {:keys [success error cancel]}
    (let [sessionid (session-id)
         trid      (security/random-password)
         paths     {:success success
                    :error   error
                    :cancel  cancel}]
     (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (java.util.Date.)} :upsert true)
     (response/json {:trid trid})))
  (defpage [:get "/dev/saml-login"] {:keys [success error cancel]}
    (let [sessionid (session-id)
         trid      (security/random-password)
         paths     {:success success
                    :error   error
                    :cancel  cancel}]
     (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (java.util.Date.)} :upsert true)
     (core/html [:html
                 [:head [:title "SAML Dev Login"]
                  [:script {:src "/lp-static/lib/jquery-3.6.0.min.js"}]
                  [:script {:src "/app/0/common.js?lang=fi"}]]
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
                                   [:td [:input {:name "userid"
                                                 :data-test-id "dummy-login-userid"
                                                 :value dummy-person-id}]]]
                                  [:tr [:td "Katuosoite: "
                                        [:td (form/text-field :street "Testikatu 23")]]]
                                  [:tr [:td "Postinumero: "]
                                   [:td (form/text-field :zip "90909")]]
                                  [:tr [:td "Kaupunki: "]
                                   [:td (form/text-field :city "Testikyl\u00e4")]]
                                  [:tr [:td "TRID: "]
                                   [:td (form/text-field :stamp trid)]]
                                  [:tr [:td "eIDAS Personal Identifier: "]
                                   [:td (form/text-field :eidasId "")]]
                                  [:tr [:td "eIDAS Given name: "]
                                   [:td (form/text-field :eidasFirstName "")]]
                                  [:tr [:td "eIDAS Family name: "]
                                   [:td (form/text-field :eidasFamilyName "")]]
                                  [:tr [:td "eIDAS Date of birth: "]
                                   [:td (form/text-field :eidasBirthDate "")]]
                                  [:tr [:td {:colspan 2} (form/submit-button {:id "btn" :data-test-id "submit-button"} "OK")]]
                                  [:tr [:td {:colspan 2} (form/submit-button {:id "btn" :data-test-id "cancel-button" :name "cancel"} "Peru tapahtuma")]]])]]])))

  (defpage [:post "/dev/saml-login"] []
    (let [request     (request/ring-request)
          form-params (util/map-keys keyword (:form-params request))
          session     (:session request)
          trid        (:stamp form-params)
          ident       (select-keys form-params [:firstName :givenName :lastName :userid :street :zip :city :stamp :eidasId :eidasFirstName :eidasFamilyName :eidasBirthDate])]
     (if (:cancel form-params)
       (let [data (mongo/select-one :vetuma {:sessionid (:id session) :trid trid})]
         (response/redirect (get-in data [:paths :cancel])))
       (let [data (mongo/update-one-and-return :vetuma {:sessionid (:id session) :trid trid} {$set {:user ident}})]
         (response/redirect (get-in data [:paths :success]))))))

  (defpage [:get "/dev/saml-logout"] {:keys [return]}
    (if-let [session (ident-session/get-session (session-id))]
      (ident-session/delete-user session))
    (response/redirect return)))
