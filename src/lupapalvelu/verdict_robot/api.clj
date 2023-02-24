(ns lupapalvelu.verdict-robot.api
  "REST API for robot integration:
    1.  Fetching and acknowledging Pate verdict robot JSON exports.
    2.  Fetching and acknowledging application operation locations."
  (:require [lupapalvelu.rest.rest-api :as rest :refer [defendpoint]]
            [lupapalvelu.rest.schemas :refer [OrganizationId field]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-robot.core :as robot]
            [lupapalvelu.verdict-robot.schemas :refer [Pvm OperationLocationsMessage]]
            [noir.response :as resp]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))

(def Date (sc/pred date/xml-date?
                   "ISO date format (YYYY-MM-dd). Timezone/offset part is ignored. The
                   time is always 00:00 in Finnish time."))

(defendpoint "/rest/verdict-messages"
  {:summary             "Published and removed verdicts."
   :description         "JSON for suitable verdict-related messages (published, deleted). By default, only those messages not previously acknowledged are returned. This behaviour can be overridden by `all` parameter. Time range is `from` - `until` (exclusive). `until` is optional. `organization` parameter denotes the id of the target organization (e.g., 091-R)"
   :parameters          [:organization OrganizationId
                         :from         Date]
   :optional-parameters [:until Date
                         :all   (field rest/boolean-string "If `true`, the response includes also the previously acknowledged messages (default `false`).")]}
  (if (usr/user-is-authority-in-organization? user organization)
    (resp/status 200 (resp/json (robot/messages organization
                                                (date/timestamp from)
                                                (date/timestamp until)
                                                (rest/true-string? all))))
    (resp/status 401 "Unauthorized")))

(defendpoint [:post "/rest/ack-verdict-messages"]
  {:summary        "Acknowledge that given messages have been successfully received."
   :description    "The messages have earlier been fetched via `/rest/verdict-messages`.The parameters are passed in a JSON body."
   :parameters [:organization OrganizationId
                :ids          (field [sc/Str] "Message identifiers (`sanomatunnus` field values in the `verdict-messages` response)")]}
  (if (usr/user-is-authority-in-organization? user organization)
    (some->> (map (comp ss/lower-case ss/trim) ids)
             (remove ss/blank?)
             (robot/ack-messages organization))
    (resp/status 401 "Unauthorized")))

(defendpoint "/rest/operation-locations"
  {:summary             "Application operations location information"
   :description "The response includes information on every application operation that _could have_
   location. These are operations that target either building or structure. The locations are returned
   regardless of the application states. The date parameters `from` and (optional) `until` define the location
   modification time range. *Note:* The time range only considers the manually edited locations."
   :parameters          [:organization OrganizationId
                         :from         Date]
   :optional-parameters [:until Date
                         :all   (field rest/boolean-string "By default, a location is not resent if it has not changed since the last acknowledgement. This can be overridden, if `all` is `true`.")]
   :returns             OperationLocationsMessage}
  (try+
    (->> (robot/operation-locations user organization
                                    (date/timestamp from) (date/timestamp until)
                                    (boolean all))
         (robot/save-operation-locations-integration-message user organization)
         resp/json
         (resp/status 200))
    (catch vector? v
      (apply resp/status v))))

(defendpoint [:post "/rest/ack-operation-locations-message"]
  {:summary     "Acknowledge that the given message has been successfully received and processed."
   :description "The message has been fetched earlier via `/rest/operation-locations`.  Every operation
   location sent with the original message are marked acknowledged and will not be resent if unchanged. The
   parameters are passed in a JSON body."
   :parameters  [:organization OrganizationId
                 :message-id   (field ssc/ObjectIdStr "Value of `message-id` field in the original message.")]}
  (try+
    (robot/ack-operation-locations user organization message-id)
    (resp/status 200 "OK")
    (catch vector? v
      (apply resp/status v))))
