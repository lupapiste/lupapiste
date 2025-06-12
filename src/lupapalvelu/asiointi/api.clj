(ns lupapalvelu.asiointi.api
  (:require [clojure.java.io :as io]
            [lupapalvelu.asiointi.application-query.handler :as app-query]
            [lupapalvelu.asiointi.authn :as authn]
            [lupapalvelu.asiointi.authz :as authz]
            [lupapalvelu.asiointi.common :as common]
            [lupapalvelu.asiointi.control-case :as control-case]
            [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.asiointi.logging :as logging-mw]
            [lupapalvelu.asiointi.notifications.api :as notification-api]
            [lupapalvelu.asiointi.notifications.handler :as notification]
            [lupapalvelu.asiointi.permanent-identifiers.handler :as assign-id]
            [lupapalvelu.asiointi.shared-schemas :as schemas]
            [lupapalvelu.ryhti.api.malli-schema :as ryhti-schema]
            [malli.core :as malli]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.response :as resp]
            [sade.core :as core]
            [sade.env :as env]
            [sade.util :as util]))

(def description (slurp (io/resource "asiointiapi/description.md")))

(defn- permit-type-api
  [{:keys [path summary tags schema control-case]}]
  [path
   {:summary summary
    :tags    tags}
   ["/:permanent-permit-id"
    {::authz/contexts  [{:context-type :application
                         :user-key     ::user
                         :app-key      ::application
                         :required     #{:application/read}}]
     ::common/bind-app {:app-key      ::application
                        :user-key     ::user
                        :control-case control-case
                        :path         [:parameters :path :permanent-permit-id]}
     :parameters       {:path [:map [:permanent-permit-id :string]]}}
    [""
     {:summary "Read a single application"
      :get     (let [coercer (malli/coercer schema)]
                 {:summary   "Read application"
                  :responses {200 {:description "The application."
                                   :content     {"application/json" {:schema schema}}}}
                  :handler   (fn [{::keys [application]}]
                               (app-query/application-as-ryhti (util/mmap application coercer)))})}]
    (when (control-case/has-building-and-apartment-ids? control-case)
      [""
       {::authz/contexts [{:context-type :application
                           :user-key     ::user
                           :app-key      ::application
                           :required     #{:application/edit}}]}
       ["/assign-permanent-ids"
        {:summary "Assign permanent identifier(s) to building(s) and/or apartment(s)."
         :post    {:parameters {:body assign-id/AssignIdentifiersRequest}
                   :handler    (fn [{{{:keys [buildings]} :body} :parameters
                                     ::keys                      [application]}]
                                 (assign-id/assign! (util/mmap {:created (core/now)}
                                                               application
                                                               buildings)))}}]
       ;; NOTE: We also ought to have an API for fixing a incorrectly assigned permanent ID.
       ])]])

(def ^:private asiointi-api-routes
  [["/asiointi"
    {:openapi    {:id :asiointi-api}
     :middleware [openapi/openapi-feature]}
    ["/docs/*" {:get {:no-doc  true
                      :handler (swagger-ui/create-swagger-ui-handler {:url "/asiointi/openapi.json"})}}]
    ["/openapi.json" {:get {:no-doc  true
                            :openapi {:info       {:title       "asiointi-api"
                                                   :description description
                                                   :version     "0.0.1"}
                                      :tags       [{:name        :notification-registration
                                                    :description "`[Beta]` Register to receive notifications. See section _5. Herätteet_."}
                                                   {:name        :building-permit
                                                    :description "`[Beta]` Read and modify building permit applications. See section _3.2 Rakentamislupa_."}
                                                   {:name        :deviation-permit
                                                    :description "`[Beta]` Read and modify deviation permit applications. See section _3.3 Poikkeamislupa_."}]
                                      :components {:securitySchemes {:lupapisteBasicAuth {:description "Basic HTTP Authentication"
                                                                                          :type        :http
                                                                                          :scheme      :basic}}}}

                            :handler (openapi/create-openapi-handler)}}]
    ["/api" {:openapi          {:security {:lupapisteBasicAuth []}}
             :middleware       [[logging-mw/middleware logging-mw/default-commit!]
                                [authn/authentication-middleware]
                                [common/application-middleware]
                                [authz/authorization-middleware]]
             ::authn/bind-user {:user-key ::user}
             :responses        http-util/common-errors}
     ["/:organization-id"
      {::authz/contexts [{:context-type :organization
                          :path         [:parameters :path :organization-id]
                          :user-key     ::user
                          :required     #{:application/read}}]
       :parameters      {:path [:map [:organization-id string?]]}}
      (when (env/dev-mode?)
        ["/hello"
         {:summary "Mock/placeholder endpoint for an organization for tests"
          :get     {:responses {200 {:description "A mock reply"
                                     :content     {"application/json" {:schema [:map
                                                                                [:content string?]]}}}}
                    :handler   (fn [{{{:keys [organization-id]} :path} :parameters}]
                                 (resp/response {:content (str "Hello from " organization-id)}))}}])
      ["/notification-registrations"
       {:tags [:notification-registration]}
       [""
        {:get  {:summary   "Return list of registrations"
                :responses {200 {:description "List of registrations"
                                 :content     {"application/json" {:schema notification/Registrations}}}}
                :handler   (fn [{{{:keys [organization-id]} :path} :parameters}]
                             (notification/find-registrations organization-id))}
         :post {:summary    "Add a new registration"
                :parameters {:body [:map {:closed true}
                                    [:registration notification/RegistrationDraft]]}
                :responses  {200 {:description "The created registration"
                                  :content     {"application/json" {:schema notification/Registration}}}}
                :handler    (fn [{{{:keys [organization-id]} :path
                                   {:keys [registration]}    :body} :parameters
                                  ::keys                            [user]}]
                              (-> (util/mmap {:created (core/now)} user organization-id registration)
                                  notification/create-registration!))}}]
       ["/:registration-id"
        {:parameters {:path [:map [:registration-id schemas/ObjectIdString]]}
         :get        {:summary   "Return the target registration"
                      :responses {200 {:description "A registration"
                                       :content     {"application/json" {:schema notification/Registration}}}}
                      :handler   (fn [{{{:keys [organization-id registration-id]} :path} :parameters}]
                                   (notification/find-registration (util/mmap organization-id
                                                                              registration-id)))}
         :post       {:summary    "Modify the target registration"
                      :parameters {:body {:registration notification/RegistrationDraft}}
                      :responses  {200 {:description "A modified registration"
                                        :content     {"application/json" {:schema notification/Registration}}}}
                      :handler    (fn [{{{:keys [organization-id registration-id]} :path
                                         {:keys [registration]}                    :body} :parameters}]
                                    (notification/update-registration! (util/mmap organization-id
                                                                                  registration-id
                                                                                  registration)))}
         :delete     {:summary   "Remove the target registration"
                      :responses {204 {:description "Success"}}
                      :handler   (fn [{{{:keys [organization-id registration-id]} :path} :parameters}]
                                   (notification/delete-registration! (util/mmap organization-id
                                                                                 registration-id)))}}]]
      ;; To match Ryhti (and make the return schemas unambiguous) we model each permit type
      ;; as a separate API.
      ;; NOTE: At the moment the APIs are identical, so we can use a template function.
      (permit-type-api {:control-case :ryhti/building
                        :path         "/building-permit"
                        :schema       ryhti-schema/BuildingPermitIssueSchema
                        :summary      "Building permit applications"
                        :tags         [:building-permit]})
      (permit-type-api {:control-case :ryhti/deviation
                        :path         "/deviation-permit"
                        :schema       ryhti-schema/DeviationPermitIssueSchema
                        :summary      "Deviation permit applications"
                        :tags         [:deviation-permit]})]]]])

(def routes
  (->> (concat
         notification-api/notification-api-routes
         asiointi-api-routes)
       (into [])))
