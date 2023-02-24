(ns lupapalvelu.ui.pate.service
  (:require [goog.object :as googo]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.pate.state :as state]
            [sade.shared-util :as util]
            [sade.shared-strings :as ss]
            [clojure.set :as set]
            [clojure.string :as str]))

(defn fetch-template-list []
  (common/query "verdict-templates"
                #(reset! state/template-list (:verdict-templates %))
                :organizationId @state/org-id))

(defn- list-update-response [callback]
  (fn [response]
    (fetch-template-list)
    (callback response)
    (hub/send "pate::verdict-templates-changed")))

(defn update-changes-and-errors
  "Returns a success callback function that updates the state according
  to the command response (e.g., changes, removals and errors). container*
  is a 'top-level' atom that contains the info property (including
  modified and filled?)."
  [container* {:keys [state path]}]
  (fn [{:keys [modified changes errors removals filled]}]
    (swap! state (fn [state]
                   (let [state (as-> state $
                                 (reduce (fn [acc [k v]]
                                           (assoc-in acc (map keyword k) v))
                                         $
                                         changes)
                                 (reduce (fn [acc k]
                                           (let [[x & _
                                                  :as path] (map keyword k)
                                                 pruned     (util/dissoc-in acc path)]
                                             ;; Make sure that the
                                             ;; top-level map still
                                             ;; exists
                                             (cond-> pruned
                                               (-> pruned x nil?)
                                               (assoc x {}))))
                                         $
                                         removals))]
                     (reduce (fn [acc [k v]]
                               (assoc-in acc
                                         (cons :_errors
                                               (map keyword k))
                                         v))
                             (util/dissoc-in state
                                             (cons :_errors
                                                   (map keyword path)))
                             errors))))
    (swap! container* (fn [container]
                        (cond-> container
                          modified            (assoc-in [:info :modified] modified)
                          (not (nil? filled)) (assoc-in [:info :filled?] filled))))))

(defn fetch-categories [callback]
  (common/query "verdict-template-categories"
                #(do
                   (reset! state/categories (:categories %))
                   (callback (:categories %)))
                :organizationId @state/org-id))

(defn publish-template [template-id callback]
  (common/command {:command "publish-verdict-template"
                   :success (list-update-response callback)}
                  :template-id template-id
                  :organizationId @state/org-id))

(defn save-draft-value [template-id path value callback]
  (common/command {:command "save-verdict-template-draft-value"
                   :success callback}
                  :template-id template-id
                  :path path
                  :value value
                  :organizationId @state/org-id))

(defn fetch-template [template-id callback]
  (common/query :verdict-template
                callback
                :template-id template-id
                :organizationId @state/org-id))

(defn fetch-updated-template [template-id callback]
  (common/command {:command  :update-and-open-verdict-template
                   :success callback}
                  :template-id template-id
                  :organizationId @state/org-id))

(defn new-template [category callback]
  (common/command {:command "new-verdict-template"
                   :success (list-update-response callback)}
                  :category category
                  :lang (common/get-current-language)
                  :organizationId @state/org-id))

(defn set-template-name [template-id name callback]
  (common/command {:command "set-verdict-template-name"
                   :success (list-update-response callback)}
                  :template-id template-id
                  :name name
                  :organizationId @state/org-id))

(defn toggle-delete-template [template-id delete callback]
  (common/command {:command "toggle-delete-verdict-template"
                   :success (list-update-response callback)}
                  :template-id template-id
                  :delete delete
                  :organizationId @state/org-id))

(defn copy-template [template-id callback]
  (common/command {:command "copy-verdict-template"
                   :success (list-update-response callback)}
                  :template-id template-id
                  :organizationId @state/org-id))

(defn settings [category callback]
  (common/query "verdict-template-settings"
                callback
                :category category
                :organizationId @state/org-id))

(defn save-settings-value [category path value callback]
  (common/command {:command "save-verdict-template-settings-value"
                   :success callback}
                  :category category
                  :path path
                  :value value
                  :organizationId @state/org-id))


;; Phrases

(defn fetch-organization-phrases []
  (common/query "organization-phrases"
                #(reset! state/phrases (get % :phrases []))
                :organizationId @state/org-id))

(defn fetch-application-phrases [app-id]
  (common/query "application-phrases"
                #(reset! state/phrases (get % :phrases []))
                :id app-id))

(defn fetch-custom-application-phrases [app-id]
  (common/query "custom-application-phrase-categories"
                #(reset! state/custom-phrase-categories (get % :custom-categories []))
                :id app-id))

(defn fetch-custom-organization-phrases []
  (common/query "custom-organization-phrase-categories"
                #(reset! state/custom-phrase-categories (get % :custom-categories []))
                :organizationId @state/org-id))

(defn upsert-phrase [phrase-map callback]
  (apply common/command
         "upsert-phrase"
         (fn []
           (concat (fetch-organization-phrases)
                   (fetch-custom-organization-phrases))
           (callback))
         (flatten (into [:organizationId @state/org-id] phrase-map))))

(defn delete-phrase [phrase-id callback]
  (common/command {:command "delete-phrase"
                   :success (fn []
                              (fetch-organization-phrases)
                              (callback))}
                  :phrase-id phrase-id
                  :organizationId @state/org-id))

(defn save-phrase-category [category]
  (common/command {:command "save-phrase-category"
                   :show-saved-indicator? true
                   :success (fn []
                             (fetch-custom-organization-phrases))}
                  :category category
                  :organizationId @state/org-id))

(defn delete-phrase-category [category]
  (common/command {:command "delete-phrase-category"
                   :show-saved-indicator? true
                   :success (fn [_]
                             (fetch-custom-organization-phrases))}
                  :category category
                  :organizationId @state/org-id))

;; Application verdict templates

(defn fetch-application-verdict-templates [app-id]
  (common/query "application-verdict-templates"
                #(reset! state/template-list (:templates %))
                :id app-id))

;; Verdicts

(defn loop-fetch-verdict-list
  "Fetches verdicts until no verdicts have 'publishing-state'"
  [app-id & [old-verdicts]]
  (common/query "pate-verdicts"
                (fn [result]
                  (let [verdicts              (:verdicts result)
                        get-states #(reduce (fn [states {state :verdict-state}]
                                              (conj states state))
                                            [] %)
                        new-states (get-states verdicts)
                        old-states (get-states old-verdicts)]
                    (if (and (seq (set/intersection helper/publishing-states (set new-states)))
                             (str/includes? (googo/getValueByKeys js/window "location" "href")
                                            (str app-id "/" "verdict")))
                      (do
                        (when (not= new-states old-states)
                          (reset! state/verdict-list verdicts)
                          (state/refresh-verdict-auths app-id))
                        (common/start-delay #(loop-fetch-verdict-list app-id verdicts) 5000))
                      (do
                        (reset! state/verdict-list verdicts)
                        (state/refresh-verdict-auths app-id)))))
                :id app-id))

(defn fetch-verdict-list [app-id]
  (common/query "pate-verdicts"
                #(reset! state/verdict-list (:verdicts %))
                :id app-id))

(defn new-verdict-draft
  ([app-id template-id callback]
    (new-verdict-draft app-id template-id callback nil))
  ([app-id template-id callback replacement-id]
    (common/command {:command "new-pate-verdict-draft"
                     :success callback}
                    :id app-id
                    :template-id template-id
                    :replacement-id replacement-id)))

(defn copy-verdict-draft
  [app-id callback replacement-id]
   (common/command {:command "copy-pate-verdict-draft"
                    :success callback}
                   :id app-id
                   :replacement-id replacement-id))

(defn new-legacy-verdict-draft [app-id callback]
  (common/command {:command "new-legacy-verdict-draft"
                   :success callback}
                  :id app-id))

(defn open-verdict [app-id verdict-id callback]
  (common/query "pate-verdict"
                callback
                :id app-id
                :verdict-id verdict-id))

(defn fetch-appeals [app-id verdict-id]
  (when (state/auth? :appeals)
    (common/query :appeals
                  #(->> %
                        :data
                        ((keyword verdict-id))
                        (map (fn [{:keys [giver appellant] :as a}]
                               (assoc a :author (or giver appellant))))
                        (reset! state/appeals))
                  :id app-id)))

(declare batch-job)

(defn upsert-appeal [app-id verdict-id params wait?* callback]
  (apply (partial common/command
                  {:command               :upsert-pate-appeal
                   :show-saved-indicator? true
                   :waiting?              wait?*
                   :success               (fn [res]
                                            (reset! wait?* true)
                                            (batch-job (fn [{:keys [pending]}]
                                                         (when (empty? pending)
                                                           (fetch-appeals app-id verdict-id)
                                                           (reset! wait?* false)
                                                           (callback)))
                                                       res))}
                  :id app-id
                  :verdict-id verdict-id)
         (->> (util/filter-map-by-val identity params)
              (mapcat identity))))

(defn delete-appeal [app-id verdict-id appeal-id]
  (common/command {:command               :delete-pate-appeal
                   :show-saved-indicator? true
                   :success               #(fetch-appeals app-id verdict-id)}
                  :id app-id
                  :verdict-id verdict-id
                  :appeal-id appeal-id))

(defn open-published-verdict [app-id verdict-id callback]
  (common/query "published-pate-verdict"
                callback
                :id app-id
                :verdict-id verdict-id)
  (fetch-appeals app-id verdict-id))

(defn published-verdict-attachment-ids [app-id verdict-id callback]
  (common/query :published-verdict-attachment-ids
                callback
                :id app-id
                :verdict-id verdict-id))

(defn fetch-verdict-bulletins
  "Verdict bulletins for the application. The resetting error handler is needed, since the
  auth model might be out of date if the last verdict has been deleted. The current auth
  model check can be circumvented with `force?` argument."
  ([app-id force?]
   (letfn [(clear-bulletins [] (reset! state/verdict-bulletins nil))]
     (if (or force? (state/auth? :verdict-bulletins))
       (common/query-with-error-fn :verdict-bulletins
                                   #(reset! state/verdict-bulletins (:bulletins %))
                                   clear-bulletins
                                   :id app-id)
       (clear-bulletins))))
  ([app-id]
   (fetch-verdict-bulletins app-id false)))

(defn delete-verdict
  "Delete command to backend. Does not re-load verdicts from backend, unless an error occurs from backend.
  The caller must control the 'verdits' state."
  [app-id {:keys [id published category]}]
  (let [backing-system? (util/=as-kw category :backing-system)]
    (common/command {:command (if backing-system?
                                :delete-verdict
                                :delete-pate-verdict)
                     :success #(if published
                                 (do
                                   (js/repository.load app-id)
                                   (fetch-verdict-bulletins app-id true))
                                 (do
                                   (js/lupapisteApp.services.attachmentsService.queryAll)
                                   (state/refresh-application-auth-model app-id)
                                   (state/refresh-verdict-auths app-id)))
                     :error   (fn [resp]
                                (common/show-saved-indicator resp)
                                (fetch-verdict-list app-id))}
                   :id app-id
                   :verdict-id id)))

(defn check-for-verdict [app-id waiting?* callback fix-options]
  (let [opts {:command (if fix-options
                         :check-for-verdict-fix
                         :check-for-verdict)
              :waiting? waiting?*
              :show-saved-indicator? true
              :success (fn [response]
                         (fetch-verdict-list app-id)
                         (fetch-verdict-bulletins app-id true)
                         (js/repository.load app-id)
                         (callback response))}]
    (if fix-options
      (common/command opts :id app-id :verdict-fix-options fix-options)
      (common/command opts :id app-id))))

(defn edit-verdict [app-id verdict-id path value callback]
  (common/command {:command "edit-pate-verdict"
                   :success (fn [response]
                              (state/refresh-verdict-auths app-id
                                                           {:verdict-id verdict-id})
                              (callback response))}
                  :id app-id
                  :verdict-id verdict-id
                  :path path
                  :value value))

(defn- refresh-verdict [app-id verdict-id]
  (fetch-verdict-list app-id)
  (state/refresh-verdict-auths app-id {:verdict-id verdict-id}))

(defn publish-and-reopen-verdict
  [app-id {verdict-id :id legacy? :legacy?} {:keys [proposal? callback waiting?]}]
  (common/command {:command  (cond
                               legacy?   :publish-legacy-verdict
                               proposal? :publish-verdict-proposal
                               :else     :publish-pate-verdict)
                   :waiting? waiting?
                   :success  (fn []
                               (refresh-verdict app-id verdict-id)
                               (if proposal?
                                 (open-verdict app-id verdict-id callback)
                                 (do
                                   (open-published-verdict app-id verdict-id callback)
                                   (js/repository.lightLoad app-id))))}
                  :id app-id
                  :verdict-id verdict-id))

(defn revert-proposal
  [app-id {verdict-id :id} {:keys [callback waiting?]}]
  (common/command {:command  :revert-verdict-proposal
                   :waiting? waiting?
                   :success  (fn []
                               (refresh-verdict app-id verdict-id)
                               (open-verdict app-id verdict-id callback))}
                  :id app-id
                  :verdict-id verdict-id))

(defn sign-contract [app-id verdict-id password category error-callback]
  (let [command (if (util/=as-kw :allu-contract category)
                  :sign-allu-contract
                  :sign-pate-contract)]
    (common/command {:command command
                     :success (fn []
                                (state/refresh-verdict-auths app-id
                                                             {:verdict-id verdict-id})
                                (fetch-verdict-list app-id)
                                (js/repository.lightLoad app-id))
                     :error error-callback}
                    :id app-id
                    :verdict-id verdict-id
                    :password password)))

(defn schedule-publishing [app-id verdict-id options]
  (common/command
    (merge {:command :scheduled-verdict-publish}
           options)
    :id app-id
    :verdict-id verdict-id))

(declare refresh-attachments)

(defn generate-pdf
  [app-id verdict-id waiting?*]
  (let [refresh-auths #(state/refresh-verdict-auths app-id
                                                    {:verdict-id verdict-id})]
    (common/command {:command :generate-pate-pdf
                    :waiting? waiting?*
                    :success (fn [{:keys [attachment-id]}]
                               (when attachment-id
                                 (refresh-auths)
                                 (refresh-attachments)))
                     :error (fn [_] (refresh-auths))}
                   :id app-id
                   :verdict-id verdict-id)))

(defn loop-until-verdict-is-done
  "Fetces verdict until it has passed its publishing state"
  [app-id verdict-id reset-verdict-fn]
  (letfn [(loop-pate-verdict-query [app-id verdict-id]
            (common/query "pate-verdict"
                          (fn [result]
                            (let [page-has-not-changed  (str/includes? (googo/getValueByKeys js/window
                                                                                             "location"
                                                                                             "href")
                                                                      (str app-id "/" verdict-id))
                                  state                 (-> result :verdict :state)]
                              (when page-has-not-changed
                                (if (contains? helper/publishing-states state)
                                  (common/start-delay #(loop-pate-verdict-query app-id verdict-id)
                                                      5000)
                                  (do
                                    (refresh-verdict app-id verdict-id)
                                    (if (= "published" state)
                                      (open-published-verdict app-id verdict-id reset-verdict-fn)
                                      (reset! state/current-verdict-state state))
                                    (js/repository.lightLoad app-id))))))
                          :id app-id
                          :verdict-id verdict-id))]
    (loop-pate-verdict-query app-id verdict-id)))

;; Attachments

(defn delete-file [file-id]
  (common/command {:command :remove-uploaded-file}
                  :attachmentId file-id))

(defn bind-attachments
  "If draft? is true, then :bind-draft-attachments command is called."
  [app-id filedatas options draft?]
  (common/command (merge
                    {:command (if draft? :bind-draft-attachments :bind-attachments)}
                    options)
                  :id app-id
                  :filedatas filedatas))

;; Signature request

(defn fetch-application-parties [app-id verdict-id callback]
  (common/query "signature-request-parties"
                callback
                :id app-id
                :verdict-id verdict-id))

(defn send-signature-request [app-id verdict-id signer-id]
  (common/command {:command :send-signature-request
                   :success #(fetch-verdict-list app-id)}
                  :id app-id
                  :verdict-id verdict-id
                  :signer-id signer-id))

(defn batch-job [status-fn {:keys [job]}]
  (status-fn (when job
               (reduce (fn [acc {:keys [fileId status]}]
                         (let [k (case (keyword status)
                                   :done :done
                                   :error :error
                                   :pending)]
                           (update acc k #(cons fileId %))))
                       {:pending []
                        :error   []
                        :done    []}
                       (some-> job :value vals))))
  (if (util/=as-kw (:status job) :running)
    (common/query :bind-attachments-job
                  (partial batch-job status-fn)
                  :jobId (:id job)
                  :version (:version job))
    job))

(defn canonize-filedatas
  "Frontend filedatas to backend format."
  [filedatas]
  (map (fn [{:keys [file-id type] :as filedata}]
         (let [[type-group type-id] (util/split-kw-path type)]
           (merge (select-keys filedata [:target :contents :source])
                  {:fileId file-id
                   :type   {:type-group type-group
                            :type-id    type-id}})))
       filedatas))

;; Co-operation with the AttachmentsService

(defn attachments []
  (js->clj (js/lupapisteApp.services.attachmentsService.rawAttachments)
           :keywordize-keys true))

(defn refresh-attachments []
  (js/lupapisteApp.services.attachmentsService.queryAll))


;; Suomi.fi-messages

(defn load-suomifi-recipient [app-id]
  (common/query "get-verdict-recipient"
                #(reset! state/verdict-recipient (:recipient %))
                :id app-id))

(defn initialize-suomifi-settings [app-id]
  (when (and (state/auth? :get-organizations-suomifi-messages-enabled)
             (ss/not-blank? @state/org-id))
    (common/query "get-organizations-suomifi-messages-enabled"
                  (fn [{:keys [enabled]}]
                    (when enabled
                      (load-suomifi-recipient app-id)))
                  :organizationId @state/org-id
                  :section "verdict")))
