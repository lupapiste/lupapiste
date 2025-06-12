(ns lupapalvelu.asiointi.test-utils
  (:require [clojure.test :refer [is]]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.json :as json]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.util UUID]))

(set! *warn-on-reflection* true)

(defn submit!
  [& {:keys [url method body username password]}]
  (let [url     (str (itest/server-address) url)
        args    (cond-> {:throw-exceptions false
                         :basic-auth       [username password]
                         :content-type     :json}
                  body (assoc :body (json/encode body)))
        http-fn (case method
                  :get    itest/http-get
                  :post   itest/http-post
                  :delete itest/http-delete)
        resp    (http-fn url args)
        json?   (= "application/json;charset=utf-8"
                   (get-in resp [:headers "Content-Type"]))]
    (if json?
      (update resp :body (fn [body] (json/decode body true)))
      resp)))

(defn query-application
  [api-type]
  (fn [{:keys [username password organization-id permit-id]}]
    (let [{:keys [status
                  body]} (submit! {:method   :get
                                   :url      (format "/asiointi/api/%s/%s/%s"
                                                     organization-id (name api-type) permit-id)
                                   :username username
                                   :password password})]
      (is (= 200 status))
      body)))

(defn assign-permanent-ids!
  [api-type]
  (fn [{:keys [username password organization-id permit-id body]}]
    (let [{:keys [status
                  body]} (submit! {:method   :post
                                   :url      (format "/asiointi/api/%s/%s/%s/assign-permanent-ids"
                                                     organization-id (name api-type) permit-id)
                                   :username username
                                   :password password
                                   :body     body})]
      (is (= 204 status))
      body)))

(defn uuid-string?
  [s]
  (and (string? s)
       (try
         (some? (UUID/fromString s))
         (catch RuntimeException _
           false))))

(defn date-string?
  [s]
  (and (string? s)
       (date/zoned-parse s :iso-date)))

(defn application->buildings
  "Given an application in AsiointiAPI/Ryhti format, extract the buildings and apartments."
  [application]
  (->> application
       :constructionAction
       (map :building)
       (map (fn [{:keys [buildingKey permanentBuildingIdentifier buildingSection]}]
              (util/assoc-when {:buildingKey buildingKey}
                               :permanentBuildingIdentifier (ss/blank-as-nil permanentBuildingIdentifier)
                               :apartments (->> buildingSection
                                                (mapcat :apartment)
                                                (mapv (fn [{:keys [apartmentKey apartmentIdentifier]}]
                                                        (util/assoc-when {:apartmentKey apartmentKey}
                                                                         :apartmentIdentifier (ss/blank-as-nil apartmentIdentifier))))
                                                not-empty))))
       (into [])))
