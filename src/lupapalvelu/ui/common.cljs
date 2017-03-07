(ns lupapalvelu.ui.common)

(defn query [query-name success-fn & kvs]
  (-> (js/ajax.query (clj->js query-name) (-> (apply hash-map kvs) clj->js))
      (.success (fn [js-result]
                  (success-fn (js->clj js-result :keywordize-keys true))))
      .call))

(defn command [command-name success-fn & kvs]
  (-> (js/ajax.command (clj->js command-name) (-> (apply hash-map kvs) clj->js))
      (.success (fn [js-result]
                  (js/util.showSavedIndicator js-result)
                  (success-fn (js->clj js-result :keywordize-keys true))))
      .call))
