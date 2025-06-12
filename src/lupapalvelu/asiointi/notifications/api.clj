(ns lupapalvelu.asiointi.notifications.api
  "A mock API for notifications

  This is purely for documentation purposes and complements the main AsiointiAPI
  documentation as the latter does not describe the structure of the notification
  messages for the API consumers."
  (:require [lupapalvelu.asiointi.notifications.outgoing :as outgoing]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.response :as resp]))

(def notification-api-routes
  [["/asiointi-notifications"
    {:openapi    {:id :asiointi-notifications-api}
     :middleware [openapi/openapi-feature]}
    ["/docs/*" {:get {:no-doc  true
                      :handler (swagger-ui/create-swagger-ui-handler {:url "/asiointi-notifications/openapi.json"})}}]
    ["/openapi.json" {:get {:no-doc  true
                            :openapi {:info {:title       "asiointi-notifications-api"
                                             :description "Description of the AsiointiAPI notification types."
                                             :version     "0.0.1"}}

                            :handler (openapi/create-openapi-handler)}}]
    ["/api" {:openapi {}}
     ["/notify"
      {:summary "Example API demonstrating the different notification types"
       :post    {:summary    "Submit a notification"
                 :parameters {:body outgoing/Notification}
                 :responses  {204 {:description "The notification was accepted"}}
                 :handler    (fn [_]
                               (resp/status 204))}}]]]])
