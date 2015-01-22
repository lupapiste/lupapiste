(ns lupapalvelu.foreman-api
  (:require [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
            [monger.operators :refer :all]))

(defcommand update-foreman-other-applications
  {:roles [:applicant :authority]
   :states action/all-states
   :parameters [:id foremanHetu]}
  [{application :application user :user :as command}]
  (when-let [foreman-applications (seq (foreman/get-foreman-project-applications application foremanHetu))]
    (let [other-applications (map (fn [app] (foreman/other-project-document app (:created command))) foreman-applications)
          tyonjohtaja-doc (domain/get-document-by-name application "tyonjohtaja-v2")
          muut-hankkeet (get-in tyonjohtaja-doc [:data :muutHankkeet])
          muut-hankkeet (select-keys muut-hankkeet (for [[k v] muut-hankkeet :when (not (get-in v [:autoupdated :value]))] k))
          muut-hankkeet (into [] (map (fn [[_ v]] v) muut-hankkeet))
          all-hankkeet (concat other-applications muut-hankkeet)
          all-hankkeet (into {} (map-indexed (fn [idx hanke] {(keyword (str idx)) hanke}) all-hankkeet))
          tyonjohtaja-doc (assoc-in tyonjohtaja-doc [:data :muutHankkeet] all-hankkeet)
          documents (:documents application)
          documents (map (fn [doc] (if (= (:id tyonjohtaja-doc) (:id doc)) tyonjohtaja-doc doc)) documents)]
      (update-application command {$set {:documents documents}}))
    (ok)))

(defquery foreman-history
  {:roles            [:authority :applicant]
   :states           action/all-states
   :extra-auth-roles [:any]
   :parameters       [:id]}
  [{application :application user :user :as command}]
  (if application
    (ok :projects (foreman/get-foreman-history-data application))
    (fail :error.not-found)))

(defquery reduced-foreman-history
  {:roles            [:authority :applicant]
   :states           action/all-states
   :extra-auth-roles [:any]
   :parameters       [:id]}
  [{application :application user :user :as command}]
  (if application
    (ok :projects (foreman/get-foreman-reduced-history-data application))
    (fail :error.not-found)))

(defquery foreman-applications
  {:roles            [:applicant :authority]
   :states           action/all-states
   :extra-auth-roles [:any]
   :parameters       [:id]}
  [{application :application user :user :as command}]
  (if application
    (let [application-id (:id application)
          app-link-resp (mongo/select :app-links {:link {$in [application-id]}})
          apps-linking-to-us (filter #(= (:type ((keyword application-id) %)) "linkpermit") app-link-resp)
          foreman-application-links (filter #(= (:apptype (first (:link %)) "tyonjohtajan-nimeaminen")) apps-linking-to-us)
          foreman-application-ids (map (fn [link] (first (:link link))) foreman-application-links)
          applications (mongo/select :applications {:_id {$in foreman-application-ids}})
          mapped-applications (map (fn [app] (foreman/foreman-application-info app)) applications)]
      (ok :applications (sort-by :id mapped-applications)))
    (fail :error.not-found)))