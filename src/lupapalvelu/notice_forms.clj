(ns lupapalvelu.notice-forms
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.assignment :as assi]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.automatic-assignment.schemas :refer [NoticeFormType]]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as krysp]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr :refer [SummaryUser]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(def form-type NoticeFormType)

(def form-states ["open" "ok" "rejected"])

(def org-id-and-type {:organizationId ssc/NonBlankStr
                      :type           form-type})

(defn- supported-operation? [operation-name type]
  (not (util/includes-as-kw? (cond-> [;; Unsupported for all
                                      :tyonjohtajan-nimeaminen-v2
                                      :rakennustietojen-korjaus
                                      :suunnittelijan-nimeaminen
                                      :raktyo-aloit-loppuunsaat]

                               (util/not=as-kw type :construction)
                               (concat [;; Unsupported for terrain and location
                                        :kayttotark-muutos
                                        :sisatila-muutos
                                        :markatilan-laajentaminen
                                        :linjasaneeraus
                                        :takka-tai-hormi
                                        :jakaminen-tai-yhdistaminen
                                        :jatevesi
                                        :puun-kaataminen
                                        :tontin-jarjestelymuutos
                                        :muu-tontti-tai-kort-muutos
                                        :kaivuu
                                        :rak-valm-tyo
                                        :aloitusoikeus]))
                             operation-name)))

(defn authority-in-organization?
  "More robust and specific version of
  `usr/user-is-authority-in-organization?`. True if the user is
  authority in the given organization and enabled."
  [{:keys [role enabled orgAuthz]} org-id]
  (boolean (and enabled
                (util/=as-kw role :authority)
                (util/includes-as-kw? (get orgAuthz (keyword org-id))
                                      :authority))))

(defn user-authority-in-organization
  "Pre-check for handler user-id."
  [{data :data}]
  (when-let [user-id (:userId data)]
    (if-let [user (usr/get-user-by-id user-id)]
      (when-not (authority-in-organization? user (:organizationId data))
        (fail :error.not-authority))
      (fail :error.user.not.found))))

(defschema ToggleFormParams
  (assoc org-id-and-type :enabled sc/Bool))

(defschema FormTextParams
  (assoc org-id-and-type
         :lang (apply sc/enum (map name i18n/languages))
         :text sc/Str))

(defschema FormHandlerRoleParams
  (assoc org-id-and-type
         :roleId (sc/maybe ssc/ObjectIdStr)))

(defschema FormHandlerUserParams
  (assoc org-id-and-type
         :userId sc/Str))

(defschema FormHandlerTypeParams
  (assoc org-id-and-type
         (sc/optional-key :handlerType) (sc/maybe (sc/enum "role-id" "user-id"))))

(defschema NewNoticeFormParams
  {:id                          ssc/ApplicationId
   :text                        ssc/NonBlankStr
   :buildingIds                 [ssc/NonBlankStr]
   (sc/optional-key :filedatas) [sc/Any]})

(defschema CustomerDetails
  {:name  ssc/NonBlankStr
   :email ssc/EmailSpaced
   :phone ssc/Tel
   :payer (sc/conditional
            :permitPayer {:permitPayer (sc/eq true)}
            :else {:name       ssc/NonBlankStr
                   :street     ssc/NonBlankStr
                   :zip        ssc/Zipcode
                   :city       ssc/NonBlankStr
                   :identifier (sc/cond-pre ssc/FinnishY ssc/Hetu)})})

(defschema NewNoticeFormWithCustomerParams
  (assoc NewNoticeFormParams :customer CustomerDetails))

(defschema NoticeForm
  {:id                         ssc/ObjectIdStr
   :text                       sc/Str
   :buildingIds                [sc/Str]
   :type                       form-type
   ;; The last history item is the current one
   :history                    [{:state                  (apply sc/enum form-states)
                                 :timestamp              ssc/Timestamp
                                 ;; Typically used for rejection text
                                 (sc/optional-key :info) sc/Str
                                 :user                   SummaryUser}]
   (sc/optional-key :customer) CustomerDetails})

(defn- update-org-notice-form
  [org-id type path value]
  (org/update-organization org-id
                           {$set {(util/kw-path :notice-forms type path)
                                  value}}))

(defn toggle-form
  "Enable/disable form in the organization"
  [{:keys [organizationId enabled type]}]
  (update-org-notice-form organizationId type :enabled enabled))

(defn set-form-text
  "Notice form help text in one language."
  [{:keys [organizationId type lang text]}]
  (update-org-notice-form organizationId type (util/kw-path :text lang) text))

(defn toggle-form-integration
  "Enable/disable form integration in the organization"
  [{:keys [organizationId enabled type]}]
  (update-org-notice-form organizationId type :integration enabled))

(defn form-state [form]
  (some-> form :history last :state))

(defn form-title [lang form]
  (i18n/localize lang (util/kw-path :notice-forms (:type form))))

(defn primary-operation-supported-check
  "Fails if the primary operation is not supported for the given notice form type"
  [{:keys [application]} type]
  (when-not (supported-operation? (some-> application :primaryOperation :name)
                                  type)
    (fail :error.unsupported-primary-operation)))

(defn foreman-check
  "Pre-checker that fails if the responsible foreman (Vastaava
  työnjohtaja) has not been assigned to the application."
  [{:keys [application]}]
  (when application
    (when-not (->> (foreman/get-linked-foreman-applications application)
                   (filter #(util/includes-as-kw? states/post-submitted-states (:state %)))
                   (mapcat :documents)
                   (some #(= (get-in % [:data :kuntaRoolikoodi :value])
                             "vastaava työnjohtaja")))
      (fail :error.no-suitable-foreman))))

(defn form-exists? [application criteria]
  (some (fn [form]
          (every? true? (map (fn [[k v]]
                               (case k
                                 :state       (util/=as-kw (form-state form) v)
                                 :type        (util/=as-kw (:type form) v)
                                 :buildingIds (util/intersection-as-kw (:buildingIds form) v)))
                             criteria)))
        (:notice-forms application)))


(defn pre-checks-by-type [{data :data :as command}]
  (when-let [type (:type data)]
    (or (primary-operation-supported-check command type)
        (when (util/=as-kw type :construction)
          (foreman-check command)))))

(defn foreman-list
  "Simplified `foreman/get-linked-foreman-applications` list."
  [application]
  (map (fn [{:keys [state auth documents latest-verdict-status]}]
         (let [doc-data   (some->> documents first :data)
               doc-names  (remove ss/blank? [(get-in doc-data [:henkilotiedot :etunimi :value])
                                             (get-in doc-data [:henkilotiedot :sukunimi :value])])
               name-auth  (util/find-by-key :role "foreman" auth)
               auth-names (remove ss/blank? [(:firstName name-auth)
                                             (:lastName name-auth)])
               role       (get-in doc-data [:kuntaRoolikoodi :value])]
           {:status     (:status latest-verdict-status)
            :stateLoc   (get latest-verdict-status :statusLoc state)
            :foremanLoc (util/kw-path :osapuoli.tyonjohtaja.kuntaRoolikoodi role)
            :fullname   (ss/join " " (or (seq doc-names) auth-names))}))
       (foreman/get-linked-foreman-applications application)))

(defn- operations-map
  "Id - name map for application operations"
  [application]
  (->> (app-utils/get-operations application)
       (map #(vector (:id %) (:name %)))
       (into {})))

(defn default-buildings
  "A building is included if,
  1. It is not already selected in the currently open form of the same type
  2. It is bound to an operation
  3. The operation is supported for the form type."
  [{:keys [buildings notice-forms] :as application} type]
  (let [open-buildings (->> notice-forms
                            (filter #(and (= type (:type %))
                                          (= "open" (form-state %))))
                            (mapcat :buildingIds)
                            set)
        op-map         (operations-map application)]
    (some->> buildings
             (remove (util/fn-> :buildingId ss/blank?))
             (remove #(contains? open-buildings (:buildingId %)))
             (map (fn [{op-id :operationId :as build}]
                    (-> build
                        (select-keys [:buildingId :description :nationalId])
                        (assoc :opName (get op-map op-id)))))
             (filter :opName)
             (filter (util/fn-> :opName (supported-operation? type))))))

(defn location-buildings
  "For a building to be listed in a location notice form, it has to be
  both available and in an approved terrain form."
  [{:keys [notice-forms] :as application} type]
  (let [approved (->> notice-forms
                      (filter #(and (util/=as-kw :terrain (:type %))
                                    (= "ok" (form-state %))))
                      (mapcat :buildingIds)
                      set)]
    (filter #(contains? approved (:buildingId %))
            (default-buildings application type))))

(defn buildings [application type]
  ((if (util/=as-kw type :location)
     location-buildings
     default-buildings) application type))


(defn buildings-available
  "Pre-check version for `buildings`. If buildingIds are given, they must satisfy the checker
  requirements."
  [{:keys [application data]}]
  (when (and application (seq (:buildingIds data)))
    (let [available (set (map :buildingId (buildings application (:type data))))]
      (when-not (and (seq available)
                     (every? (partial contains? available) (:buildingIds data)))
        (fail :error.buildings-not-available)))))

(defn organization-notice-form
  "Borganization denotes an organization either via delay, id or map."
  [borganization type]
  (get-in (if (delay? borganization)
            @borganization
            (org/get-org-from-org-or-id borganization))
          [:notice-forms (keyword type)]))

(defn info-text
  "Notice form info text is defined in the organization."
  [lang organization type]
  (get-in (organization-notice-form organization type)
          [:text (keyword lang)]))

(defn user->customer
  "Note that the result is not valid `CustomerDetails` instance. This is fine, since the result is
  part of the new notice data."
  [{user-id :id}]
  (let [user     (usr/get-user-by-id user-id)
        fullname (usr/full-name user)]
    (merge (select-keys user  [:email :phone])
           {:name  fullname
            :payer (assoc (select-keys user [:street :zip :city])
                          :name fullname
                          :permitPayer true)})))

(defn new-notice-data [{:keys [lang application organization user]} type]
  (cond-> {:info-text (info-text lang @organization type)
           :buildings (buildings application type)}
    (util/=as-kw type :construction) (assoc :foremen (foreman-list application))
    (util/includes-as-kw? [:terrain :location] type) (assoc :customer (user->customer user))))

(defn new-notice-form
  "New notice form and related stuff.
  1. New notice form into application
  2. Create corresponding attachments for the uploaded files
  3. Upsert assignments"
  [{:keys [lang data user created] :as command} type]
  (let [form (->> (select-keys data [:text :buildingIds :customer])
                  (merge {:id      (mongo/create-id)
                          :type    type
                          :history [{:state     "open"
                                     :timestamp created
                                     :user      (usr/summary user)}]})
                  ss/trimwalk
                  (sc/validate NoticeForm))]
    (action/update-application command {$push {:notice-forms form}})
    (doseq [filedata (:filedatas data)]
      (att/convert-and-attach! command
                               {:target          {:id   (:id form)
                                                  :type "notice-form"}
                                :created         created
                                :attachment-type {:type-group "muut"
                                                  :type-id    "muu"}
                                :contents        (form-title lang form)}
                               filedata))
    (factory/process-notice-form-automatic-assignments command form)
    form))

(defn form-attachments [application form-id]
  (filter #(= (:target %) {:id   form-id
                           :type "notice-form"})
          (:attachments application)))

(defn delete-notice-form
  "Deletes the form with given id and its attachments. Also prunes
  assignemnts accordingly."
  [{:keys [application] :as command} form-id]
  ;; Notice form
  (action/update-application command {$pull {:notice-forms {:id form-id}}})
  ;; Attachments
  (some->> (form-attachments application form-id)
           (map :id)
           (att/delete-attachments! application))
  ;; Assignments (should be only one)
  (assi/remove-target-from-assignments (:id application) form-id))

(defn construction-form-kuntagml
  "Saves construction form KuntaGML message (Aloitusilmoitus), if...
  1. State is ok.
  2. Organization has proper integration configuration.
  3. Integration is activated for construction forms."
  [{:keys [application organization] :as command} form-id state]
  (when (and (= state "ok")
             (:integration (organization-notice-form organization :construction)))
    (let [form (->> (mongo/by-id :applications (:id application) [:notice-forms])
                    :notice-forms
                    (util/find-by-id form-id))]
      (when (util/=as-kw (:type form) :construction)
        (krysp/save-aloitusilmoitus-as-krysp command form)))))

(defn set-notice-form-state
  "Adds new state to the form history. Updates attachments and
  assignments accordingly. For construction forms also creates
  KuntaGML message, if prudentf
    "
  [{:keys [application user created organization] :as command} form-id state info]
  ;; Notice form
  (action/update-application command
                             {:notice-forms {$elemMatch {:id form-id}}}
                             {$push {:notice-forms.$.history (util/assoc-when-pred {:state     state
                                                                                    :timestamp created
                                                                                    :user (usr/summary user)}
                                                                                   ss/not-blank?
                                                                                   :info info)}
                              $set {:notice-forms.$.modified created}})
  ;; Attachments
  (doseq [{file :latestVersion} (form-attachments application form-id)]
    (att/set-attachment-state! command
                               (:fileId file)
                               (if (= state "ok") :ok :requires_user_action)))
  ;; Assignments (should be only one)
  (assi/remove-target-from-assignments (:id application) form-id)

  ;; KuntaGML
  (construction-form-kuntagml command form-id state))

(defn form-status
  "Summary of the last history state."
  [{:keys [history]}]
  (let [{user :user :as m} (last history)]
    (assoc (select-keys m [:state :timestamp :info])
           :fullname (->> [(:firstName user) (:lastName user)]
                          (map ss/trim)
                          (remove ss/blank?)
                          (ss/join " ")))))

(defn form-buildings [lang {:keys [buildings] :as application} {:keys [buildingIds]}]
  (let [op-map (operations-map application)]
    (map (fn [bid]
           (if-let [{:keys [description nationalId
                            operationId]} (util/find-by-key :buildingId bid
                                                            buildings)]
             (->> [(some->> (get op-map operationId)
                            (str "operations.")
                            (i18n/localize lang))
                   description
                   nationalId]
                  (map ss/trim)
                  (remove ss/blank?)
                  (ss/join " - "))
             (i18n/localize lang :notice-form.no-building)))
         buildingIds)))

(defn notice-forms [{:keys [application]} lang]
  (let [{:keys [notice-forms]} application]
    (for [{:keys [id] :as form} notice-forms]
      (util/assoc-when (select-keys form [:id :text :type :customer])
                       :status (form-status form)
                       :buildings (form-buildings lang application form)
                       :attachments (map (fn [{att-id :id file :latestVersion}]
                                           (assoc (select-keys file [:contentType :created :fileId :filename
                                                                     :size :user :version])
                                                  :attachmentId att-id))
                                         (form-attachments application id))))))

(defn cleanup-notice-forms
  "Delete every notice form (including their attachments and
  assignments) if the application is no longer in post-verdict
  state. Called as command :on-success function."
  [{:keys [application] :as command} _]
  (when-let [notice-forms (seq (:notice-forms application))]
    (let [{state :state} (mongo/by-id :applications (:id application) [:state])]
      (when-not (util/includes-as-kw? states/post-verdict-but-terminal state)
        (doseq [form-id (map :id notice-forms)]
          (delete-notice-form command form-id))))))
