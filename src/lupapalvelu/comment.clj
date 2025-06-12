(ns lupapalvelu.comment
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.bulletin-report.page :as page]
            [lupapalvelu.comment-html :as comment-html]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- enrich-attachment-comment [attachments-by-id {{target-type :type target-id :id :as target} :target :as comment}]
  (if (and (= (keyword target-type) :attachment) target-id)
    (assoc comment :target (-> (get attachments-by-id target-id)
                               (select-keys [:id :type :contents :fetched])
                               (rename-keys {:id       :attachmentId
                                             :type     :attachmentType
                                             :contents :attachmentContents
                                             :fetched  :attachmentFetched})
                               (merge target)))
    comment))

(defn- enrich-user-information [auth {user :user :as comment}]
  (let [user-auths  (filter (comp #{(:id user)} :id) auth)
        party-roles (:party-roles (first user-auths))
        other-roles (:other-roles (first user-auths))
        auth-roles  (map (comp keyword :role) user-auths)]
    (->> (or (some (set other-roles) [:authority])
             (first party-roles)
             (some (set auth-roles) [:foreman :statementGiver])
             (when (not-empty auth-roles) :other-auth)
             (when (usr/authority? user)  :authority)
             :other-auth)
         (assoc-in comment [:user :application-role]))))

(defn enrich-comments [{comments :comments attachments :attachments :as application}]
  (let [attachments-by-id (->> attachments
                               (map (juxt :id identity))
                               (into {}))]
    (->> comments
         (map (partial enrich-attachment-comment attachments-by-id))
         (mapv (partial enrich-user-information (auth/enrich-auth-information application))))))

(defn comment-mongo-update
  ([application text target type mark-answered user to-user timestamp]
    (comment-mongo-update application text target type mark-answered user to-user timestamp [:applicant :authority]))
  ([{:keys [state] :as application} text target type mark-answered user to-user timestamp roles]
    {:pre [state (not (nil? mark-answered))]}

    (let [answered? (and mark-answered (usr/authority? user))]
      (util/deep-merge
        {$set  {:modified timestamp}}

        (when-not (and answered? (ss/blank? text))
          {$push {:comments (domain/->comment text target type user to-user timestamp roles)}})

        (case (keyword state)
          ;; LUPA-371, LUPA-745
          :info (when answered?
                  (app-state/state-transition-update :answered timestamp application user))

          ;; LUPA-371
          :answered (when (usr/applicant? user)
                      (app-state/state-transition-update :info timestamp application user))

          nil)))))

(defn get-comments-title [lang application]
  (format "%s - %s" (:id application) (i18n/localize lang :conversation.title)))

(defn get-comments-as-pdf [system lang application]
  {:pre [(map? system)]}
  (let [org          (organization/get-organization (:organization application))
        {body :body} (comment-html/comment-html lang org application)
        header       (page/render-template :header {:lang lang})
        footer       (page/render-template :footer {:lang lang})]
    (laundry-client/html-to-pdf (components/system->laundry system)
                                body
                                header
                                footer
                                (get-comments-title lang application)
                                {:top    "22mm"
                                 :bottom "28mm"
                                 :left   "11mm"
                                 :right  "11mm"})))

(defn get-comments-filename [lang application]
  (format "%s-%s.pdf" (:id application) (i18n/localize lang :conversation.title)))
