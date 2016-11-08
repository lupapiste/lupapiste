(ns lupapalvelu.ident.dummy
  (:require [noir.response :as response]
            [noir.core :refer [defpage]]
            [sade.util :as util]
            [hiccup.form :as form]
            [hiccup.core :as core]
            [sade.env :as env]
            [monger.operators :refer [$set]]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]
            [noir.request :as request]
            [hiccup.core :as core]
            [lupapalvelu.security :as security]
            [sade.env :as env]))

(defn session-id [] (get-in (request/ring-request) [:session :id]))

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
                                   [:td [:input {:name "userid"
                                                 :data-test-id "dummy-login-userid"
                                                 :value "010101-123N"}]]]
                                  [:tr [:td "Katuosoite: "
                                        [:td (form/text-field :street "Testikatu 23")]]]
                                  [:tr [:td "Postinumero: "]
                                   [:td (form/text-field :zip "90909")]]
                                  [:tr [:td "Kaupunki: "]
                                   [:td (form/text-field :city "Testikyl\u00e4")]]
                                  [:tr [:td "TRID: "]
                                   [:td (form/text-field :stamp trid)]]
                                  [:tr [:td {:colspan 2} (form/submit-button {:id "btn" :data-test-id "submit-button"} "OK")]]
                                  [:tr [:td {:colspan 2} (form/submit-button {:id "btn" :data-test-id "cancel-button" :name "cancel"} "Peru tapahtuma")]]])]]])))

  (defpage [:post "/dev/saml-login"] []
    (let [request     (request/ring-request)
         form-params (util/map-keys keyword (:form-params request))
         session     (:session request)
         trid        (:stamp form-params)
         ident       (select-keys form-params [:firstName :givenName :lastName :userid :street :zip :city :stamp])]
     (if (:cancel form-params)
       (let [data (mongo/select-one :vetuma {:sessionid (:id session) :trid trid})]
         (response/redirect (get-in data [:paths :cancel])))
       (let [data (mongo/update-one-and-return :vetuma {:sessionid (:id session) :trid trid} {$set {:user ident}})]
         (response/redirect (get-in data [:paths :success]))))))

  (defpage [:get "/dev/saml-logout"] {:keys [return]}
    (response/redirect return)))