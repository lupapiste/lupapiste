(ns lupapalvelu.prev-permit
  (:require [taoensso.timbre :refer [info]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.application :as application]
            [lupapalvelu.action :as action]
            [lupapalvelu.verdict-api :as verdict-api]
            [lupapalvelu.user :as user]
            [lupapalvelu.authorization-api :as authorization]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.commands :as commands]))

(defn- get-applicant-email [applicant]
  (-> (or
        (get-in applicant [:henkilo :sahkopostiosoite])
        (get-in applicant [:yritys :sahkopostiosoite]))
    user/canonize-email
    (#(if-not (action/email-validator {:data {:email %}})  ;; action/email-validator returns nil if email was valid
       %
       (do
         (info "Prev permit application creation, not inviting the invalid email address received from backing system: " %)
         nil)))))

(defn- invite-applicants [{:keys [lang user created application] :as command} applicants]
  (dorun
    (->> applicants
         (map-indexed
           (fn [i applicant]
             ;; only invite applicants who have a valid email address
             (when-let [applicant-email (get-applicant-email applicant)]

               ;; Invite applicants
               (authorization/send-invite!
                 (update-in command [:data] merge {:email        applicant-email
                                                   :text         (i18n/localize lang "invite.default-text")
                                                   :documentName nil
                                                   :documentId   nil
                                                   :path         nil
                                                   :role         "writer"}))
               (info "Prev permit application creation, invited " applicant-email " to created app " (get-in command [:data :id]))

               ;; Set applicants' user info to Hakija documents
               (let [document (if (zero? i)
                                (domain/get-document-by-name application "hakija")
                                (commands/do-create-doc (assoc-in command [:data :schemaName] "hakija")))
                     applicant-type (-> applicant (select-keys [:henkilo :yritys]) keys first)
                     user-info (case applicant-type
                                 ;; Not including here the id of the invited user into "user-info",
                                 ;; so it is not set to personSelector, and validation is thus not done.
                                 ;; If user id would be given, the validation would fail since applicants have not yet accepted their invitations
                                 ;; (see the check in :personSelector validator in model.clj).
                                 :henkilo {:firstName (get-in applicant [:henkilo :nimi :etunimi])
                                           :lastName (get-in applicant [:henkilo :nimi :sukunimi])
                                           :email applicant-email
                                           :phone (get-in applicant [:henkilo :puhelin])
                                           :personId (get-in applicant [:henkilo :henkilotunnus])
                                           :street (get-in applicant [:henkilo :osoite :osoitenimi :teksti])
                                           :zip (get-in applicant [:henkilo :osoite :postinumero])
                                           :city (get-in applicant [:henkilo :osoite :postitoimipaikannimi])
                                           :turvakieltokytkin (:turvakieltoKytkin applicant)}

                                 :yritys (let [;; the postiosoite path changed in krysp 2.1.5, supporting both
                                               postiosoite (or
                                                             (get-in applicant [:yritys :postiosoite])
                                                             (get-in applicant [:yritys :postiosoitetieto :postiosoite]))]
                                           {:companyName (get-in applicant [:yritys :nimi])
                                            :companyId (get-in applicant [:yritys :liikeJaYhteisotunnus])
                                            :email applicant-email
                                            :phone (get-in applicant [:yritys :puhelin])
                                            ;;
                                            ;; TODO: Etsitaanko naita yhteyshenkilon tietoja esim. applicant-emailin perusteella kannasta?
                                            ;;
;                                            :firstName (get-in applicant [:henkilo :nimi :etunimi])
;                                            :lastName (get-in applicant [:henkilo :nimi :sukunimi])
                                            :street (get-in postiosoite [:osoitenimi :teksti])
                                            :zip (get-in postiosoite [:postinumero])
                                            :city (get-in postiosoite [:postitoimipaikannimi])
                                            :turvakieltokytkin (:turvakieltoKytkin applicant)}))]

                 (commands/set-subject-to-document application document user-info (name applicant-type) created))))))))

(defn do-create-application-from-previous-permit [{:keys [lang user created] :as command} xml app-info location-info]
  (let [{:keys [rakennusvalvontaasianKuvaus vahainenPoikkeaminen hakijat]} app-info
        manual-schema-datas {"hankkeen-kuvaus" (filter seq
                                                 (conj []
                                                   (when-not (ss/blank? rakennusvalvontaasianKuvaus) [["kuvaus"] rakennusvalvontaasianKuvaus])
                                                   (when-not (ss/blank? vahainenPoikkeaminen) [["poikkeamat"] vahainenPoikkeaminen])))}
        ;; TODO: Property-id structure is about to change -> Fix this municipality logic when it changes.
        municipality (subs (:propertyId location-info) 0 3)
        command (update-in command [:data] merge {:municipality municipality :infoRequest false :messages []} location-info)
        created-application (application/do-create-application command manual-schema-datas)
        ;; TODO: Aseta applicationille viimeisin state? (lupapalvelu.document.canonical-common/application-state-to-krysp-state kaanteisesti)
        ;        created-application (assoc created-application
        ;                              :state (some #(when (= (-> app-info :viimeisin-tila :tila) (val %)) (first %)) lupapalvelu.document.canonical-common/application-state-to-krysp-state))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (merge command (action/application->command created-application))]

    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (application/insert-application created-application)
    (verdict-api/find-verdicts-from-xml command xml)  ;; Get verdicts for the application
    (invite-applicants command hakijat)
    (:id created-application)))