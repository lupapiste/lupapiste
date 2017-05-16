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