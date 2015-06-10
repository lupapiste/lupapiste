(ns lupapalvelu.foreman-api
  (:require [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application :as application]
            [lupapalvelu.document.commands :as commands]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [monger.operators :refer :all]))

(defn cleanup-hakija-doc [doc]
  (-> doc
      (assoc :id (mongo/create-id))
      (assoc-in [:data :henkilo :userId] {:value nil})))

(defcommand create-foreman-application
  {:parameters [id taskId foremanRole]
   :user-roles #{:applicant :authority}
   :states action/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [created user application] :as command}]
  (let [original-open? (util/pos? (:opened application))
        foreman-app (-> (application/do-create-application
                         (assoc command :data {:operation "tyonjohtajan-nimeaminen-v2"
                                               :x (-> application :location :x)
                                               :y (-> application :location :y)
                                               :address (:address application)
                                               :propertyId (:propertyId application)
                                               :municipality (:municipality application)
                                               :infoRequest false
                                               :messages []}))
                      (assoc :opened (if original-open? created nil)))

        task                 (util/find-by-id taskId (:tasks application))

        hankkeen-kuvaus      (get-in (domain/get-document-by-name application "hankkeen-kuvaus") [:data :kuvaus :value])
        hankkeen-kuvaus-doc  (domain/get-document-by-name foreman-app "hankkeen-kuvaus-minimum")
        hankkeen-kuvaus-doc  (if hankkeen-kuvaus
                               (assoc-in hankkeen-kuvaus-doc [:data :kuvaus :value] hankkeen-kuvaus)
                               hankkeen-kuvaus-doc)

        tyonjohtaja-doc      (domain/get-document-by-name foreman-app "tyonjohtaja-v2")
        tyonjohtaja-doc      (if-not (ss/blank? foremanRole)
                               (assoc-in tyonjohtaja-doc [:data :kuntaRoolikoodi :value] foremanRole)
                               tyonjohtaja-doc)

        hakija-docs          (domain/get-documents-by-name application "hakija-r")
        hakija-docs          (map cleanup-hakija-doc hakija-docs)

        new-application-docs (->> (:documents foreman-app)
                                  (remove #(#{"hankkeen-kuvaus-minimum" "hakija-r" "tyonjohtaja-v2"} (-> % :schema-info :name)))
                                  (concat (remove nil? [hakija-docs hankkeen-kuvaus-doc tyonjohtaja-doc]))
                                  flatten)

        foreman-app (assoc foreman-app :documents new-application-docs)]

    (application/do-add-link-permit foreman-app (:id application))
    (application/insert-application foreman-app)
    (when task
      (let [updates [[[:asiointitunnus] (:id foreman-app)]]]
        (commands/persist-model-updates application "tasks" task updates created)))
    (ok :id (:id foreman-app) :auth (:auth foreman-app))))

(defcommand update-foreman-other-applications
  {:user-roles #{:applicant :authority}
   :states action/all-states
   :parameters [:id foremanHetu]
   :pre-checks [application/validate-authority-in-drafts]}
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

(defcommand link-foreman-task
  {:user-roles #{:applicant :authority}
   :states action/all-states
   :parameters [id taskId foremanAppId]
   :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [created application] :as command}]
  (let [task (util/find-by-id taskId (:tasks application))]
    (if task
      (let [updates [[[:asiointitunnus] foremanAppId]]]
        (commands/persist-model-updates application "tasks" task updates created))
      (fail :error.not-found))))

(defn foreman-app-check [_ application]
  (when-not (foreman/foreman-app? application)
    (fail :error.not-foreman-app)))

(defquery foreman-history
  {:user-roles #{:authority :applicant}
   :states           action/all-states
   :user-authz-roles action/all-authz-roles
   :parameters       [:id]
   :pre-checks       [foreman-app-check]}
  [{application :application user :user :as command}]
  (if application
    (ok :projects (foreman/get-foreman-history-data application))
    (fail :error.not-found)))

(defquery reduced-foreman-history
  {:user-roles #{:authority :applicant}
   :states           action/all-states
   :user-authz-roles action/all-authz-roles
   :parameters       [:id]
   :pre-checks       [foreman-app-check]}
  [{application :application user :user :as command}]
  (if application
    (ok :projects (foreman/get-foreman-reduced-history-data application))
    (fail :error.not-found)))

(defquery foreman-applications
  {:user-roles #{:applicant :authority :oirAuthority}
   :states           action/all-states
   :user-authz-roles action/all-authz-roles
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
