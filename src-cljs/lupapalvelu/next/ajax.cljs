(ns lupapalvelu.next.ajax)


(defn query [query-name
             {:keys [success-fn error-fn fail-fn
                     pending-fn complete-fn]}
             & kvs]
  {:pre [(some? success-fn)]}
  (-> (js/lpAjax.query (clj->js query-name) (-> (apply hash-map kvs) clj->js))
      (.success success-fn)
      (cond->
        error-fn (.error error-fn)
        fail-fn (.fail fail-fn)
        pending-fn (.pending pending-fn)
        complete-fn (.complete complete-fn))
      .call))
