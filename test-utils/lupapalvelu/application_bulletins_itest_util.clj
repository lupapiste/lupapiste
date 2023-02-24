(ns lupapalvelu.application-bulletins-itest-util
  (:require [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date])
  (:import [org.apache.http.client CookieStore]))

(defn send-file [cookie-store & [filename]]
  (without-anti-csrf
    (let [filename    (or filename "dev-resources/sipoon_alueet.zip")
          uploadfile  (io/file filename)
          uri         (str (server-address) "/api/raw/upload-file")
          resp        (http-post uri
                                 {:multipart [{:name "files[]" :content uploadfile}]
                                  :throw-exceptions false
                                  :cookie-store cookie-store})]
      resp)))

(defn create-application-and-bulletin [& args]
  (let [args         (if (map? args)
                       args
                       (apply hash-map args))
        cookie-store (if (contains? args :cookie-store)
                       (:cookie-store args)
                       (doto ^CookieStore (->cookie-store (atom {}))
                         (.addCookie test-db-cookie)))
        starts       (-> (date/now) (date/minus :day) (date/timestamp))
        ends         (-> (date/now) (date/plus :day) (date/timestamp))
        app          (if (contains? args :app)
                       (:app args)
                       (create-and-send-application sonja :operation "lannan-varastointi"
                                                    :propertyId sipoo-property-id
                                                    :x 406898.625 :y 6684125.375
                                                    :address "Hitantine 108"
                                                    :state "sent"))
        m2p-resp     (command sonja :move-to-proclaimed
                              :id (:id app)
                              :proclamationStartsAt starts
                              :proclamationEndsAt ends
                              :proclamationText "testi"
                              :cookie-store cookie-store)
        bulletin     (:bulletin (query pena :bulletin :bulletinId (:id app) :cookie-store cookie-store))]
    m2p-resp => ok?
    bulletin))

(defn randstamp
  "Random timestamp within the given day. The range is from 00:00 to
  23:59. Given datestring is in the Finnish format."
  [datestring]
  (-> (date/zoned-date-time datestring)
      (date/with-time (rand-int 24) (rand-int 60) (rand-int 60))
      (date/timestamp)))

(defn make-version [state start-ts & [end-ts]]
  (cond-> {:bulletinState (name state)}
    (= state :proclaimed) (assoc :proclamationStartsAt start-ts
                                 :proclamationEndsAt end-ts)
    (= state :verdictGiven) (assoc :appealPeriodStartsAt start-ts
                                   :appealPeriodEndsAt end-ts)
    (= state :final) (assoc :officialAt start-ts)))
