(ns lupapalvelu.application.foreman
  (:require [sade.strings :as ss]))

(defn tj-document->fullname [tj-document]
  (let [henkilotiedot (some-> tj-document :data :henkilotiedot)
        firstname     (some-> henkilotiedot :etunimi :value)
        lastname      (some-> henkilotiedot :sukunimi :value)
        fullname      (ss/trim-join " " firstname lastname)]
    fullname))

(defn foreman-document->role [foreman-doc]
  (some->> foreman-doc :data :kuntaRoolikoodi :value))

(defn tj-document->email [tj-document]
  (some-> tj-document :data :yhteystiedot :email :value))

(defn tj-document->foreman-consent [tj-document]
  (some-> tj-document :data :foremanConsent :value))

(defn with-submit-settings-defaults [settings]
  (merge-with merge
              {:notification {:acknowledge  true
                              :approve      false
                              :give-verdict true}
               :application  {:approve      false
                              :give-verdict true}}
              settings))
