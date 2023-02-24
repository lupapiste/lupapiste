(ns lupapalvelu.location-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.util :as util]))

(apply-remote-minimal)

(def db-name (str "test_location" (now)))
(def runeberg "2020-02-05")
(def today (date/xml-date (date/now)))
(def yesterday (date/xml-date (.minusDays (date/now) 1)))
(def tomorrow (date/xml-date (.plusDays (date/now) 1)))
(def UNAUTHORIZED (contains {:status 401 :body "Unauthorized"}))
(def FORBIDDEN (contains {:status 403 :body "Forbidden"}))
(def NOT-FOUND (contains {:status 404 :body "Not found"}))
(def OK (contains {:status 200 :body "OK"}))

(defn rest-call
  [params & option-kvs]
  (let [res (http-get (str (server-address) "/rest/operation-locations")
                      (merge {:throw-exceptions false
                              :query-params     params
                              :basic-auth       ["sipoo-r-backend" "sipoo"]}
                             (apply hash-map option-kvs)))]
    (cond-> res
      (= 200 (:status res)) (-> decode-response :body))))

(defn ack-call
  [params & option-kvs]
  (http-post (str (server-address) "/rest/ack-operation-locations-message")
             (merge {:throw-exceptions false
                     :headers          {"content-type" "application/json;charset=utf-8"}
                     :body             (json/encode params)
                     :basic-auth       ["sipoo-r-backend" "sipoo"]}
                   (apply hash-map option-kvs))))



(let [app-id                    (:id (create-and-submit-application pena :propertyId sipoo-property-id :operation "sisatila-muutos"))
      get-application           #(-> (get-by-id :applications app-id) :body :data)
      doc-and-op-id             (fn [docs op-name]
                                  (let [doc (util/find-first (util/fn-> :schema-info :op :name (= op-name)) docs)]
                                    {:doc-id (:id doc) :op-id (-> doc :schema-info :op :id)}))
      check-location-operations (fn [description result-list]
                                  (fact {:midje/description description}
                                    (:operations (query sonja :location-operations :id app-id))
                                    => (just result-list :in-any-order)))
      _                         (fact "Pena adds one structure, one new building and one non-sructure/building operation"
                                  app-id => truthy
                                  (command pena :add-operation :id app-id :operation "aita") => ok?
                                  (command pena :add-operation :id app-id :operation "yl-puiden-kaataminen") => ok?
                                  (command pena :add-operation :id app-id :operation "pientalo") => ok?)
      {:keys [documents]}       (get-application)
      {doc-interior :doc-id
       op-interior  :op-id}     (doc-and-op-id documents "sisatila-muutos")
      {doc-fence :doc-id
       op-fence  :op-id}        (doc-and-op-id documents "aita")
      {doc-house :doc-id
       op-house  :op-id}        (doc-and-op-id documents "pientalo")
      building-id               "122334455R"
      make-building             (fn [op-id national-id description x y]
                                  {:operationId    op-id
                                   :nationalId     national-id
                                   :description    description
                                   :location       [x y]
                                   :location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 [x y])})
      backend-buildings         [(make-building op-interior
                                                "vtj-prt-interior"
                                                "Interior elsewhere"
                                                54444 6999999)
                                 (make-building op-fence
                                                "vtj-prt-fence"
                                                "  "
                                                53333 6888888)
                                 (make-building op-house
                                                "vtj-prt-house"
                                                "House elsewhere"
                                                52222 6777777)]]

  (fact "Applicant can query location operations"
    (query pena :location-operations :id app-id) => ok?)

  (check-location-operations "Initial location operations"
                             [{:id op-interior :operation "sisatila-muutos"}
                              {:id op-fence :operation "aita"}
                              {:id op-house :operation "pientalo"}])

  (fact "Select building for sisatila-muutos"
    (command pena :merge-details-from-krysp :id app-id
             :documentId doc-interior
             :buildingId building-id
             :overwrite false) => ok?
    (check-location-operations "Building for sisatila-muutos"
                               [{:id op-interior :operation "sisatila-muutos" :building-id building-id}
                                {:id op-fence :operation "aita"}
                                {:id op-house :operation "pientalo" }]))

  (fact "Add description to aita"
    (command pena :update-op-description :id app-id
             :op-id op-fence
             :desc "Barbed wire") => ok?
    (check-location-operations "Description for aita"
                               [{:id op-interior :operation "sisatila-muutos" :building-id building-id}
                                {:id op-fence :operation "aita" :description "Barbed wire"}
                                {:id op-house :operation "pientalo" }]))

  (fact "Set location for pientalo"
    (command sonja :set-operation-location :id app-id
             :operation-id op-house
             :x 55555
             :y 7111111) => ok?
    (check-location-operations "Pientalo location"
                               [{:id op-interior :operation "sisatila-muutos" :building-id building-id}
                                {:id op-fence :operation "aita" :description "Barbed wire"}
                                {:id       op-house :operation "pientalo"
                                 :location [55555.0 7111111.0]}]))

  (let [{:keys [documents buildings]} (get-application)]
    (fact "Location updated in mongo"
      (->> (util/find-by-id doc-house documents)
           :schema-info :op :location)
      => (just {:epsg3067 [55555.0 7111111.0]
                :wgs84    [17.94102 63.84267]
                :modified pos?
                :user     {:firstName "Sonja"
                           :id        (id-for-key sonja)
                           :lastName  "Sibbo"
                           :role      "authority"
                           :username  "sonja"}}))
    (fact "buildings array is still empty"
      buildings => []))

  (fact "Location is not included in the application query response"
    (->> (query-application sonja app-id) :documents
         (util/find-by-id doc-house) :schema-info :op :location)
    => nil)

  (fact "Buildings from elsewhere"
    (set-application-buildings app-id backend-buildings)
    (let [buildings         (:buildings (get-application))
          interior-building (util/find-by-key :operationId op-interior buildings)
          fence-building    (util/find-by-key :operationId op-fence buildings)
          house-building    (util/find-by-key :operationId op-house buildings)]
      (check-location-operations "Buildings"
                                 [{:id          op-interior
                                   :operation   "sisatila-muutos"
                                   :description (:description interior-building)
                                   :building-id (:nationalId interior-building)
                                   :location    (:location interior-building)}
                                  {:id          op-fence
                                   :operation   "aita"
                                   :description "Barbed wire"
                                   :building-id (:nationalId fence-building)
                                   :location    (:location fence-building)}
                                  {:id          op-house
                                   :operation   "pientalo"
                                   :description (:description house-building)
                                   :building-id (:nationalId house-building)
                                   :location    (:location house-building)}])

      (fact "Set tag and description for pientalo"
        (command pena :update-doc-identifier :id app-id
                 :doc doc-house
                 :value "A"
                 :identifier "tunnus") => ok?
        (command pena :update-op-description :id app-id
                 :op-id op-house
                 :desc "Mancave") => ok?
        (check-location-operations "Mancave"
                                   [{:id          op-interior
                                     :operation   "sisatila-muutos"
                                     :description (:description interior-building)
                                     :building-id (:nationalId interior-building)
                                     :location    (:location interior-building)}
                                    {:id          op-fence
                                     :operation   "aita"
                                     :description "Barbed wire"
                                     :building-id (:nationalId fence-building)
                                     :location    (:location fence-building)}
                                    {:id          op-house
                                     :operation   "pientalo"
                                     :tag         "A"
                                     :description "Mancave"
                                     :building-id (:nationalId house-building)
                                     :location    (:location house-building)}]))

      (let [{xy     :location
             xy-wgs :location-wgs84}      (make-building "a" "b" "c" 51111.1 6655555.5)
            _                             (command ronja :set-operation-location :id app-id
                                                   :operation-id op-fence
                                                   :x (first xy) :y (second xy))
            {:keys [documents buildings]} (get-application)]
        (facts "Setting operation location updates buildings if needed"
          (fact "Location updated in mongo"
            (->> (util/find-by-id doc-fence documents)
                 :schema-info :op :location)
            => (just {:epsg3067 xy
                      :wgs84    xy-wgs
                      :modified pos?
                      :user     {:firstName "Ronja"
                                 :id        ronja-id
                                 :lastName  "Sibbo"
                                 :role      "authority"
                                 :username  "ronja"}}))
          (fact "buildings array item's location updated too"
            (util/find-by-key :operationId op-fence buildings)
            => (contains {:operationId    op-fence
                          :location       xy
                          :location-wgs84 xy-wgs})))
        (check-location-operations "Buildings updated"
                                   [{:id          op-interior
                                     :operation   "sisatila-muutos"
                                     :description (:description interior-building)
                                     :building-id (:nationalId interior-building)
                                     :location    (:location interior-building)}
                                    {:id          op-fence
                                     :operation   "aita"
                                     :description "Barbed wire"
                                     :building-id (:nationalId fence-building)
                                     :location    xy}
                                    {:id          op-house
                                     :operation   "pientalo"
                                     :tag         "A"
                                     :description "Mancave"
                                     :building-id (:nationalId house-building)
                                     :location    (:location house-building)}])
        (fact "Invalid locations in the buildings array are ignored"
          (set-application-buildings app-id (map (fn [{op-id :operationId :as m}]
                                                   (cond-> m
                                                     (= op-id op-interior)
                                                     (assoc :location [10 20])))
                                                 buildings))
          (check-location-operations "Invalid location ignored"
                                     [{:id          op-interior
                                       :operation   "sisatila-muutos"
                                       :description (:description interior-building)
                                       :building-id (:nationalId interior-building)}
                                      {:id          op-fence
                                       :operation   "aita"
                                       :description "Barbed wire"
                                       :building-id (:nationalId fence-building)
                                       :location    xy}
                                      {:id          op-house
                                       :operation   "pientalo"
                                       :tag         "A"
                                       :description "Mancave"
                                       :building-id (:nationalId house-building)
                                       :location    (:location house-building)}]))

        (fact "Reader authority"
          (query luukas :location-operations :id app-id) => ok?
          (command luukas :set-operation-location :id app-id
                   :operation-id op-fence
                   :x (first xy) :y (second xy))
          => unauthorized?))

      (fact "operation-locations query fails if the application does not have suitable operations"
        (let [app-id-ya (:id (create-and-open-application pena :propertyId sipoo-property-id
                                                          :operation "ya-kayttolupa-metsastys"))]
          app-id-ya => truthy
          (query sonja :location-operations :id app-id-ya)
          => (err :error.no-location-operations)

          (facts "REST"
            (fact "Robot not activated for any Sipoo scopes"
              (rest-call {:organization "753-R"
                          :from         runeberg}) => FORBIDDEN)

            (fact "Enable robot in Sipoo R scope"
              (command admin :update-organization
                       :municipality "753"
                       :permitType "R"
                       :pateRobot true)
              => ok?)
            (fact "Application not in REST-friendly state"
              (rest-call {:organization "753-R"
                          :from         runeberg}) => NOT-FOUND)
            (fact "Approve application"
              ;; No backend so the buildings are not touched
              (command sipoo :set-krysp-endpoint :organizationId "753-R"
                       :url "" :username "" :password ""
                       :permitType "R" :version "") => ok?
              (command sonja :update-app-bulletin-op-description :id app-id
                       :description "Structures") => ok?
              (command sonja :approve-application :id app-id :lang "fi") => ok?)
            (facts "Successful REST call"
              (let [{:keys [message-id]
                     :as   data} (rest-call {:organization "753-R"
                                             :from         runeberg})
                    find-message (fn [message-id]
                                   (->> (get-by-id :integration-messages message-id)
                                        :body :data))
                    get-ack      #(-> (get-by-id :ack-operation-locations app-id) :body :data)]
                (fact "Data is OK. Operations without location are not included."
                  data =>
                  {:message-id message-id
                   :data       [{:application-id app-id
                                 :operations     [{:id          op-fence
                                                   :operation   "aita"
                                                   :description "Barbed wire"
                                                   :building-id (:nationalId fence-building)
                                                   :location    [51111.1 6655555.5]}
                                                  {:id          op-house
                                                   :operation   "pientalo"
                                                   :tag         "A"
                                                   :description "Mancave"
                                                   :building-id (:nationalId house-building)
                                                   :location    (:location house-building)}]}]})
                (fact "Integration message has been created"
                  (find-message message-id)
                  => (just {:id           message-id
                            :direction    "out"
                            :transferType "http"
                            :partner      "robot"
                            :format       "json"
                            :created      pos?
                            :status       "published"
                            :target       {:id   "753-R"
                                           :type "organization"}
                            :initiator    {:id       "sipoo-r-backend"
                                           :username "sipoo-r-backend"}
                            :messageType  "operation-locations"
                            :data         data}))
                (fact "Ack the message"
                  (ack-call {:organization "753-R"
                             :message-id   message-id}) => OK)
                (let [ack-ts (:acknowledged (find-message message-id))]
                  (fact "Message has been acked"
                    ack-ts => pos?)
                  (fact "Operation locations are now acked"
                    (get-ack)
                    => (just {:id                app-id
                              (keyword op-fence) {:location     [51111.1 6655555.5]
                                                  :message-id   message-id
                                                  :acknowledged ack-ts}
                              (keyword op-house) {:location     (:location house-building)
                                                  :message-id   message-id
                                                  :acknowledged ack-ts}})))
                (fact "Every location acked -> locations not found"
                  (rest-call {:organization "753-R"
                              :from         runeberg}) => NOT-FOUND)
                (fact "... unless all parameter is given"
                  (let [{new-msg-id :message-id
                         :as        new-data} (rest-call {:organization "753-R"
                                                          :from         runeberg
                                                          :until        tomorrow
                                                          :all          true})]
                    (fact "New message id"
                      new-msg-id = truthy
                      new-msg-id =not=> message-id)
                    (fact "New data very similar to old one"
                      new-data => (assoc data :message-id new-msg-id))))
                (fact "Applicaton out of time range"
                  (rest-call {:organization "753-R"
                              :from         runeberg
                              :until        yesterday
                              :all          true}) => NOT-FOUND
                  (rest-call {:organization "753-R"
                              :from         tomorrow
                              :all          true}) => NOT-FOUND)
                (fact "Ack cannot be redone"
                  (ack-call {:organization "753-R"
                             :message-id   message-id}) => NOT-FOUND)
                (fact "Update aita location"
                  (command sonja :set-operation-location :id app-id
                           :operation-id op-fence
                           :x 50000
                           :y 6622222) => ok?)
                (fact "Now aita is again listed in the locations message"
                  (let [{fence-msg-id :message-id
                         :as          fence-data} (rest-call {:organization "753-R"
                                                              :from         yesterday})]
                    fence-data => {:message-id fence-msg-id
                                   :data       [{:application-id app-id
                                                 :operations     [{:id          op-fence
                                                                   :operation   "aita"
                                                                   :description "Barbed wire"
                                                                   :building-id (:nationalId fence-building)
                                                                   :location    [50000.0 6622222.0]}]}]}
                    (fact "Organization and message mismatch"
                      (ack-call {:organization "837-R"
                                 :message-id   fence-msg-id}
                                :basic-auth ["tampe-rest" "tampere"])
                      => NOT-FOUND)
                    (fact "Ack fence"
                      (ack-call {:organization "753-R"
                                 :message-id   fence-msg-id}) => OK)
                    (fact "Operation locations are now acked"
                      (get-ack)
                      => (just {:id                app-id
                                (keyword op-fence) (just {:location     [50000.0 6622222.0]
                                                          :message-id   fence-msg-id
                                                          :acknowledged pos?})
                                (keyword op-house) (just {:location     (:location house-building)
                                                          :message-id   message-id
                                                          :acknowledged pos?})}))))))

            (fact "Return 404 Not found if no locations found"
              (rest-call {:organization "753-R"
                          :from         tomorrow}) => NOT-FOUND)
            (fact "Unauthorized"
              (rest-call {:organization "753-R"
                          :from         runeberg}
                         :basic-auth ["tampe-rest" "tampere"])
              => UNAUTHORIZED)))))))
