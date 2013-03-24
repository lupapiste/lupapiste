(ns lupapalvelu.ua-compatible-header)

(defn add-ua-compatible-header
  "Ring middleware. Sets X-UA-Compatible header"
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (assoc-in response [:headers "X-UA-Compatible"] "IE=edge,chrome=1"))))
