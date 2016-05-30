(ns lupapalvelu.foreman
  (:require [clojure.set :as set]
            [taoensso.timbre :as timbre :refer [error]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as company]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.operations :as op]
            [monger.operators :refer :all]))

(defn ensure-foreman-not-linked [{{foreman-app-id :foremanAppId task-id :taskId} :data} {tasks :tasks}]
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
                     :state {$nin ["canceled"]}
                     :documents {$elemMatch {"schema-info.name"              "tyonjohtaja-v2"
                                             "data.henkilotiedot.hetu.value" foreman-hetu}}}
                    [:created :documents :municipality]))))

(defn get-foreman-project-applications
  "Based on the passed foreman application, fetches all project applications that have the same foreman as in
  the passed foreman application (personal id is used as key). Returns all the linked applications as a list"
  [foreman-application foreman-hetu]
  (let [foreman-apps    (->> (get-foreman-applications foreman-application foreman-hetu)
                             (remove #(= (:id %) (:id foreman-application))))
        foreman-app-ids (map :id foreman-apps)
        links           (mongo/select :app-links {:link {$in foreman-app-ids}})
        linked-app-ids  (remove (set foreman-app-ids) (distinct (mapcat #(:link %) links)))]
    (mongo/select :applications {:_id {$in linked-app-ids}} [:documents :address :primaryOperation])))

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

(defn get-foreman-reduced-history-data [foreman-app]
  (let [history-data    (get-foreman-history-data foreman-app)
        grouped-history (group-by (juxt :municipality :jobDescription :limitedApproval) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))

(defn foreman-application-info [application]
  (-> (select-keys application [:id :state :auth :documents])
      (update-in [:documents] (fn [docs] (filter #(= (get-in % [:schema-info :name]) "tyonjohtaja-v2") docs)))))

(defn foreman-app? [application] (= :tyonjohtajan-nimeaminen-v2 (-> application :primaryOperation :name keyword)))

(defn notice?
  "True if application is foreman application and of type notice (ilmoitus)"
  [application]
  (and
    (foreman-app? application)
    (= "tyonjohtaja-ilmoitus" (:permitSubtype application))))

(defn- validate-notice-or-application [{subtype :permitSubtype :as application}]
  (when (and (foreman-app? application) (ss/blank? subtype))
    (fail :error.foreman.type-not-selected)))

(defn- validate-notice-submittable [{:keys [primaryOperation linkPermitData] :as application}]
  (when (notice? application)
    (when-let [link (some #(when (= (:type %) "lupapistetunnus") %) linkPermitData)]
      (when-not (states/post-verdict-states (keyword
                                              (get
                                                (mongo/select-one :applications {:_id (:id link)} {:state 1})
                                                :state)))
        (fail :error.foreman.notice-not-submittable)))))

(defn validate-application
  "Validates foreman applications. Returns nil if application is OK, or fail map."
  [application]
  (when (foreman-app? application)
    (or
      (validate-notice-or-application application)
      (validate-notice-submittable application))))

(defn new-foreman-application [{:keys [created user application] :as command}]
  (-> (application/do-create-application
        (assoc command :data {:operation "tyonjohtajan-nimeaminen-v2"
                              :x (-> application :location first)
                              :y (-> application :location second)
                              :address (:address application)
                              :propertyId (:propertyId application)
                              :municipality (:municipality application)
                              :infoRequest false
                              :messages []}))
    (assoc :opened (if (util/pos? (:opened application)) created nil))))


(defn- cleanup-hakija-doc [{info :schema-info :as doc}]
  (let [schema-name (op/get-operation-metadata :tyonjohtajan-nimeaminen-v2 :applicant-doc-schema)]
    (-> doc
      (assoc :id (mongo/create-id))
      (assoc-in [:schema-info :name]  schema-name)
      (assoc-in [:data :henkilo :userId] {:value nil}))))

(defn update-foreman-docs [foreman-app application role]
  (let [hankkeen-kuvaus      (-> (domain/get-documents-by-subtype (:documents application) "hankkeen-kuvaus") first (get-in [:data :kuvaus :value]))
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
         (remove (comp #{"tyonjohtaja-v2"} :name :schema-info))
         (remove (comp #{"hankkeen-kuvaus" "hakija"} :subtype :schema-info))
         (concat hakija-docs [hankkeen-kuvaus-doc tyonjohtaja-doc])
         (remove nil?)
         (assoc foreman-app :documents))))

(defn- applicant-user-auth [applicant path auth]
  (when-let [applicant-user (some-> applicant
                                    (get-in path)
                                    usr/canonize-email
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

(defn create-company-auth [company-id]
  (when-let [company (company/find-company-by-id company-id)]
    (assoc
      (company/company->auth company)
      :id "" ; prevents access to application before accepting invite
      :role "reader"
      :invite {:user {:id company-id}})))


(defn- invite->auth [inv app-id inviter timestamp]
  (if (:company-id inv)
    (create-company-auth (:company-id inv))
    (let [invited (usr/get-or-create-user-by-email (:email inv) inviter)]
      (auth/create-invite-auth inviter invited app-id (:role inv) timestamp))))

(defn copy-auths-from-linked-app [foreman-app foreman-user linked-app user created]
  (->> (:auth linked-app)
       (applicant-invites (:documents foreman-app))
       (remove #(= (:email %) (:email user)))
       (mapv #(invite->auth % (:id foreman-app) user created))
       (update foreman-app :auth concat)))

(defn add-foreman-invite-auth [foreman-app foreman-user user created]
  (if (and foreman-user (not (auth/has-auth? foreman-app (:id foreman-user))))
    (->> (auth/create-invite-auth user foreman-user (:id foreman-app) "foreman" created)
         (update foreman-app :auth conj))
    foreman-app))

(defn create-foreman-application-with-docs [command linked-application foreman-role]
  (-> (new-foreman-application command)
      (update-foreman-docs linked-application foreman-role)))

(defn- invite-company! [foreman-app {user :user} auth]
  (let [company-id (get-in auth [:invite :user :id])
        token-id   (company/company-invitation-token user company-id (:id foreman-app))]
    (notif/notify! :accept-company-invitation {:admins     (company/find-company-admins company-id)
                                               :caller     user
                                               :company    (company/find-company! {:id company-id})
                                               :link-fi    (str (env/value :host) "/app/fi/welcome#!/accept-company-invitation/" token-id)
                                               :link-sv    (str (env/value :host) "/app/sv/welcome#!/accept-company-invitation/" token-id)})))

(defn- user-invite-notifications! [foreman-app auths]
  (->> (map #(set/rename-keys % {:username :email}) auths)
       (hash-map :application foreman-app :recipients)
       (notif/notify! :invite)))

(defn- invite-notifications! [{auths :auth :as foreman-app} command]
  (let [invite-auths (remove (comp #{:owner} :role) auths)]
    ;; Non-company invites
    (->> (remove (comp #{"company"} :type) invite-auths)
         (user-invite-notifications! foreman-app))
    ;; Company invites
    (->> (filter (comp #{"company"} :type) invite-auths)
         (run! (partial invite-company! foreman-app command)))))

(defn- invite-to-linked-app! [linked-app foreman-user {user :user created :created :as command}]
  (update-application command
                      {:auth {$not {$elemMatch {:invite.user.username (:email foreman-user)}}}}
                      {$push {:auth (auth/create-invite-auth user foreman-user (:id linked-app) "foreman" created)}
                       $set  {:modified created}})
  (notif/notify! :invite {:application linked-app :recipients [foreman-user]}))

(defn send-invite-notifications! [foreman-app foreman-user linked-app {user :user created :created :as command}]
  (try
    (when (and foreman-user (not (auth/has-auth? linked-app (:id foreman-user))))
      (invite-to-linked-app! linked-app foreman-user command))
    (invite-notifications! foreman-app command)
    (catch Exception e
      (error "Error when inviting to foreman application:" e))))

(defn update-foreman-task-on-linked-app! [application {foreman-app-id :id} task-id {created :created}]
  (when-let [task (util/find-by-id task-id (:tasks application))]
    (doc-persistence/persist-model-updates application "tasks" task [[[:asiointitunnus] foreman-app-id]] created)))
