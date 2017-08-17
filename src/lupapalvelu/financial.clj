(ns lupapalvelu.financial
  (:require
    [monger.operators :refer :all]
    [sade.core :refer :all]
    [lupapalvelu.user :as usr]
    [lupapalvelu.user-utils :as uu]
    [lupapalvelu.token :as token]
    [lupapalvelu.ttl :as ttl]
    [sade.env :as env]))

(defn fetch-organization-financial-handlers [org-id]
  (let [query {$and [{:role "financialAuthority"}, (usr/org-authz-match [org-id])]}
        financial-handlers (usr/find-users query)]
    (ok :data financial-handlers)))

(defn create-financial-handler [user-data caller]
  [caller user-data]
  (let [user (usr/create-new-user caller user-data :send-email false)
        token (token/make-token :password-reset caller {:email (:email user)} :ttl ttl/create-user-token-ttl)]
    (ok :id (:id user)
        :user user
        :linkFi (str (env/value :host) "/app/fi/welcome#!/setpw/" token)
        :linkSv (str (env/value :host) "/app/sv/welcome#!/setpw/" token))))

(defn delete-organization-financial-handler [email org-id]
  (usr/update-user-by-email email {:role "financialAuthority"} {$unset {(str "orgAuthz." org-id) ""}}))
