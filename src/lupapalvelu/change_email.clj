(ns lupapalvelu.change-email
  (:require [lupapalvelu.document.document :as doc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.states :as states]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [lupapalvelu.vetuma :as vetuma]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :as timbre]))

(defn- notify-init-email-change [user new-email]
  (let [token-id (token/make-token :change-email user {:new-email new-email}
                                   :auto-consume false
                                   :ttl ttl/change-email-token-ttl)
        token (token/get-usable-token token-id)]
    (notifications/notify! (cond
                             (usr/financial-authority? user) :change-email-for-financial-authority
                             (usr/company-user? user) :change-email-for-company-user
                             :else :change-email)
                           {:user (assoc user :email new-email)
                            :data {:old-email (:email user)
                                   :new-email new-email
                                   :token     token}})))

(defn init-email-change [user email]
  (let [email (ss/canonize-email email)
        dummy-user (usr/get-user-by-email email)]
    (if (or (not dummy-user) (usr/dummy? dummy-user))
      (notify-init-email-change user email)
      (fail :error.duplicate-email))))

(defn- remove-dummy-auths-where-user-already-has-auth [user-id new-email]
  (mongo/update-by-query :applications
                         {:auth.id user-id}
                         {$pull {:auth {:username         new-email
                                        :invite.user.role "dummy"}}}))

(defn- change-auths-dummy-id-to-user-id [{:keys [id username email] :as user} dummy-id]
  (mongo/update-by-query :applications
                         {:auth {$elemMatch {:id dummy-id
                                             :invite.user.role "dummy"}}}
                         {$set {:auth.$.id id
                                :auth.$.username username
                                :auth.$.invite.email email
                                :auth.$.invite.user (usr/summary user)}}))

(def application-email-paths
  "Lists the locations of email fields inside the application document in MongoDB.
  The $ mark is used as a placeholder for an array index (or index-like object key)"
  [;; Most parties (persons and companies)
   :documents.$.data.henkilo.yhteystiedot.email
   :documents.$.data.yritys.yhteyshenkilo.yhteystiedot.email
   ;; Designers and foremen
   :documents.$.data.yhteystiedot.email
   ;; Building owners
   :documents.$.data.rakennuksenOmistajat.$.henkilo.yhteystiedot.email
   ;; Extended waste report
   :documents.$.data.contact.email
   ;; Manure handling exceptions
   :documents.$.data.poikkeamistapa.tapaA.$.hyodyntava-maatila.yhteystiedot.email
   :documents.$.data.poikkeamistapa.tapaB.$.varastoiva-maatila.yhteystiedot.email
   :documents.$.data.poikkeamistapa.tapaC.$.hyodyntava-maatila.yhteystiedot.email
   :documents.$.data.poikkeamistapa.tapaD.$.patterinSijaintipaikka.yhteystiedot.email
   ;; Notice forms
   :notice-forms.$.customer.email
   :notice-forms.$.history.$.user.username])

(defn- wrapped-email-path?
  "Of the current two arrays, 'documents' has 'wrapped' data, meaning at the end of the path there are
  :value and :modified fields and not just the straight up email string"
  [path]
  (-> path util/split-kw-path first (= :documents)))

(defn- get-valid-email-change-path
  "Returns the path composed of the split path-parts if the application has the old-email at the end of it"
  [path-parts application old-email]
  (let [path     (util/kw-path path-parts)
        wrapped? (wrapped-email-path? path)]
    (when (->> (cond-> (vec path-parts)
                 wrapped? (conj :value))
               (get-in application)
               (= old-email))
      path)))

(defn- get-application-email-paths
  "Returns all specific (all array indices are known) paths where the application has the old-email, e.g.
  `:documents.0.data.rakennuksenOmistajat.3.henkilo.yhteystiedot.email`"
  [old-email application]
  (->> application-email-paths
       ;; Split the path into sections by using the array index marker '$' as separator
       (map #(->> (util/split-kw-path %)
                  (partition-by #{:$})
                  (remove (comp #{:$} first))))
       ;; Put the paths back together by injecting the appropriate indices between sections
       (mapcat (fn [[head & tail]]
                 (loop [unwalked tail
                        found    [head]]
                   (if (or (empty? unwalked)
                           (empty? found))
                     ;; Walked as far up the application as we can, return validated results
                     (keep #(get-valid-email-change-path % application old-email) found)
                     ;; Keep walking and collecting path candidates by appending the index and next step in the path
                     (recur (rest unwalked)
                            (->> found
                                 (mapcat (fn [path-parts]
                                           (let [coll (get-in application path-parts)]
                                             (map #(concat path-parts [%] (first unwalked))
                                                  (cond
                                                    (nil? coll)        nil
                                                    (sequential? coll) (range (count coll))
                                                    (map? coll)        (keys coll)
                                                    :else
                                                    (timbre/errorf "Expected coll at email path % in %s"
                                                                   (util/kw-path path-parts)
                                                                   (:id application)))))))))))))
       ;; Retain application info
       (map #(assoc {:application application} :email-path %))))

(defn- is-email-update-target-valid?
  "Checks the doc/form against the appropriate pre-checks to see if it can be updated in the current state etc.
  Email path here is for example `:documents.2.data.contact.email`"
  [{:keys [email-path application]}]
  (let [[array-kw target-kw & _] (util/split-kw-path email-path)
        target                   (get-in application [array-kw (-> target-kw name util/->int)])]
    (case array-kw
      :notice-forms (->> target :history (sort-by :timestamp) last :state (not= "ok"))
      :documents    (every? #(-> {:application application
                                  :document    target}
                                 (%)
                                 (nil?))
                            doc/update-doc-pre-checks))))

(defn update-email-in-application-forms!
  "Update the changed email address in documents and notice-forms on the application where the old email is found.
  Uses the same restrictions to determine if a document is allowed to be changed as the `update-doc` command.
  Does not affect apps in terminal states such as archived, closed, etc."
  [user-id old-email new-email created]
  (doseq [[app-id email-paths] (->> (mongo/select :applications
                                                  {:auth.id user-id
                                                   :state   {$nin states/terminal-states}}
                                                  ;; :state and :history required by doc update pre-checks
                                                  [:id :documents :notice-forms :state :history])
                                    (mapcat (partial get-application-email-paths old-email))
                                    (filter is-email-update-target-valid?)
                                    (group-by #(-> % :application :id))
                                    (util/map-values #(mapv :email-path %)))]
    (mongo/update-by-id :applications
                        app-id
                        {$push {:_sheriff-notes {:created created
                                                 :note    (format "Changed email from %s to %s"
                                                                  old-email
                                                                  new-email)}}
                         $set  (->> email-paths
                                    (map (fn [email-path]
                                           (if (wrapped-email-path? email-path)
                                             {(util/kw-path email-path :value) new-email
                                              (util/kw-path email-path :modified) created}
                                             {email-path new-email})))
                                    (cons {:modified created})
                                    (reduce merge))})))

(defn update-email-in-application-auth! [user-id old-email new-email]
  ;; There might be duplicates due to old bugs, ensure everything is updated.
  ;; loop exits when no applications with the old username were found
  (loop [n 1]
    (when (pos? n)
      (recur (mongo/update-by-query :applications
                                    {:auth {$elemMatch {:id user-id
                                                        :username old-email}}}
                                    {$set {:auth.$.username new-email}})))))

(defn update-email-in-invite-auth! [user-id old-email new-email]
  (loop [n 1]
    (when (pos? n)
      (recur (mongo/update-by-query :applications
                                    {:auth {$elemMatch {:id user-id
                                                        :invite.email old-email}}}
                                    {$set {:auth.$.invite.email new-email
                                           :auth.$.invite.user.username new-email}})))))

(defn- change-email-with-token [token stamp created]
  {:pre [(map? token)]}

  (let [{hetu      :personId
         old-email :email
         id        :id :as user} (usr/get-user-by-id! (:user-id token))
        new-email                (get-in token [:data :new-email])
        com-admin?               (usr/company-admin? user)
        financial-authority?     (usr/financial-authority? user)
        {vetuma-hetu :userid}    (when (or (usr/verified-person-id? user) com-admin?)
                                   (vetuma/get-user stamp))
        not-company?             (-> user :company :role ss/blank?)]
    (cond
      (not= (:token-type token) :change-email)
      (fail! :error.token-not-found)

      (and (not hetu) not-company? (not financial-authority?))
      (fail! :error.missing-person-id)

      (and (usr/strong-authentication-required? user)
           (usr/verified-person-id? user)
           (not= hetu vetuma-hetu))
      (fail! :error.personid-mismatch)

      (usr/email-in-use? new-email)
      (fail! :error.duplicate-email))

    (when-let [{dummy-id :id :as dummy-user} (usr/get-user-by-email new-email)]
      (when (usr/dummy? dummy-user)
        (remove-dummy-auths-where-user-already-has-auth id new-email)
        (change-auths-dummy-id-to-user-id user dummy-id)
        (usr/remove-dummy-user dummy-id)))

    ;; Strictly this atomic update is enough.
    ;; Access to applications is determined by user id.
    (usr/update-user-by-email old-email
                              {:personId hetu}
                              {$set (merge {:username new-email :email new-email}
                                           (when (and (not (usr/verified-person-id? user)) com-admin?)
                                             {:personId vetuma-hetu
                                              :personIdSource :identification-service}))})

    (update-email-in-application-auth! id old-email new-email)

    (update-email-in-invite-auth! id old-email new-email)

    (update-email-in-application-forms! id old-email new-email created)

    ;; Cleanup tokens
    (when (usr/verified-person-id? user) (vetuma/consume-user stamp))
    (token/get-usable-token (:id token) :consume true)

    ;; Send notifications
    (notifications/notify! :email-changed {:user user, :data {:new-email new-email}})

    (ok)))

(defn change-email [tokenId stamp created]
  (if-let [token (token/get-usable-token tokenId)]
    (change-email-with-token token stamp created)
    (fail :error.token-not-found)))
