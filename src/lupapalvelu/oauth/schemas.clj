(ns lupapalvelu.oauth.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(def user-keys   [:id :firstName :lastName :email :company :language :oauth-consent])
(def client-keys [:oauth])

(defn- view [schema ks]
  (assoc (st/select-keys schema ks)
         sc/Keyword sc/Any))

(defschema RootPath
  (sc/constrained sc/Str (partial re-matches #"^/.+") "Path must start with /"))

(defschema Scope (sc/enum "read" "pay"))

(defschema Fields
  "Options map that is passed between functions in the OAuth implementation"
  {:client                            (view usr/User client-keys)
   :scope                             ssc/NonBlankStr ; "read,pay"
   :scope-vec                         [(sc/one Scope "Must have scopes") Scope]
   :response_type                     (sc/enum "code" "token")
   :success_callback                  RootPath
   :error_callback                    RootPath
   (sc/optional-key :anti-csrf)       ssc/NonBlankStr
   (sc/optional-key :cancel_callback) RootPath
   (sc/optional-key :user)            (view usr/User user-keys)
   (sc/optional-key :lang)            (apply sc/enum (map keyword i18n/supported-langs))})

(defschema FieldsError
  {:error {:status sc/Int
           :body   sc/Any}})

(def FieldsOrError (sc/conditional :error FieldsError :else Fields))

(defn- check-scopes [{:keys [client scope-vec]}]
  (when-not (set/subset? (set scope-vec) (-> client :oauth :scopes set))
    {:invalid-scopes scope-vec}))

(defn- check-anti-csrf [{:keys [anti-csrf]}]
  (when (and anti-csrf (sc/check ssc/NonBlankStr anti-csrf))
    anti-csrf))

(sc/defn ^:always-validate validate-fields :- FieldsOrError
  [fields]
  (letfn [(bad-request [err]
            {:error {:status 400
                     :body   err}})]
    (if-let [err (some #(% fields) [check-anti-csrf
                                    (partial sc/check Fields)
                                    check-scopes])]
      (bad-request err)
      fields)))
