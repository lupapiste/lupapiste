(ns lupapalvelu.domain
  (:require [clojure.set :refer [difference]]
            [lupapalvelu.attachment.accessibility :as attachment-access]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.backing-system.krysp.verdict :as verdict]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.elasticsearch.query :as es-query]
            [lupapalvelu.logging :refer [warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [unauthorized]]
            [sade.strings :as ss]
            [sade.util :as util]))

;;
;; application mongo querys
;;

(defn organization-optimize-query
  "If there is only one org, optimize to be equal else use $in"
  [organizations]
  (if (< 1 (count organizations))
    {$in organizations}
    (first organizations)))

(defn auth-id-query [{:keys [id company]}]
  {:auth.id (if (ss/blank? (:id company))
              id
              {$in [id (:id company)]})})

(defn auth-id-es-query [{user-id :id {company-id :id company-role :role} :company}]
  (cond
    ;; User is not associated with a company
    (ss/blank? company-id) (es-query/nested-term :auth :id user-id)

    ;; User is associated with a company. This is the same logic that is implemented in
    ;; auth/application-allowed-for-company-user? predicate for mongo applications search.

    ;; User is a company admin => Sees all applications in which the company is authorized to
    (= company-role "admin") (es-query/nested-term :auth :id [user-id company-id])

    ;; User is NOT a company admin => Sees all applications in which the company is authorized to
    ;; and company role does not exist. company-role in application auth is "admin" when
    ;; invitation has not been accepted.
    :else (es-query/es-or [(es-query/nested-term :auth :id user-id)
                           {:nested
                            {:path  :auth
                             :query {:bool {:must     {:term {:auth.id company-id}}
                                            :must_not {:exists {:field :auth.company-role}}}}}}])))

(defn basic-application-es-query-for [user]
  (let [organizations (usr/organization-ids-by-roles user roles/authorized-to-orgs-roles)
        auth-id-q     (auth-id-es-query user)]
    (case (keyword (:role user))
      :applicant          auth-id-q
      :authority          (if (empty? organizations)
                            auth-id-q
                            (es-query/es-or [(es-query/term :organization organizations)
                                             auth-id-q]))
      :rest-api           (es-query/term :organization organizations)
      :oirAuthority       (es-query/term :organization organizations)
      :trusted-etl        {}
      :trusted-salesforce {}
      :financialAuthority auth-id-q
      (do
        (warnf "invalid role to get applications: user-id: %s, role: %s" (:id user) (:role user))
        {:bool {:must_not [{:exists {:field :id}}]}})))) ; should not yield any results

(defn basic-application-query-for [user]
  (let [organizations (usr/organization-ids-by-roles user roles/authorized-to-orgs-roles)
        auth-id-q     (auth-id-query user)]
    (case (keyword (:role user))
      :applicant          auth-id-q
      :authority          (if (empty? organizations)
                            auth-id-q
                            {$or [{:organization (organization-optimize-query organizations)}
                                  auth-id-q]})
      :rest-api           {:organization (organization-optimize-query organizations)}
      :asiointi-api       {:organization (organization-optimize-query organizations)}
      :oirAuthority       {:organization (organization-optimize-query organizations)}
      :trusted-etl        {}
      :trusted-salesforce {}
      :financialAuthority auth-id-q
      (do
        (warnf "invalid role to get applications: user-id: %s, role: %s" (:id user) (:role user))
        {:_id nil})))) ; should not yield any results

(defn applications-with-writer-authz-query-for [user]
  {:auth {$elemMatch {:id (:id user) :role {$in [:writer :foreman]}}}})

(defn application-query-for [user]
  (merge
    (basic-application-query-for user)
    (case (keyword (:role user))
      :applicant {:state {$nin ["canceled"]}}
      :authority {:state {$nin ["canceled"]}}
      :oirAuthority {:state {$in ["info" "answered"]} :openInfoRequest true}
      :financialAuthority {:state {$nin ["canceled"]}}
      {})))

(defn- only-authority-sees [user checker items]
  (filterv (fn [m] (or (usr/authority? user) (not (checker m)))) items))

(defn- only-authority-sees-drafts [user verdicts]
  (only-authority-sees user :draft verdicts))

(defn- non-authority-sees-only-published-pate-verdicts [user pate-verdicts]
  (cond->> pate-verdicts
    (usr/applicant? user) (filter :published)))

(defn- normalize-neighbors
  "Neighbor user ids (hetu and eidas id) are visible only to the application handlers."
  [application user neighbors]
  (cond->> neighbors
    (not (auth/application-handler? application user))
    (mapv (fn [neighbor]
            (update neighbor
                    :status
                    (partial mapv #(cond-> %
                                     (:vetuma %)
                                     (update :vetuma dissoc :userid :eidasId))))))))


(defn- filter-notice-from-application [application user]
  (if (usr/authority? user)
    application
    (dissoc application :urgency :authorityNotice)))

(defn- pick-user-company-notes
  "Filters company notes for user without changing original :company-notes structure (notes returned in a vector)."
  [{{company-id :id} :company} company-notes]
  (filterv (comp #{company-id} :companyId) company-notes))

(defn- relates-to-draft-verdict?
  "Backing system verdicts (in verdicts) can never be drafts."
  [{verdicts :pate-verdicts} {target :target source :source}]
  (let [draft-verdict-ids (->> verdicts
                               (remove :published)
                               (map :id)
                               set) ]
    (or (and (= (:type target) "verdict") (contains? draft-verdict-ids (:id target)))
        (and (= (:type source) "verdicts") (contains? draft-verdict-ids (:id source))))))

(defn- authorized-to-statement? [user statement]
  (or (:given statement)
      (usr/authority? user)
      (usr/financial-authority? user)
      (and (usr/applicant? user)
           (= (-> statement :person :email ss/canonize-email)
              (-> user :email ss/canonize-email)))))

(defn- statement-attachment-hidden-for-user? [{statements :statements} user {target :target}]
  (and (= (:type target) "statement")
       (not (->> (util/find-by-id (:id target) statements) (authorized-to-statement? user)))))

(defn- statement-summary [statement]
  (select-keys statement [:id :person :requested :given :state :external]))

(defn- can-read-comments? [app user]
  (or (auth/user-authz? roles/comment-user-authz-roles app user)
      (auth/has-organization-authz-roles? roles/commenter-org-authz-roles (:organization app) user)
      (auth/has-auth? app (get-in user [:company :id]))))

(defn- can-read-comment? [{:keys [roles]} {:keys [role]}]
  (let [effective-role (if (= (keyword role) :financialAuthority)
                         "applicant"
                         (name role))]
    ((set roles) effective-role)))

(defn- attachment-ids [application]
  (->> (:attachments application)
       (map :id)
       set))

(defn flag-removed-attachment-comments
  "Flags comments relating to removed attachments. Called before any
  attachment filtering. See filter-application-content-for function in
  domain.clj for details. The flags are used when removing
  comments (see below)."
  [application]
  (let [attachment-ids (attachment-ids application)]
    (update application :comments (fn [comments]
                                    (map (fn [{target :target :as comment}]
                                           (cond-> comment
                                             (and (util/=as-kw (:type target) :attachment)
                                                  (not (contains? attachment-ids (:id target))))
                                             (assoc :removed true)))
                                         comments)))))

(defn cleanup-attachment-comments
  "An attachment comment is removed if it is either a) related to an
  attachment that the user is not allowed to see, or b) the attachment
  is removed but the comment text is blank. Called after the
  attachments have been filtered. In other words, the non-removed
  missing attachments are considered hidden."
  [application]
  (let [attachment-ids (attachment-ids application)]
    (update application :comments (fn [comments]
                                    (remove (fn [{:keys [target removed text]}]
                                              (and (util/=as-kw (:type target) :attachment)
                                                   (not (contains? attachment-ids (:id target)))
                                                   (or (not removed) ; hidden
                                                       (and removed (ss/blank? text)))))
                                            comments)))))


(defn filter-application-content-for [application user]
  (when (seq application)
    (-> application
        (update-in [:comments] (partial filter (fn [comment] (and
                                                               (can-read-comments? application user)
                                                               (can-read-comment? comment user)))))
        flag-removed-attachment-comments
        (update-in [:verdicts] (partial only-authority-sees-drafts user))
        (update-in [:pate-verdicts] (partial non-authority-sees-only-published-pate-verdicts user))
        (update-in [:statements] (partial map #(if (authorized-to-statement? user %) % (statement-summary %))))
        (update-in [:attachments] (partial remove (partial statement-attachment-hidden-for-user? application user)))
        (update-in [:attachments] (partial only-authority-sees user (partial relates-to-draft-verdict? application)))
        (update-in [:attachments] (partial attachment-access/filter-attachments-for user application))
        (update-in [:neighbors] (partial normalize-neighbors application user))
        cleanup-attachment-comments
        (update-in [:tasks] (partial only-authority-sees user (partial relates-to-draft-verdict? application)))
        (update-in [:company-notes] (partial pick-user-company-notes user))
        (filter-notice-from-application user))))

(defn enrich-application-handlers
  "Note that this function provides the [[:general]] flag, which some handler-processing functions expect."
  ([{org-id :organization :as application}]
   (enrich-application-handlers application {:handler-roles (lazy-seq (:handler-roles (mongo/select-one :organizations {:_id org-id} [:handler-roles])))}))
  ([application {roles :handler-roles}]
   (update application :handlers (partial map #(merge (util/find-by-id (:roleId %) roles) %)))))

(defn enrich-application-tags [{app-tags :tags :as application} {org-tags :tags}]
  (->> (map (util/key-by :id org-tags) app-tags)
       (remove nil?)
       (assoc application :tags)))

(defn- enrich-application [application]
  (some-> application
          (update :documents (partial map schemas/with-current-schema-info))
          (update :tasks (partial map schemas/with-current-schema-info))
          (enrich-application-handlers)))

(defn get-application-as [query-or-id user & {:keys [include-canceled-apps?] :or {include-canceled-apps? false}}]
  {:pre [query-or-id (map? user)]}
  (let [query-id-part (if (map? query-or-id) query-or-id {:_id query-or-id})
        query-user-part (if include-canceled-apps?
                          (update-in (application-query-for user) [:state $nin] #(difference (set %) #{"canceled"}))
                          (application-query-for user))]

    (some-> (mongo/select-one :applications {$and [query-id-part query-user-part]})
            (enrich-application)
            (filter-application-content-for user)
            (domain-att/add-attachment-map))))

(defn get-application-no-access-checking
  ([query-or-id]
   {:pre [query-or-id]}
   (get-application-no-access-checking query-or-id {}))
  ([query-or-id projection]
   (let [query (if (map? query-or-id) query-or-id {:_id query-or-id})]
     (->> (mongo/select-one :applications query projection)
          enrich-application))))

(defn get-foreman-doc-application-no-access-checking
  ([doc-id org-id]
   (->> (mongo/select-one :applications {:documents    {$elemMatch {:id doc-id}}
                                         :organization org-id}))))

(defn get-multiple-applications-no-access-checking
  ([query-or-ids]
   {:pre [(coll? query-or-ids)]}
   (get-multiple-applications-no-access-checking query-or-ids {}))
  ([query-or-ids projection]
   (let [query (if (map? query-or-ids) query-or-ids {:_id {$in query-or-ids}})]
     (->> (if (seq projection)
            (mongo/select :applications query projection)
            (mongo/select :applications query))
          (map enrich-application)))))

;;
;; authorization
;;

(def write-roles ["writer" "foreman"])

(defn write-access? [application user-id]
  (auth/has-some-auth-role? application user-id write-roles))

(defn validate-access
  "Command pre-check for validating user roles in application auth array."
  [allowed-roles {:keys [application user]}]
  (when-not (or (auth/has-some-auth-role? application (:id user) allowed-roles)
                (auth/has-some-auth-role? application (get-in user [:company :id]) allowed-roles))
    unauthorized))

(defn validate-write-access
  "Validator: current user must be owner or have write access.
   To be used in commands' :pre-checks vector."
  [command]
  (validate-access write-roles command))

;;
;; documents
;;

(defn- docs-from [application-or-documents]
  (if (map? application-or-documents)
    (:documents application-or-documents)
    application-or-documents))

(defn get-document-by-id
  "returns first document from application with the document-id"
  [application-or-documents document-id]
  (let [documents (docs-from application-or-documents)]
    (util/find-by-id document-id documents)))

(defn- documents-by-schema-info [application-or-documents k v]
  (let [documents (docs-from application-or-documents)]
    (filter (comp (partial = (keyword v)) keyword k :schema-info) documents)))

(defn get-documents-by-name
  "returns document from application by schema name"
  [application-or-documents schema-name]
  (documents-by-schema-info application-or-documents :name schema-name))

(defn get-documents-by-type
  "returns document from application by schema type"
  [application-or-documents schema-type]
  (documents-by-schema-info application-or-documents :type schema-type))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application-or-documents schema-name]
  (first (get-documents-by-name application-or-documents schema-name)))

(defn get-document-by-type
  "returns first document from application by schema type"
  [application-or-documents schema-type]
  (first (get-documents-by-type application-or-documents schema-type)))

(defn get-document-by-operation
  "returns first document from application that is associated with the operation"
  [application-or-documents operation]
  (let [op-id (if (map? operation) (:id operation) operation)
        documents (docs-from application-or-documents)]
    (first (filter #(= op-id (get-in % [:schema-info :op :id])) documents))))

(defn get-subtype [{:keys [schema-info]}]
  (when (:subtype schema-info)
    (name (:subtype schema-info))))

(defn get-documents-by-subtype
  "Returns documents of given subtype"
  [documents subtype]
  {:pre [(sequential? documents)]}
  (filter (comp (partial = (name subtype)) get-subtype) documents))

(defn get-document-by-subtype
  "Returns the first document with the given subtype (keyword or string)."
  [application-or-documents subtype]
  (some-> (:documents application-or-documents application-or-documents)
          (get-documents-by-subtype subtype)
          first))

(defn get-building-documents
  "Return all documents with `:building?` set to `true`, or `nil` if none found."
  [{:keys [documents]}]
  (->> documents
       (filterv (fn [document] (-> document :schema-info :building?)))
       not-empty))

(defn get-applicant-documents
  "returns applicant documents from given application documents"
  [documents]
  {:pre [(sequential? documents)]}
  (get-documents-by-subtype documents "hakija"))

(defn get-applicant-document
  "returns first applicant document from given application documents"
  [documents]
  (first (get-applicant-documents documents)))

(defn company? [doc]
  (= "yritys" (-> doc :data :_selected :value)))

(defn get-party-info-from-doc [doc]
  (-> doc
      (get-in [:data (if (company? doc) :yritys :henkilo)])
      tools/unwrapped))

(defn get-applicant-info-from-doc [doc]
  (let [data      (if (company? doc)
                    (-> doc :data :yritys :yhteyshenkilo)
                    (-> doc :data :henkilo))]
    {:id        (-> data :userId :value)
     :email     (-> data :yhteystiedot :email :value)
     :firstName (-> data :henkilotiedot :etunimi :value)
     :lastName  (-> data :henkilotiedot :sukunimi :value)}))

(defn invites [{auth :auth}]
  (->> (filter :invite auth)
       (map (fn [{invite :invite inviter :inviter auth-type :type}]
              (merge {:inviter inviter :type auth-type} invite)))))

(defn no-pending-invites? [application user-id]
  (not-any? #(= user-id (-> % :user :id)) (invites application)))

(defn- building-product-catalogs-from [application-or-building-product-catalogs]
  (if (map? application-or-building-product-catalogs)
    (:building-product-catalogs application-or-building-product-catalogs)
    application-or-building-product-catalogs))

(defn- building-product-catalogs-by-schema-info [application-or-building-product-catalogs k v]
  (let [building-product-catalogs (building-product-catalogs-from application-or-building-product-catalogs)]
    (filter (comp (partial util/=as-kw v) k :schema-info) building-product-catalogs)))

(defn get-building-product-catalogs-by-name
  "returns building-product-catalog from application by schema name"
  [application-or-building-product-catalogs schema-name]
  (building-product-catalogs-by-schema-info application-or-building-product-catalogs :name schema-name))

(defn get-building-product-catalog-by-name
  "returns first building-product-catalog from application by schema name"
  [application-or-building-product-catalogs schema-name]
  (first (get-building-product-catalogs-by-name application-or-building-product-catalogs schema-name)))

;;
;; Verdict model
;;

(defn ->paatos
  "Returns a verdict data structure, compatible with KRYSP schema"
  [{:keys [verdictId backendId timestamp name given status official text section draft agreement metadata paatos-id]}]
  (let [verdict-id (or verdictId (mongo/create-id))]
    {:id verdict-id
     :kuntalupatunnus backendId
     :draft (if (nil? draft) false draft)
     :timestamp timestamp
     :sopimus agreement ; not in KRYSP
     :metadata metadata ; not in KRYSP?
     :paatokset [{:id          (or paatos-id (mongo/create-id))
                  :paivamaarat {:anto             given
                                :lainvoimainen    official}
                  :poytakirjat [{:paatoksentekija name
                                 :urlHash         verdict-id
                                 :status          status
                                 :paatos          text ; Only in rakennusvalvonta KRYSP
                                 :paatospvm       given
                                 :pykala          section
                                 :paatoskoodi     (when status (verdict/verdict-name status))}]}]}))

;;
;; Comment model
;;

(defn user-comment-type
  "User role within the application. Either `:authority` or `:applicant`."
  [user {:keys [organization]}]
  (if (usr/user-is-authority-in-organization? user organization)
    :authority
    :applicant))

(defn ->comment [text target type user to-user timestamp roles]
  {:pre [(or (nil? text) (string? text)) (map? target)
         type (map? user) (or (nil? to-user) (:role to-user))
         (number? timestamp) (or (sequential? roles) (set? roles))]}

  {:text    (ss/trim text)
   ;; target key order seems to be significant in MongoDB updates
   :target  (if (:id target)
              {:type (:type target) :id (:id target)}
              {:type (:type target)})
   :type    type
   :created timestamp
   :roles   (if to-user (conj (set roles) (:role to-user)) roles)
   :to      (usr/summary to-user)
   :user    (usr/summary user)})

;;
;; Neighbors
;;
(def neighbor-skeleton
  {:id ""
   :propertyId ""
   :owner {:type nil
           :name nil
           :businessID nil
           :nameOfDeceased nil
           :address {:street nil :zip nil :city nil}}
   :status []})

;;
;; Application skeleton with default values
;;

(def application-skeleton
  {:_applicantIndex             []
   :_attachment_indicator_reset nil ; timestamp
   :_comments-seen-by           {}
   :_statements-seen-by         {}
   :_verdicts-seen-by           {}
   :acknowledged                nil ; timestamp
   :address                     ""
   :applicant                   ""
   :attachments                 []
   :auth                        []
   :authorityNotice             ""
   :building-product-catalogs   []
   :buildings                   []
   :closed                      nil ; timestamp
   :closedBy                    {}
   :convertedToApplication      nil ; timestamp
   :comments                    []
   :complementNeeded            nil ; timestamp
   :created                     nil ; timestamp
   :documents                   []
   :drawings                    []
   :foreman                     ""
   :foremanRole                 ""
   :handlers                    []
   :history                     [] ; state transition audit log
   :infoRequest                 false
   :location                    {}
   :location-wgs84              {}
   :modified                    nil ; timestamp
   :municipality                ""
   :neighbors                   []
   :opened                      nil ; timestamp
   :openInfoRequest             false
   :primaryOperation            nil
   :secondaryOperations         []
   :options                     {}
   :organization                ""
   :propertyId                  ""
   :permitSubtype               ""
   :permitType                  ""
   :schema-version              1 ; Long
   :sent                        nil ; timestamp
   :started                     nil ; construction started
   :agreementPrepared           nil ; agreement prepared YA
   :agreementSigned             nil ; Agreemetn signed YA, terminal
   :finished                    nil ; Finished  YA
   :startedBy                   {}
   :state                       ""
   :statements                  []
   :submitted                   nil ; timestamp
   :suti                        {:id nil :added false}
   :tasks                       []
   :title                       ""
   :transfers                   []
   :urgency                     "normal"
   :verdicts                    []
   :tosFunction                 nil
   :metadata                    {}
   :processMetadata             {}
   :appeals                     []
   :appealVerdicts              []
   :archived                    {:initial     nil
                                 :application nil
                                 :completed   nil}
   :warrantyStart               nil ; timestamp
   :warrantyEnd                 nil
   :bulletinOpDescription       nil
   :inspection-summaries        []})

(def operation-skeleton
  {:name ""
   :description nil
   :created nil})
