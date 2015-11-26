(ns lupapalvelu.organization-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.proxy-services :refer [municipality-layers municipality-layer-objects]]))

(facts
 (let [organization {:operations-attachments {:kikka ..stuff..}}
       valid-op     {:name "kikka"}
       invalid-op   {:name "kukka"}]
   (get-organization-attachments-for-operation organization valid-op) => ..stuff..
   (get-organization-attachments-for-operation organization invalid-op) => nil))

(facts "Municipality (753) maps"
       (let [url "http://mapserver"]
         (update-organization
          "753-R"
          {$set {:map-layers {:server {:url url}
                              :layers [{:name "asemakaava"
                                        :id "asemakaava-id"
                                        :base true}
                                       {:name "kantakartta"
                                        :id "kantakartta-id"
                                        :base true}
                                       {:name "foo"
                                        :id "foo-id"
                                        :base false}
                                       {:name "bar"
                                        :id "bar-id"
                                        :base false}]}}})
         (update-organization
          "753-YA"
          {$set {:map-layers {:server {:url url}
                              :layers [{:name "asemakaava"
                                        :id "other-asemakaava-id"
                                        :base true}
                                       {:name "kantakartta"
                                        :id "other-kantakartta-id"
                                        :base true}
                                       {:name "Other foo"
                                        :id "foo-id"
                                        :base false}
                                       {:name "kantakartta" ;; not base layer
                                        :id "hii-id"
                                        :base false}]}}})
         (let [layers (municipality-layers "753")
               objects (municipality-layer-objects "753")]
           (fact "Five layers"
                 (count layers) => 5)
           (fact "Only one asemakaava"
                 (->> layers (filter #(= (:name %) "asemakaava")) count) => 1)
           (fact "Only one with foo-id"
                 (->> layers (filter #(= (:id %) "foo-id")) count) => 1)
           (facts "Two layers named kantakartta"
                  (let [kantas (filter #(= "kantakartta" (:name %)) layers)
                        [k1 k2] kantas]
                    (fact "Two layers" (count kantas) => 2)
                    (fact "One of which is not a base layer"
                          (not= (:base k1) (:base k2)) => true)))
           (facts "Layer objects"
                  (fact "Every layer object has an unique id"
                        (->> objects (map :id) set count) => 5)
                  (fact "Ids are correctly formatted"
                        (every? #(let [{:keys [id base]} %]
                                   (or (and base (= (name {"asemakaava" 101
                                                           "kantakartta" 102})
                                                    id))
                                       (and (not base) (not (number? id))))) objects))
                  (fact "Bar layer is correct "
                        (let [subtitles {:fi "" :sv "" :en ""}
                              bar-index (->> layers (map-indexed #(assoc %2 :index %1)) (some #(if (= (:id %) "bar-id") (:index %))))]

                          (nth objects bar-index) => {:name {:fi "bar" :sv "bar" :en "bar"}
                                                      :subtitle subtitles
                                                      :id (str "Lupapiste-" bar-index)
                                                      :baseLayerId (str "Lupapiste-" bar-index)
                                                      :isBaseLayer false
                                                      :wmsName "Lupapiste-753-R:bar-id"
                                                      :wmsUrl "/proxy/wms"})))
           (facts "New map data with different server to 753-YA"
                  (update-organization
                   "753-YA"
                   {$set {:map-layers {:server {:url "http://different"}
                                       :layers [{:name "asemakaava"
                                                 :id "other-asemakaava-id"
                                                 :base true}
                                                {:name "kantakartta"
                                                 :id "other-kantakartta-id"
                                                 :base true}
                                                {:name "Other foo"
                                                 :id "foo-id"
                                                 :base false}]}}})
                  (let [layers (municipality-layers "753")]
                    (fact "Two layers with same ids are allowed if the servers differ"
                          (->> layers (filter #(= (:id %) "foo-id")) count) => 2)
                    (fact "Still only two base layers"
                          (->> layers (filter :base) count) => 2))))))
