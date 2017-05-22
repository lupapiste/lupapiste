 (ns lupapalvelu.ui.util)

 (defonce elemId (atom 0))
 (defn unique-elem-id
   ([] (unique-elem-id ""))
   ([prefix]
    (str prefix (swap! elemId inc))))

(defn clj->json [ds]
  (->> ds clj->js (.stringify js/JSON)))

(defn json->clj [json]
  (->> json (.parse js/JSON) js->clj))

(def is-ie?
  (let [agent (.-userAgent js/navigator)]
    (not (nil? (re-find #".*Trident/.*" agent)))))

(def is-edge?
  (let [agent (.-userAgent js/navigator)]
    (not (nil? (re-find #".*Edge/.*" agent)))))
