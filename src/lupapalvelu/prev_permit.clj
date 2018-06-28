(ns lupapalvelu.prev-permit
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.authorization-api :as authorization]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.property :as prop]
            [lupapalvelu.review :as review]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [debug info infof]]))

(def building-fields
  (->> schemas/rakennuksen-tiedot (map (comp keyword :name)) (concat [:rakennuksenOmistajat :valtakunnallinenNumero])))

(defn- get-applicant-email [applicant]
  (-> (or
        (get-in applicant [:henkilo :sahkopostiosoite])
        (get-in applicant [:yritys :sahkopostiosoite]))
    ss/canonize-email
    (#(if-not (action/email-validator {:data {:email %}})  ;; action/email-validator returns nil if email was valid
       %
       (do
         (info "Prev permit application creation, not inviting the invalid email address received from backing system: " %)
         nil)))))

(defn get-applicant-type [applicant]
  (-> applicant (select-keys [:henkilo :yritys]) keys first))

(defn- applicant-field-values [applicant {:keys [name] :as element}]
  (if (contains? applicant :henkilo)
    (case (keyword name)
      :_selected "henkilo"
      :userId nil
      :etunimi (get-in applicant [:henkilo :nimi :etunimi])
      :sukunimi (get-in applicant [:henkilo :nimi :sukunimi])
      :hetu (get-in applicant [:henkilo :henkilotunnus])
      :turvakieltoKytkin (boolean (:turvakieltoKytkin applicant))
      :katu (get-in applicant [:henkilo :osoite :osoitenimi :teksti])
      :postinumero (get-in applicant [:henkilo :osoite :postinumero])
      :postitoimipaikannimi (get-in applicant [:henkilo :osoite :postitoimipaikannimi])
      :puhelin (some->> (get-in applicant [:henkilo :puhelin])
                        (re-find #"[0-9- ]+")) ;; Strip illegal characters: only accept dash, numbers and whitespace.
      :email (get-in applicant [:henkilo :sahkopostiosoite])
      :koulutusvalinta (get-in applicant [:koulutus])
      :valmistumisvuosi (get-in applicant [:valmistumisvuosi])
      :kuntaRoolikoodi (or (get-in applicant [:suunnittelijaRoolikoodi])
                           (get-in applicant [:tyonjohtajaRooliKoodi]))
      (tools/default-values element))
    (let [postiosoite (or (get-in applicant [:yritys :postiosoite])
                          (get-in applicant [:yritys :postiosoitetieto :postiosoite]))]
      (case (keyword name)
        :_selected "yritys"
        :companyId nil
        :yritysnimi (get-in applicant [:yritys :nimi])
        :liikeJaYhteisoTunnus (get-in applicant [:yritys :liikeJaYhteisotunnus])
        :katu (get-in postiosoite [:osoitenimi :teksti])
        :postinumero (get-in postiosoite [:postinumero])
        :postitoimipaikannimi (get-in postiosoite [:postitoimipaikannimi])
        :turvakieltoKytkin (boolean (:turvakieltoKytkin applicant))
        :puhelin (get-in applicant [:yritys :puhelin])
        :email (get-in applicant [:yritys :sahkopostiosoite])
        :verkkolaskuTunnus (get-in applicant [:yritys :verkkolaskutustieto :Verkkolaskutus :verkkolaskuTunnus])
        :ovtTunnus (get-in applicant [:yritys :verkkolaskutustieto :Verkkolaskutus :ovtTunnus])
        :valittajaTunnus (get-in applicant [:yritys :verkkolaskutustieto :Verkkolaskutus :valittajaTunnus])
        (tools/default-values element)))))

(defn sanitize-document
  "Validates document replaces erroneous (:err) values with blanks."
  [application document]
  (reduce (fn [doc {:keys [path result]}]
            (cond-> doc
              (= (first result) :err) (assoc-in (cons :data path)
                                                {:value ""})))
          document
          (doc-model/validate application document)))

(defn sanitize-updates
  "Removes updates that would invalidate document. The updates are
  transforms and applied to the document before validation."
  [application document updates]
  (let [bad-paths (->> (doc-model/validate
                        application
                        (doc-model/apply-updates
                         document
                         (doc-persistence/transform document updates)))
                       (filter #(= (first (:result %)) :err))
                       (map :path)
                       set)]
    (remove (util/fn->> first (contains? bad-paths))
            updates)))

(defn- party->party-doc [party schema-name]
  (let [schema         (schemas/get-schema 1 schema-name)
        default-values (tools/create-document-data schema tools/default-values)
        document       (sanitize-document {}
                                          {:id          (mongo/create-id)
                                           :created     (now)
                                           :schema-info (:info schema)
                                           :data        (tools/create-document-data schema
                                                                                    (partial applicant-field-values party))})
        unset-type     (if (contains? party :henkilo) :yritys :henkilo)]
    (assoc-in document [:data unset-type] (unset-type default-values))))

(defn- suunnittelijaRoolikoodi->doc-schema [koodi]
  (cond
    (= koodi "p\u00e4\u00e4suunnittelija") "paasuunnittelija"
    :default                               "suunnittelija"))

(defn- osapuoli-kuntaRoolikoodi->doc-schema [koodi]
  (cond
    (= koodi "Rakennusvalvonta-asian laskun maksaja") "maksaja"
    (= koodi "Hakijan asiamies")                      "asiamies"))

(defn osapuoli->party-document [party]
  (when-let [schema-name (osapuoli-kuntaRoolikoodi->doc-schema (:kuntaRooliKoodi party))]
    (party->party-doc party schema-name)))

(defn suunnittelija->party-document [party]
  (when-let [schema-name (suunnittelijaRoolikoodi->doc-schema (:suunnittelijaRoolikoodi party))]
    (party->party-doc party schema-name)))

(defn tyonjohtaja->tj-document [party]
  (party->party-doc party "tyonjohtaja-v2"))

(defn invite-applicants [{:keys [lang user created application] :as command} applicants authorize-applicants]
  ;; FIXME: refactor document updates out from here
  (let [applicants-with-no-info (remove get-applicant-type applicants)
        applicants (filter get-applicant-type applicants)]

    ;; ensures execution will not throw exception here if hakija in the xml message lacks both "henkilo" and "yritys" fields
    (doall
      (map
        #(info "Prev permit application creation, app id: " (:id application) ", missing hakija information -> not inviting the applicant: " %)
        applicants-with-no-info))

    (dorun
      (->> applicants
        (map-indexed
          (fn [i applicant]

            ;; only invite applicants who have a valid email address
            (let [applicant-email (get-applicant-email applicant)]

              ;; Invite applicants
              (if (true? authorize-applicants)
                (when-not (ss/blank? applicant-email)
                  (authorization/send-invite!
                    (update-in command [:data] merge {:email        applicant-email
                                                      :text         (i18n/localize lang "invite.default-text")
                                                      :documentName nil
                                                      :documentId   nil
                                                      :path         nil
                                                      :role         "writer"}))
                  (info "Prev permit application creation, invited " applicant-email " to created app " (get-in command [:data :id]))))


               ;; Set applicants' user info to Hakija documents
               (let [document (if (zero? i)
                                (domain/get-applicant-document (:documents application))
                                (doc-persistence/do-create-doc! command (operations/get-applicant-doc-schema-name application)))
                     applicant-type (get-applicant-type applicant)
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
                                           {:name (get-in applicant [:yritys :nimi])
                                            :y (get-in applicant [:yritys :liikeJaYhteisotunnus])
                                            :email applicant-email
                                            :phone (get-in applicant [:yritys :puhelin])
                                            :address1 (get-in postiosoite [:osoitenimi :teksti])
                                            :zip (get-in postiosoite [:postinumero])
                                            :po (get-in postiosoite [:postitoimipaikannimi])
                                            :turvakieltokytkin (:turvakieltoKytkin applicant)}))]
                 (as-> (doc-persistence/document-subject-updates document
                                                                 user-info
                                                                 (name applicant-type)) $
                   (sanitize-updates application document $)
                   (doc-persistence/persist-model-updates application
                                                          "documents"
                                                          document
                                                          $
                                                          created))))))))))

(defn document-data->op-document [{:keys [schema-version] :as application} data]
  (let [op (app/make-op "aiemmalla-luvalla-hakeminen" (now))
        doc (doc-persistence/new-doc application (schemas/get-schema schema-version "aiemman-luvan-toimenpide") (now))
        doc (assoc-in doc [:schema-info :op] op)
        doc-updates (lupapalvelu.document.model/map2updates [] data)]
    (lupapalvelu.document.model/apply-updates doc doc-updates)))

(defn schema-datas [{:keys [rakennusvalvontaasianKuvaus vahainenPoikkeaminen]} buildings]
  (map
    (fn [{:keys [data]}]
      (remove empty? (conj (doc-model/map2updates [] (select-keys data building-fields))
                           (when-not (or (ss/blank? (:rakennusnro data))
                                         (= "000" (:rakennusnro data)))
                             [["tunnus"] (:rakennusnro data)])
                           (when-not (ss/blank? rakennusvalvontaasianKuvaus)
                             [["kuvaus"] rakennusvalvontaasianKuvaus])
                           (when-not (ss/blank? vahainenPoikkeaminen)
                             [["poikkeamat"] vahainenPoikkeaminen])))) buildings))

(defn do-create-application-from-previous-permit [command operation xml app-info location-info authorize-applicants]
  (let [{:keys [hakijat]} app-info
        buildings-and-structures (building-reader/->buildings-and-structures xml)
        document-datas (schema-datas app-info buildings-and-structures)
        manual-schema-datas {"aiemman-luvan-toimenpide" (first document-datas)}
        command (update-in command [:data] merge
                  {:operation operation :infoRequest false :messages []}
                  location-info)
        created-application (application/do-create-application command manual-schema-datas)
        new-parties (remove empty?
                            (concat (map suunnittelija->party-document (:suunnittelijat app-info))
                                    (map osapuoli->party-document (:muutOsapuolet app-info))))
        structure-descriptions (map :description buildings-and-structures)
        created-application (assoc-in created-application [:primaryOperation :description] (first structure-descriptions))

        ;; make secondaryOperations for buildings other than the first one in case there are many
        other-building-docs (map (partial document-data->op-document created-application) (rest document-datas))
        secondary-ops (mapv #(assoc (-> %1 :schema-info :op) :description %2) other-building-docs (rest structure-descriptions))

        created-application (-> created-application
                                (update-in [:documents] concat other-building-docs new-parties)
                                (update-in [:secondaryOperations] concat secondary-ops)
                                (assoc :opened (:created command)))

        ;; attaches the new application, and its id to path [:data :id], into the command
        command (util/deep-merge command (action/application->command created-application))]
  (logging/with-logging-context {:applicationId (:id created-application)}
    ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
    (application/insert-application created-application)
    (infof "Inserted prev-permit app: org=%s kuntalupatunnus=%s authorizeApplicants=%s"
           (:organization created-application)
           (get-in command [:data :kuntalupatunnus])
           authorize-applicants)
    ;; Get verdicts for the application
    (when-let [updates (verdict/find-verdicts-from-xml command xml)]
      (action/update-application command updates))

    (invite-applicants command hakijat authorize-applicants)
    (infof "Processed applicants, processable applicants count was: %s" (count (filter get-applicant-type hakijat)))

    (let [updated-application (mongo/by-id :applications (:id created-application))
          {:keys [updates added-tasks-with-updated-buildings attachments-by-task-id]} (review/read-reviews-from-xml user/batchrun-user-data (now) updated-application xml)
          review-command (assoc (action/application->command updated-application (:user command)) :action "prev-permit-review-updates")
          update-result (review/save-review-updates review-command updates added-tasks-with-updated-buildings attachments-by-task-id)]
      (if (:ok update-result)
        (info "Saved review updates")
        (infof "Reviews were not saved: %s" (:desc update-result))))


    (let [fetched-application (mongo/by-id :applications (:id created-application))]
      (mongo/update-by-id :applications (:id fetched-application) (meta-fields/applicant-index-update fetched-application))
      fetched-application))))

(defn enough-location-info-from-parameters? [{{:keys [x y address propertyId]} :data}]
  (and
    (not (ss/blank? address))
    (not (ss/blank? propertyId))
    (-> x util/->double pos?)
    (-> y util/->double pos?)))

(defn get-location-info [{data :data :as command} app-info]
  (when app-info
    (let [rakennuspaikka-exists? (and (:rakennuspaikka app-info)
                                      (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))
          location-info          (cond
                                   rakennuspaikka-exists?                          (:rakennuspaikka app-info)
                                   (enough-location-info-from-parameters? command) (select-keys data [:x :y :address :propertyId]))]
      (when-not rakennuspaikka-exists?
        (info "Prev permit application creation, rakennuspaikkatieto information incomplete:" (:rakennuspaikka app-info)))
      location-info)))

(defn fetch-prev-application! [{{:keys [organizationId kuntalupatunnus authorizeApplicants]} :data :as command}]
  (let [operation             "aiemmalla-luvalla-hakeminen"
        permit-type           (operations/permit-type-of-operation operation)
        dummy-application     {:id "" :permitType permit-type :organization organizationId}
        xml                   (krysp-fetch/get-application-xml-by-backend-id dummy-application kuntalupatunnus)
        app-info              (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        location-info         (get-location-info command app-info)
        organization          (when (:propertyId location-info)
                                (organization/resolve-organization (prop/municipality-by-property-id (:propertyId location-info)) permit-type))
        validation-result     (permit/validate-verdict-xml permit-type xml organization)
        organizations-match?  (= organizationId (:id organization))
        no-proper-applicants? (not-any? get-applicant-type (:hakijat app-info))]
    (cond
      (empty? app-info)                 (fail :error.no-previous-permit-found-from-backend)
      (not location-info)               (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
      (not (:propertyId location-info)) (fail :error.previous-permit-no-propertyid)
      (not organizations-match?)        (fail :error.previous-permit-found-from-backend-is-of-different-organization)
      validation-result                 validation-result
      :else                             (let [{id :id} (do-create-application-from-previous-permit command
                                                                                                   operation
                                                                                                   xml
                                                                                                   app-info
                                                                                                   location-info
                                                                                                   authorizeApplicants)]
                                          (if no-proper-applicants?
                                            (ok :id id :text :error.no-proper-applicants-found-from-previous-permit)
                                            (ok :id id))))))

(def fix-prev-permit-counter (atom 0))

(defn fix-prev-permit-addresses []
  (when @mongo/connection
    (throw "Mongo already connected, aborting"))
  (try
    (mongo/connect!)
    (reset! fix-prev-permit-counter 0)

    (let [operation :aiemmalla-luvalla-hakeminen
          applications (mongo/select :applications
                                     {"primaryOperation.name" operation}
                                     {:id 1 :permitType 1 :verdicts 1 :organization 1 :address 1})]
      (doseq [{:keys [id verdicts address] :as application} applications]
        (if-let [kuntalupatunnus (get-in verdicts [0 :kuntalupatunnus])]
          (let [xml (krysp-fetch/get-application-xml-by-backend-id application kuntalupatunnus)
                app-info (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
                correct-address (get-in app-info [:rakennuspaikka :address])]
            (if (nil? correct-address)
              (println "No address info in XML for application" id)
              (when-not (= correct-address address)
                (println "old address:" address)
                (println "#_(mongo/update-by-id :applications" id
                       "{$set {:address" correct-address
                       ":title" correct-address "}})")
                (mongo/update-by-id :applications id
                                    {$set {:address correct-address
                                           :title   correct-address}})
                (mongo/update-by-id :submitted-applications id
                                    {$set {:address correct-address
                                           :title   correct-address}})
                (swap! fix-prev-permit-counter inc))))
          (println "No verdict for application" id))))
    (println "fixed" @fix-prev-permit-counter "applications")
    (finally
      (mongo/disconnect!))))

(defn fix-prev-permit-applicants []
  (when @mongo/connection
    (throw "Mongo already connected, aborting"))
  (try
    (mongo/connect!)
    (reset! fix-prev-permit-counter 0)

    (let [operation :aiemmalla-luvalla-hakeminen
          applications (mongo/select :applications
                                     {"primaryOperation.name" operation}
                                     {:id 1 :permitType 1 :verdicts 1 :organization 1 :documents 1})]
      (doseq [{:keys [id verdicts documents] :as application} applications]
        (if-let [kuntalupatunnus (get-in verdicts [0 :kuntalupatunnus])]
          (let [xml               (krysp-fetch/get-application-xml-by-backend-id application kuntalupatunnus)
                app-info          (krysp-reader/get-app-info-from-message xml kuntalupatunnus)]
            (if (seq app-info)
              (let [dummy-command         (action/application->command application)
                    old-applicants        (filter #(= (get-in % [:schema-info :name]) "hakija-r") documents)
                    new-applicants        (map #(party->party-doc % "hakija-r") (:hakijat app-info))]

                ; remove old applicants from application & create applicant doc for each
                (action/update-application dummy-command {$pull {:documents {:id {$in (map :id old-applicants)}}}})
                (action/update-application dummy-command {$pushAll {:documents new-applicants}})
                (println "Updated" id)
                (swap! fix-prev-permit-counter inc))
              (println "No XML for application" id)))
          (println "No verdict for application" id))))
    (println "fixed" @fix-prev-permit-counter "applications")
    (finally
      (mongo/disconnect!))))

(defn- ->op-document [{:keys [schema-version] :as application} building]
  (let [op (app/make-op "aiemmalla-luvalla-hakeminen" (now))
        doc (doc-persistence/new-doc application (schemas/get-schema schema-version "aiemman-luvan-toimenpide") (now))
        doc (assoc-in doc [:schema-info :op] op)
        doc-updates (lupapalvelu.document.model/map2updates [] (select-keys building building-fields))]
    (lupapalvelu.document.model/apply-updates doc doc-updates)))

(defn extend-prev-permit-with-all-parties
  [application app-xml app-info]
  (let [new-parties (concat (map suunnittelija->party-document (:suunnittelijat app-info))
                            (map osapuoli->party-document (:muutOsapuolet app-info)))
        buildings (building-reader/->buildings app-xml)]
      {:new-op-documents (map (partial ->op-document application) buildings)
       :new-parties new-parties}))
