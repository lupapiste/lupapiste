(ns lupapalvelu.foreman
  (:require [taoensso.timbre :refer [error]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as company]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]))

(defn foreman-app? [application]
  (= :tyonjohtajan-nimeaminen-v2
     (some-> application :primaryOperation :name keyword)))

(defcontext foreman-app-context [{{user-id :id} :user application :application}]
  ;; Some permissions are added in foreman applicatons by user application role
  (when (foreman-app? application)
    {:context-scope :foreman-app
     :context-roles (->> (auth/get-auths application user-id)
                         (map :role))}))

(defn ensure-foreman-not-linked [{{foreman-app-id :foremanAppId task-id :taskId} :data {:keys [tasks]} :application}]
  (when (and (not (ss/blank? foreman-app-id))
             (->> (filter (comp #{:task-vaadittu-tyonjohtaja} keyword :name :schema-info) tasks)
                  (remove (comp #{task-id} :id))
                  (map (comp :value :asiointitunnus :data))
                  (some #{foreman-app-id})))
    (fail :error.foreman-already-linked)))

(defn other-project-document [application timestamp]
  (let [building-doc (domain/get-document-by-name application "uusiRakennus")
        kokonaisala (get-in building-doc [:data :mitat :kokonaisala :value])]
    {:luvanNumero {:value (:id application)
                   :modified timestamp}
     :katuosoite {:value (:address application)
                  :modified timestamp}
     :rakennustoimenpide {:value (get-in application [:primaryOperation :name])
                          :modified timestamp}
     :kokonaisala {:value kokonaisala
                   :modified timestamp}
     :autoupdated {:value true
                   :modified timestamp}}))

(defn- get-foreman-hetu [foreman-application]
  (let [foreman-doc (domain/get-document-by-name foreman-application "tyonjohtaja-v2")]
    (get-in foreman-doc [:data :henkilotiedot :hetu :value])))

(defn- get-foreman-applications [foreman-application & [foreman-hetu]]
  (let [foreman-hetu (if (ss/blank? foreman-hetu)
                       (get-foreman-hetu foreman-application)
                       foreman-hetu)]
    (when-not (ss/blank? foreman-hetu)
      (mongo/select :applications
                    {"primaryOperation.name" "tyonjohtajan-nimeaminen-v2"
                     :state {$in ["acknowledged" "foremanVerdictGiven" "appealed"]}
                     :documents {$elemMatch {"schema-info.name"              "tyonjohtaja-v2"
                                             "data.henkilotiedot.hetu.value" foreman-hetu}}}
                    [:created :documents :municipality]))))

(defn get-foreman-document [foreman-application]
  (domain/get-document-by-name foreman-application "tyonjohtaja-v2"))

(defn get-foreman-project-applications
  "Based on the passed foreman application, fetches all project applications that have the same foreman as in
  the passed foreman application (personal id is used as key). Returns all the linked applications as a list"
  [foreman-application foreman-hetu]
  (let [foreman-apps    (->> (get-foreman-applications foreman-application foreman-hetu)
                             (remove #(= (:id %) (:id foreman-application))))
        foreman-app-ids (map :id foreman-apps)
        links           (mongo/select :app-links {:link {$in foreman-app-ids}})
        linked-app-ids  (remove (set foreman-app-ids) (distinct (mapcat #(:link %) links)))]
    (mongo/select :applications {$and [{:_id {$in linked-app-ids}} {:state {$ne :closed}}]} [:documents :address :primaryOperation])))

(defn- get-linked-app-operations [foreman-app-id link]
  (let [other-id  (first (remove #{foreman-app-id} (:link link)))]
    (get-in link [(keyword other-id) :apptype])))

(defn- unwrap [wrapped-value]
  (let [value (tools/unwrapped wrapped-value)]
    (if (and (or (coll? value) (string? value)) (empty? value))
      "ei tiedossa"
      value)))

(defn- get-history-data-from-app [app-links foreman-app]
  (let [foreman-doc     (domain/get-document-by-name foreman-app "tyonjohtaja-v2")

        municipality    (:municipality foreman-app)
        difficulty      (unwrap (get-in foreman-doc [:data :patevyysvaatimusluokka]))
        foreman-role    (unwrap (get-in foreman-doc [:data :kuntaRoolikoodi]))
        limitedApproval (unwrap (get-in foreman-doc [:data :tyonjohtajanHyvaksynta :tyonjohtajanHyvaksynta]))
        limitedApproval (if limitedApproval
                          "yes"
                          nil)

        relevant-link   (first (filter #(some #{(:id foreman-app)} (:link %)) app-links))
        project-app-id  (first (remove #{(:id foreman-app)} (:link relevant-link)))
        operation       (get-linked-app-operations (:id foreman-app) relevant-link)]

    {:municipality    municipality
     :difficulty      difficulty
     :jobDescription  foreman-role
     :operation       operation
     :limitedApproval limitedApproval
     :linkedAppId     project-app-id
     :foremanAppId    (:id foreman-app)
     :created         (:created foreman-app)}))

(defn get-foreman-history-data [foreman-app]
  (let [foreman-apps       (->> (get-foreman-applications foreman-app)
                                (remove #(= (:id %) (:id foreman-app))))
        links              (mongo/select :app-links {:link {$in (map :id foreman-apps)}})]
    (map (partial get-history-data-from-app links) foreman-apps)))

(defn- reduce-to-highlights [history-group]
  (let [history-group (sort-by :created history-group)
        difficulty-values (vec (map :name (:body schemas/patevyysvaatimusluokka)))]
    (reduce (fn [highlights group]
              (if (pos? (util/compare-difficulty :difficulty difficulty-values (first highlights) group))
                (cons group highlights)
                highlights))
            nil
            history-group)))

(defn reduce-foreman-history-data [history-data]
  (let [grouped-history (group-by (juxt :municipality :jobDescription :limitedApproval) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))

(defn get-foreman-reduced-history-data [foreman-app]
  (let [history-data    (get-foreman-history-data foreman-app)
        grouped-history (group-by (juxt :municipality :jobDescription :limitedApproval) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))

(defn- select-latest-verdict-status [{:keys [verdicts]}]
  (->> (mapcat :paatokset verdicts)
       (mapcat :poytakirjat)
       (filter #(some-> % :paatospvm (< (now))))
       (apply util/max-key :paatospvm)
       (#(or (:status %) (:paatoskoodi %)))))

(defn foreman-application-info [application]
  (-> (select-keys application [:id :state :auth :documents])
      (assoc :latest-verdict-status (select-latest-verdict-status application))
      (update :documents (fn [docs] (filter #(= (get-in % [:schema-info :name]) "tyonjohtaja-v2") docs)))))

(defn get-linked-foreman-applications-by-id [id]
  (let [_ (println "CAlling me....")
        app-link-resp (mongo/select :app-links {:link {$in [id]}})
        apps-linking-to-us (filter #(= (:type ((keyword id) %)) "linkpermit") app-link-resp)
        foreman-application-links (filter #(= "tyonjohtajan-nimeaminen-v2"
                                              (get-in % [(keyword (first (:link %))) :apptype]))
                                          apps-linking-to-us)
        foreman-application-ids (map (fn [link] (first (:link link))) foreman-application-links)
        applications (mongo/select :applications {:_id {$in foreman-application-ids}} [:id :state :auth :documents])
        mapped-applications (map foreman-application-info applications)]
    (sort-by :id mapped-applications)))

(defn allow-foreman-only-in-foreman-app
  "If user has :foreman role in current application, check that the application is a forman application."
  [{:keys [application user]}]
  (when (and (auth/has-auth-role? application (:id user) :foreman)
             (not (foreman-app? application)))
    unauthorized))

(defn notice?
  "True if application is foreman application and of type notice (ilmoitus)"
  [application]
  (and
    (foreman-app? application)
    (= "tyonjohtaja-ilmoitus" (:permitSubtype application))))

(defn validate-foreman-submittable [application link-permit]
  (when-not (app/submitted? application)
    (when link-permit
      (when-not (app/submitted? link-permit)
        (fail :error.not-submittable.foreman-link)))))

(defn- validate-notice-or-application [{subtype :permitSubtype :as application}]
  (when (and (foreman-app? application) (ss/blank? subtype))
    (fail :error.foreman.type-not-selected)))

(defn- validate-notice-submittable [application link-permit]
  (when (notice? application)
    (when link-permit
      (when-not (states/post-verdict-states (keyword (:state link-permit)))
        (fail :error.foreman.notice-not-submittable)))))

(defn validate-application
  "Validates foreman applications. Returns nil if application is OK, or fail map."
  [application]
  (when (foreman-app? application)
    (let [link        (some #(when (= (:type %) "lupapistetunnus") %) (:linkPermitData application))
          link-permit (when link
                        (mongo/select-one :applications {:_id (:id link)} {:state 1}))]
      (or
        (validate-notice-or-application application)
        (validate-notice-submittable application link-permit)
        (validate-foreman-submittable application link-permit)))))

(defn new-foreman-application [{:keys [created application] :as command}]
  (-> (app/do-create-application
       (assoc command :data {:operation "tyonjohtajan-nimeaminen-v2"
                             :x (-> application :location first)
                             :y (-> application :location second)
                             :address (:address application)
                             :propertyId (:propertyId application)
                             :municipality (:municipality application)
                             :infoRequest false
                             :messages []}))
      (assoc :opened (if (util/pos? (:opened application)) created nil))))

(defn- cleanup-hakija-doc [doc]
  (let [schema-name (op/get-operation-metadata :tyonjohtajan-nimeaminen-v2 :applicant-doc-schema)]
    (-> doc
      (assoc :id (mongo/create-id))
      (assoc-in [:schema-info :name]  schema-name)
      (assoc-in [:data :henkilo :userId] {:value nil}))))

;; imported previous permit applications do not have a hankkeen-kuvaus -document
;; and have the description in the first aiemman-luvan-toimenpide -document
(defn get-application-description [application]
  (let
    [description
      (-> (domain/get-documents-by-subtype (:documents application) "hankkeen-kuvaus")
        first
        (get-in [:data :kuvaus :value]))]
    (if (and description (not (= description "")))
      description
      (or
        (-> (domain/get-document-by-name (:documents application) "aiemman-luvan-toimenpide")
         (get-in [:data :kuvaus :value]))
        ""))))

(defn update-foreman-docs [foreman-app application role]
  (let [hankkeen-kuvaus      (get-application-description application)
        hankkeen-kuvaus-doc  (first (domain/get-documents-by-subtype (:documents foreman-app) "hankkeen-kuvaus"))
        hankkeen-kuvaus-doc  (if hankkeen-kuvaus
                               (assoc-in hankkeen-kuvaus-doc [:data :kuvaus :value] hankkeen-kuvaus)
                               hankkeen-kuvaus-doc)

        tyonjohtaja-doc      (domain/get-document-by-name foreman-app "tyonjohtaja-v2")
        tyonjohtaja-doc      (if-not (ss/blank? role)
                               (assoc-in tyonjohtaja-doc [:data :kuntaRoolikoodi :value] role)
                               tyonjohtaja-doc)

        hakija-docs          (domain/get-applicant-documents (:documents application))
        hakija-docs          (map cleanup-hakija-doc hakija-docs)]
    (->> (:documents foreman-app)
         (remove (comp #{:tyonjohtaja-v2} keyword :name :schema-info))
         (remove (comp #{:hankkeen-kuvaus :hakija} keyword :subtype :schema-info))
         (concat hakija-docs [hankkeen-kuvaus-doc tyonjohtaja-doc])
         (remove nil?)
         (assoc foreman-app :documents))))

(defn- applicant-user-auth [applicant path auth]
  (when-let [applicant-user (some-> applicant
                                    (get-in path)
                                    ss/canonize-email
                                    usr/get-user-by-email)]
    ;; Create invite for applicant if authed
    (when (some (partial usr/same-user? applicant-user) auth)
      {:email (:email applicant-user)
       :role "writer"})))

(defn- henkilo-invite [applicant auth]
  {:pre [(= "henkilo" (get-in applicant [:data :_selected]))
         (sequential? auth)]}
  (applicant-user-auth applicant [:data :henkilo :yhteystiedot :email] auth))

(defn- yritys-invite [applicant auth]
  {:pre [(= "yritys" (get-in applicant [:data :_selected]))
         (sequential? auth)]}
  (if-let [company-id (get-in applicant [:data :yritys :companyId])]
    ;; invite the Company if it's authed
    (some
      #(when (= company-id (or (:id %) (get-in % [:invite :user :id])))
         {:company-id company-id})
      auth)
    (applicant-user-auth applicant [:data :yritys :yhteyshenkilo :yhteystiedot :email] auth)))

(defn applicant-invites [documents auth]
  (->> (domain/get-applicant-documents documents)
       (tools/unwrapped)
       (map #(case (-> % :data :_selected)
               "henkilo" (henkilo-invite % auth)
               "yritys"  (yritys-invite % auth)))
       (remove nil?)))

(defn- create-company-auth [inviter company-id]
  (when-let [company (company/find-company-by-id company-id)]
    ;; company-auth can be nil if company is locked
    (when-let [company-auth (company/company->auth company :writer)]
      (assoc company-auth
             :inviter (usr/summary inviter)))))

(defn- invite->auth [inv app-id inviter timestamp]
  (if (:company-id inv)
    (create-company-auth inviter (:company-id inv))
    (let [invited (usr/get-or-create-user-by-email (:email inv) inviter)]
      (auth/create-invite-auth inviter invited app-id (:role inv) timestamp))))

(defn copy-auths-from-linked-app [foreman-app linked-app user created]
  (->> (:auth linked-app)
       (applicant-invites (:documents foreman-app))
       (remove #(= (:email %) (:email user)))
       (mapv #(invite->auth % (:id foreman-app) user created))
       (remove nil?)
       (update foreman-app :auth concat)))

(defn add-foreman-invite-auth [foreman-app foreman-user user created]
  (if (and foreman-user (not (auth/has-auth? foreman-app (:id foreman-user))))
    (->> (auth/create-invite-auth user foreman-user (:id foreman-app) "foreman" created)
         (update foreman-app :auth conj))
    foreman-app))

(defn- invite-to-linked-app! [linked-app foreman-user {user :user created :created :as command}]
  (update-application command
                      {:auth {$not {$elemMatch {:invite.user.username (:email foreman-user)}}}}
                      {$push {:auth (auth/create-invite-auth user foreman-user (:id linked-app) "foreman" created)}
                       $set  {:modified created}}))

(defn send-invite-notifications! [foreman-app foreman-user linked-app command]
  (try
    (when (and foreman-user (not (auth/has-auth? linked-app (:id foreman-user))))
      (invite-to-linked-app! linked-app foreman-user command))
    (copy-app/send-invite-notifications! foreman-app command)
    (catch Exception e
      (error "Error when inviting to foreman application:" e))))

(defn update-foreman-task-on-linked-app! [application {foreman-app-id :id} task-id {created :created}]
  (when-let [task (util/find-by-id task-id (:tasks application))]
    (doc-persistence/persist-model-updates application "tasks" task [[[:asiointitunnus] foreman-app-id]] created)))

(defn get-linked-foreman-applications [{:keys [id]}]
  (let [foreman-application-ids (->> (mongo/select :app-links {:link {$in [id]}})
                                     (filter (comp #{"linkpermit"} :type (keyword id)))
                                     (filter (comp #{"tyonjohtajan-nimeaminen-v2"} :apptype #((->> % :link first keyword) %)))
                                     (map (comp first :link)))]
    (->> (mongo/select :applications {:_id {$in foreman-application-ids}} [:id :state :auth :documents :verdicts])
         (map foreman-application-info))))
