(ns lupapalvelu.migration.flatten-huoneisto-data-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.sweet :refer :all]
            [clojure.data :refer :all]))

(def application-old {:documents [{:id "53fb1f33300446a6803591e0"
                                   :schema-info {:version 1
                                                 :name "uusiRakennus"
                                                 :approvable true
                                                 :op {:id "524128df03640915c932a642"
                                                      :name "kerrostalo-rivitalo"
                                                      :created 1380002015862}}
                                   :created 1380002015862
                                   :data { :huoneistot {:0 {:muutostapa {:value "lis\u00e4ys"}
                                                            :keittionTyyppi {:value "keittio"}
                                                            :huoneistoTunnus {:porras {:value "A"}
                                                                              :huoneistonumero {:value "1"}
                                                                              :jakokirjain {:value "a"}}
                                                            :huoneistonTyyppi {:huoneistoTyyppi {:value "asuinhuoneisto"}
                                                                               :huoneistoala {:value "56"}
                                                                               :huoneluku {:value "66"}}
                                                            :varusteet {:ammeTaiSuihkuKytkin {:value true}
                                                                        :saunaKytkin {:value true}}}}}}]})

(def application-expected {:documents [{:id "53fb1f33300446a6803591e0"
                                        :schema-info {:version 1
                                                      :name "uusiRakennus"
                                                      :approvable true
                                                      :op {:id "524128df03640915c932a642"
                                                           :name "kerrostalo-rivitalo"
                                                           :created 1380002015862}}
                                        :created 1380002015862
                                        :data { :huoneistot {:0 {:muutostapa {:value "lis\u00e4ys"}
                                                                 :keittionTyyppi {:value "keittio"}
                                                                 :porras {:value "A"}
                                                                 :huoneistonumero {:value "1"}
                                                                 :jakokirjain {:value "a"}
                                                                 :huoneistoTyyppi {:value "asuinhuoneisto"}
                                                                 :huoneistoala {:value "56"}
                                                                 :huoneluku {:value "66"}
                                                                 :ammeTaiSuihkuKytkin {:value true}
                                                                 :saunaKytkin {:value true}}}}}]})

(fact "flatten aplications huoneistot documents"
  (let [flatten-data (flatten-huoneisto-data application-old)] 
    (:documents application-expected) =not=> (:documents application-old)
    flatten-data => (:documents application-expected)))
