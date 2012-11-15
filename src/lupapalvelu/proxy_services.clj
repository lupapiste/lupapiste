(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]))

;;
;; NLS:
;;

(def nsl-auth ["***REMOVED***" "***REMOVED***"])
(def nsl-url "https://ws.nls.fi/rasteriaineistot/image")

(defn nls [request]
  (client/get nsl-url {:query-params (:query-params request)
                       :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
                       :basic-auth nsl-auth
                       :as :stream}))

;;
;; Proxy services by name:
;;

(def services {"nls" nls})
