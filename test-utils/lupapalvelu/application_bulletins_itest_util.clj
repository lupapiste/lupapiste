(ns lupapalvelu.application-bulletins-itest-util
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [sade.util :as util]))

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
                       (doto (->cookie-store (atom {}))
                         (.addCookie test-db-cookie)))
        starts       (util/get-timestamp-ago :day 1)
        ends         (util/get-timestamp-from-now :day 1)
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
