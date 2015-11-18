(ns lupapalvelu.migration.valid-pdfa-to-archivable-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.sweet :refer :all]))

(fact "change-valid-pdfa-to-archivable - without valid-pdfa flag"
  (change-valid-pdfa-to-archivable {:version {:major 0
                                              :minor 2}})

  =>                               {:version {:major 0
                                              :minor 2}})

(fact "change-valid-pdfa-to-archivable - with valid-pdfa flag"
  (change-valid-pdfa-to-archivable {:version {:major 0
                                              :minor 2}
                                    :valid-pdfa true})

  =>                               {:version {:major 0
                                              :minor 2}
                                    :archivabilityError nil
                                    :archivable true})

(fact "change-valid-pdfa-to-archivable - invalid-pdfa"
  (change-valid-pdfa-to-archivable {:version {:major 0
                                              :minor 2}
                                    :valid-pdfa false})
  
  =>                               {:version {:major 0
                                              :minor 2}
                                    :archivabilityError :invalid-pdfa
                                    :archivable false})

(fact "change-valid-pdfa-to-archivable - valid-pdfa is nil"
  (change-valid-pdfa-to-archivable {:version {:major 0
                                              :minor 2}
                                    :valid-pdfa nil})
  
  =>                               {:version {:major 0
                                              :minor 2}
                                    :archivabilityError :invalid-pdfa
                                    :archivable false})

(fact update-valid-pdfa-to-arhivable-on-attachment-versions
  (update-valid-pdfa-to-arhivable-on-attachment-versions 
   {:latestVersion {:version {:major 0
                              :minor 2}
                    :valid-pdfa true}
    :state "requires_authority_action"
    :type {:type-group "muut"
           :type-id "muu"}
    :versions [{:version {:major 0
                          :minor 1}}
               {:version {:major 0
                          :minor 2}
                :valid-pdfa true}]})

  => {:latestVersion {:version {:major 0
                                :minor 2}
                      :archivabilityError nil
                      :archivable true}
      :state "requires_authority_action"
      :type {:type-group "muut"
             :type-id "muu"}
      :versions [{:version {:major 0
                            :minor 1}}
                 {:version {:major 0
                            :minor 2}
                  :archivabilityError nil
                  :archivable true}]})

