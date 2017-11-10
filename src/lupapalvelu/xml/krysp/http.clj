(ns lupapalvelu.xml.krysp.http
  (:require [sade.http :as http]
            [sade.core :refer :all]))


(defn POST [xml http-conf]
  #_(http/post (:url http-conf) {:body xml
                               :content-type "application/xml"})
  200)
