(ns lupapalvelu.application-meta-fields
  (:require [taoensso.timbre :as timbre :refer [tracef debug debugf info warn error]]
            [monger.operators :refer :all]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.user :as usr]
            [lupapalvelu.attachment.util :as att-util]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))


(defn in-post-verdict-state? [_ app] (contains? states/post-verdict-states (keyword (:state app))))

(defn select-tasks-tab? [_ app]
  (let [primaryOperation (keyword (get-in app [:primaryOperation :name]))]
    (and
      (false? (contains? #{:tyonjohtajan-nimeaminen-v2 :ya-jatkoaika
                           :raktyo-aloit-loppuunsaat :jatkoaika} primaryOperation))
      (contains? #{:R :YA} (-> app :permitType keyword)))))

(defn- full-name [first-name last-name]
  (ss/trim (str last-name \space first-name)))

(defn applicant-name-from-doc-first-last [document]
  (when-let [body (:data document)]
    (if (= (get-in body [:_selected :value]) "yritys")
      (get-in body [:yritys :yritysnimi :value])
      (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
        (ss/trim (str (:value first-name) \space (:value last-name)))))))

(defn applicant-name-from-doc [document]
  (when-let [body (:data document)]
    (if (= (get-in body [:_selected :value]) "yritys")
      (get-in body [:yritys :yritysnimi :value])
      (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
        (ss/trim (str (:value last-name) \space (:value first-name)))))))

(defn applicant-index [application]
  (let [applicants (remove ss/blank? (map applicant-name-from-doc (domain/get-applicant-documents (:documents application))))
        applicant  (first applicants)]
    (tracef "applicant: '%s', applicant-index: %s" applicant applicants)
    {:applicant applicant
     :_applicantIndex applicants}))

(defn applicant-index-update [application]
  {$set (applicant-index application)})

(defn- designer-name-from-doc [document]
  (when (= "suunnittelija" (str (get-in document [:schema-info :subtype])))
    (when-let [henkilo (get-in document [:data :henkilotiedot])]
      (let [{first-name :etunimi last-name :sukunimi} henkilo]
        (ss/trim (str (:value last-name) \space (:value first-name)))))))

(defn designers-index [application]
  (let [designers (remove ss/blank? (map designer-name-from-doc (:documents application)))
        index (if (seq designers) designers [])]
    (tracef "designers: %s" index)
    {:_designerIndex index}))

(defn designers-index-update [application]
  {$set (designers-index application)})

(defn get-applicant-phone [_ app]
  (let [applicant-auth (first (auth/get-auths-by-role app :writer))]
    (->> (if (= :company (keyword (:type applicant-auth)))
           (usr/get-user {:company.id (:id applicant-auth) :company.role "admin"})
           (usr/get-user-by-id (:id applicant-auth)))
         :phone)))

(defn get-applicant-companies [_ app]
  (let [companies (auth/get-company-auths app)]
    (map :name companies)))

(defn foreman-name-from-doc [doc]
  (let [first-name (get-in doc [:data :henkilotiedot :etunimi :value])
        last-name (get-in doc [:data :henkilotiedot :sukunimi :value])]
    (full-name first-name last-name)))

(defn foreman-role-from-doc [doc]
  (get-in doc [:data :kuntaRoolikoodi :value]))

(defn- foreman-doc-from-application [application]
  (or (domain/get-document-by-name application :tyonjohtaja-v2)
      (domain/get-document-by-name application :tyonjohtaja)))

(defn foreman-index [application]
  (let [foreman-doc (foreman-doc-from-application application)]
    {:foreman (foreman-name-from-doc foreman-doc)
     :foremanRole (foreman-role-from-doc foreman-doc)}))

(defn foreman-index-update [application]
  {$set (foreman-index application)})

(defn- project-description-from-doc [application]
  (when-let [document (first (domain/get-documents-by-subtype (:documents application) "hankkeen-kuvaus"))]
    (if (ss/starts-with (get-in document [:schema-info :name]) "hankkeen-kuvaus")
      (get-in document [:data :kuvaus :value])              ;; R type of document structure
      (get-in document [:data :kayttotarkoitus :value]))))  ;; YA type of document structure

(defn project-description-index [application]
  (let [description-index (project-description-from-doc application)]
    (tracef "project description index: %s" description-index)
    {:_projectDescriptionIndex description-index}))

(defn update-project-description-index [application]
  {$set (project-description-index application)})

(defn- count-unseen-comments [user app]
  (let [last-seen (get-in app [:_comments-seen-by (keyword (:id user))] 0)]
    (count (filter (fn [comment]
                     (and (> (:created comment) last-seen)
                          (not= (get-in comment [:user :id]) (:id user))
                          (not (ss/blank? (:text comment)))))
                   (:comments app)))))

(defn- unseen-notice? [user {:keys [authorityNoticeEdited _authority-notices-seen-by]}]
  (boolean (and (usr/authority? user)
                (and (number? authorityNoticeEdited)
                    (< (get-in _authority-notices-seen-by [(keyword (:id user))] 0)
                       authorityNoticeEdited)))))

(defn- count-unseen-statements [user app]
  (if-not (:infoRequest app)
    (let [last-seen (get-in app [:_statements-seen-by (keyword (:id user))] 0)]
      (count (filter (fn [statement]
                       (and (> (or (:given statement) 0) last-seen)
                            (not= (ss/canonize-email (get-in statement [:person :email])) (ss/canonize-email (:email user)))))
                     (:statements app))))
    0))

(defn- count-unseen-verdicts [user app]
  (if (and (usr/applicant? user) (not (:infoRequest app)))
    (let [last-seen (get-in app [:_verdicts-seen-by (keyword (:id user))] 0)]
      (count (filter (fn [verdict] (> (or (:timestamp verdict) 0) last-seen)) (:verdicts app))))
    0))

(defn- state-base-filter [required-state attachment]
  (util/=as-kw required-state (att-util/attachment-state attachment)))

(defn count-attachments-requiring-action [user {:keys [infoRequest attachments _attachment_indicator_reset] :as application}]
  (if-not infoRequest
    (let [requires-user-action (partial state-base-filter :requires_user_action)
          requires-authority-action (partial state-base-filter :requires_authority_action)
          attachment-indicator-reset (or _attachment_indicator_reset 0)]
      (count
        (case (keyword (:role user))
          :applicant (filter requires-user-action attachments)
          :authority (filter #(and (requires-authority-action %)
                                (not= (get-in % [:latestVersion :user :id]) (:id usr/batchrun-user-data)) ; LPK-2207
                                (> (get-in % [:latestVersion :created] 0) attachment-indicator-reset)) attachments)
          nil)))
    0))

(defn- count-document-modifications-per-doc [user app]
  (if (and (usr/authority? user) (not (:infoRequest app)))
    (into {} (map (fn [doc] [(:id doc) (model/modifications-since-approvals doc)]) (:documents app)))
    {}))


(defn- count-document-modifications [user app]
  (if (and (usr/authority? user) (not (:infoRequest app)))
    (reduce + 0 (vals (:documentModificationsPerDoc app)))
    0))

(defn- organization-meta [_ app]
  (let [org (organization/get-organization (:organization app))
        muni (:municipality app)
        permit-type (:permitType app)
        scope (organization/resolve-organization-scope muni permit-type org)
        tags (:tags org)]
    {:name (organization/get-organization-name org)
     :links (:links org)
     :tags (zipmap (map :id tags) (map :label tags))
     :requiredFieldsFillingObligatory (:app-required-fields-filling-obligatory org)
     :kopiolaitos {:kopiolaitosEmail (:kopiolaitos-email org)
                   :kopiolaitosOrdererAddress (:kopiolaitos-orderer-address org)
                   :kopiolaitosOrdererPhone (:kopiolaitos-orderer-phone org)
                   :kopiolaitosOrdererEmail (:kopiolaitos-orderer-email org)}
     :asianhallinta (get-in scope [:caseManagement :enabled])}))

(defn- indicator-sum [_ app]
  (apply + (map (fn [[k v]] (if (#{:documentModifications :unseenStatements :unseenVerdicts} k) v 0)) app)))

(defn- archived? [_ app]
  (and (some? (get-in app [:archived :completed]))
       (> (get-in app [:archived :completed]) 1)))

(def indicator-meta-fields [{:field :documentModificationsPerDoc :fn count-document-modifications-per-doc}
                            {:field :documentModifications :fn count-document-modifications}
                            {:field :unseenComments :fn count-unseen-comments}
                            {:field :unseenStatements :fn count-unseen-statements}
                            {:field :unseenVerdicts :fn count-unseen-verdicts}
                            {:field :unseenAuthorityNotice :fn unseen-notice?}
                            {:field :attachmentsRequiringAction :fn count-attachments-requiring-action}
                            {:field :fullyArchived :fn archived?}
                            {:field :indicators :fn indicator-sum}])

(def meta-fields (conj indicator-meta-fields
                   {:field :inPostVerdictState :fn in-post-verdict-state?}
                   {:field :applicantPhone :fn get-applicant-phone}
                   {:field :applicantCompanies :fn get-applicant-companies}
                   {:field :organizationMeta :fn organization-meta}
                   {:field :stateSeq :fn #(sm/application-state-seq %2)}
                   {:field :tasksTabShouldShow :fn select-tasks-tab?}))

(defn- enrich-with-meta-fields [fields user app]
  (reduce (fn [app {field :field f :fn}] (assoc app field (f user app))) app fields))

(def with-indicators
  "Enriches application with indicators that can be calculated without extra database lookups"
  (partial enrich-with-meta-fields indicator-meta-fields))

(def with-meta-fields
  "Enriches application with all meta fields. Causes database lookups."
  (partial enrich-with-meta-fields meta-fields))

(defn enrich-with-link-permit-data [{application-id :id :as application}]
  (if-let [links (seq (when application-id (mongo/select :app-links {:link {$in [application-id]}})))]

    ;; Link permit data was found
    (let [find-link-permit-id (fn [{link :link}]
                                (util/find-first #(not= application-id %) link))
          link-permit-ids (mapv find-link-permit-id links)
          app-query (if (= (count link-permit-ids) 1)
                      {:_id (first link-permit-ids)}
                      {:_id {$in link-permit-ids}})
          link-applications (->> (mongo/select :applications
                                               app-query
                                               {:primaryOperation 1 :permitSubtype 1 :state 1 :documents 1})
                                 (reduce #(assoc %1 (:id %2) %2) {}))
          our-link-permits (filter #(= (:type ((keyword application-id) %)) "application") links)
          apps-linking-to-us (filter #(= (:type ((keyword application-id) %)) "linkpermit") links)
          convert-fn (fn [link-data]
                       (let [link-permit-id (find-link-permit-id link-data)
                             link-permit-type (:linkpermittype ((keyword link-permit-id) link-data))]

                         (if (= (:type ((keyword application-id) link-data)) "application")

                           ;; TODO: Jos viiteluvan tyyppi on myos jatkolupa, niin sitten :operation pitaa hakea
                           ;;       viela kauempaa, eli viiteluvan viiteluvalta. Eli looppia tahan?
                           ;; TODO: Jos viitelupa on kuntalupatunnus, ei saada operaatiota!

                           (let [link-permit-app-op (when (= link-permit-type "lupapistetunnus")
                                                      (get-in link-applications [link-permit-id :primaryOperation :name]))]
                             {:id link-permit-id :type link-permit-type :operation link-permit-app-op :permitSubtype ""})

                           (let [{:keys [primaryOperation permitSubtype state] :as linked-app}
                                 (when (= (:type ((keyword link-permit-id) link-data)) "application")
                                    (link-applications link-permit-id))

                                 converted-link-data  {:id link-permit-id
                                                       :type link-permit-type
                                                       :operation (:name primaryOperation)
                                                       :permitSubtype permitSubtype}]
                             (if-let [foreman-doc (foreman-doc-from-application linked-app)]
                               (assoc converted-link-data
                                 :state       state
                                 :foreman     (foreman-name-from-doc foreman-doc)
                                 :foremanRole (foreman-role-from-doc foreman-doc))
                               converted-link-data)))))]

      (assoc application
        :linkPermitData  (when (seq our-link-permits) (mapv convert-fn our-link-permits))
        :appsLinkingToUs (when (seq apps-linking-to-us) (mapv convert-fn apps-linking-to-us))))

    ;; No link permit data found
    (assoc application :linkPermitData nil, :appsLinkingToUs nil)))
