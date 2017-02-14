(ns lupapalvelu.ui.common)

(defn query [name success-fn & kvs]
  (-> (js/ajax.query name (apply js-obj kvs))
      (.success (fn [js-result]
                  (success-fn (js->clj js-result :keywordize-keys true))))
      .call))

(defn command [name success-fn & kvs]
  (-> (js/ajax.command name (apply js-obj kvs))
      (.success (fn [js-result]
                  (js/util.showSavedIndicator js-result)
                  (success-fn (js->clj js-result :keywordize-keys true))))
      .call))

