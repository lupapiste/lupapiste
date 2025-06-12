(ns lupapalvelu.migration.company
  "Utilities for company-related migrations."
  (:require [lupapalvelu.company :as com]
            [lupapalvelu.conversion.util :refer [with-sheriff-note]]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.strings :as ss]))


(defn auth-company-applications-by-domain
  "Auth `company-id` to every application that has a an active `domain` user AND the domain
  is connected to the company AND the company is not yet authed to the
  application. Returns the number of updated applications (can be zero) or nil if the
  updates were not even attempted (company is locked, does not exist, no users, ...).

  `company-id` Company id (the companies mongo document id).
  `domain`     Just the domain without @ sign (e.g., `example.net`).
  `note-text`  Optional text that is stored into the sheriff notes of the updated
  applications (default value is no note)."
  ([company-id domain note-text]
   (try
     (let [domain       (or (some->> (ss/blank-as-nil domain)
                                     ss/canonize-email
                                     (str "@"))
                            (throw (ex-info "Not a proper domain." {:domain domain})))
           company-auth (or (com/company->auth (com/find-company-by-id! company-id))
                            (throw (ex-info "No company auth." {})))
           email-re     (re-pattern (str domain "$"))
           _            (when-not (mongo/any? :users {:email      email-re
                                                      :enabled    true
                                                      :company.id company-id})
                          (throw (ex-info "No users for company."
                                          {:company-id company-id
                                           :domain     domain})))
           user-ids     (->> (mongo/select :users
                                           {:email email-re :enabled true}
                                          [:id :email])
                             ;; To avoid any regex mishaps.
                             (filter #(ss/ends-with? (:email %) domain))
                             (map :id))
           note         (when-not (ss/blank? note-text)
                          {:_sheriff-notes {:note    note-text
                                            :created (now)}})]
       (mongo/update-by-query :applications
                              {:auth.id {$ne company-id}
                               :auth    {$elemMatch {:id   {$in user-ids}
                                                     :role :writer}}}
                              {$push (merge {:auth company-auth} note)}))
     (catch Exception e
       (log/infof "No application updates for company %s (%s): %s"
                     company-id domain (ex-message e)))))
  ([company-id domain]
   (auth-company-applications-by-domain company-id domain nil)))

(defn merge-all-applications-from-companies
  "Moves all aplications from companies in merge-from-ids into the merge-to-id company
  The auth of companies in merge-from-ids will be removed from each application
  Will add a sheriff-note to each affected application"
  [merge-to-id note-text merge-from-ids]
  (let [auth (com/company->auth (com/find-company-by-id merge-to-id))]
    (mongo/update-by-query :applications
                           {$and [{:auth.id {$in merge-from-ids}}
                                  {:auth.id {$ne merge-to-id}}]}
                           (with-sheriff-note note-text {$push {:auth auth}}))
    (mongo/update-by-query :applications
                           {:auth.id {$in merge-from-ids}}
                           {$pull {:auth {:id {$in merge-from-ids}}}})))

(defn lock-companies
  "Locks n amount of companies given their ids"
  [& company-ids]
  (mongo/update-by-query :companies
                         {:_id {$in company-ids}}
                         {$set {:locked (now)}}))



(defn merge-all-users-from-companies
  "Moves all users from companies in merge-from-ids into merge-to-id company
  Will add a sheriff-note to each affected application"
  [merge-to-id note-text merge-from-ids]
  (mongo/update-by-query :users
                         {:company.id {$in merge-from-ids}}
                         (with-sheriff-note note-text {$set {:company.id merge-to-id}})))

(defn can-companies-be-merged
  "Checks that none of the companies being merged are locked"
  [& companies]
  (and (seq companies)
       (= (count companies)
          (mongo/count :companies {:_id    {$in companies}
                                   :locked {$not {$gt 0}}}))))

(defn merge-companies-into
  "Merges n amount of companies (all applications and users) into some company.
  Returns nil if all companies have been merged, otherwise will throw error
  All companies that are merged from will be locked!

  Takes the following arguments:
  A company id of the company to merge into
  And a note-text to be added as a sheriff-note to each application and user affected
  n amount of company ids to merge from"
  [merge-into-id sheriff-note-text & merge-from-ids]
  (cond
    (nil? (com/find-company-by-id merge-into-id))                :error.company-to-merge-into-not-found
    (mongo/any? :companies {:_id merge-into-id :locked {$gt 0}}) :error.merge-into-company-locked
    (empty? merge-from-ids)                                      :error.no-companies-to-merge-from-given
    (some #{merge-into-id} merge-from-ids)                       :error.company-to-merge-into-also-in-companies-to-merge
    (not-every? com/find-company-by-id merge-from-ids)           :error.some-company-to-merge-from-not-found
    :else (do
            (merge-all-applications-from-companies merge-into-id sheriff-note-text merge-from-ids)
            (apply lock-companies merge-from-ids)
            (merge-all-users-from-companies merge-into-id sheriff-note-text merge-from-ids))))
