(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]))

;;
;; NLS:
;;

(defn nls [request]
  (client/get "https://ws.nls.fi/rasteriaineistot/image"
    {:query-params (:query-params request)
     :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
     :basic-auth ["***REMOVED***" "***REMOVED***"]
     :as :stream}))

;;
;; Proxy services by name:
;;

(def services {"nls" nls})
