(ns lupapalvelu.document.schemas)

(defn to-map-by-name [docs] 
  (into {} (for [doc docs] [(get-in doc [:info :name]) doc])))

(def schemas
  (to-map-by-name [{:info {:name "uusi-rakennus"}
                  :body [{:name "date" :type "date" }
                         {:name "varusteet" :type "choice"
                          :body [{:name "sahko"  :type "checkbox"}
                                 {:name "kaasu"  :type "checkbox"}
                                 {:name "hissi"  :type "checkbox"}
                                 {:name "muu"    :type "string" :size "s"}]}
                         {:name "materiaali" :type "select"
                          :body [{:name "puu"}
                                 {:name "purkka"}
                                 {:name "betoni"}]}
                         {:name "story" :type "text"}]}
                 {:info {:name "hakijan-tiedot"}
                  :body [{:name "etunimi" :type "string"}
                         {:name "sukunimi" :type "string"}
                         {:name "osoite" :type "group"
                          :body [{:name "katu" :type "string"}
                                 {:name "postinumeto" :type "string"}
                                 {:name "postitoimipaikka" :type "string"}]}
                         {:name "puhelin" :type "string"}
                         {:name "email" :type "string"}]}]))
