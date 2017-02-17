 (ns lupapalvelu.ui.util)

 (defonce elemId (atom 0))
 (defn unique-elem-id
   ([] (unique-elem-id ""))
   ([prefix]
    (str prefix (swap! elemId inc))))
