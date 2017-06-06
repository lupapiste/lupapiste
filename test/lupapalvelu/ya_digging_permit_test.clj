(ns lupapalvelu.ya-digging-permit-test
  (:require [lupapalvelu.ya-digging-permit :refer :all]
            [midje.sweet :refer :all]))

(fact "digging-permit-can-be-created?"
  (digging-permit-can-be-created? {:permitType "not YA"}) => false
  (digging-permit-can-be-created? {:permitType    "YA"
                                   :permitSubtype "sijoituslupa"
                                   :state         :draft}) => false
  (digging-permit-can-be-created? {:permitType "YA"
                                   :state      :verdictGiven}) => false
  (digging-permit-can-be-created? {:permitType    "YA"
                                   :permitSubtype "sijoituslupa"
                                   :state         :verdictGiven}) => true)

(fact "organization-digging-operations"
  (organization-digging-operations {:selected-operations ["ya-katulupa-muu-liikennealuetyo" ; digging operation
                                                          "kerrostalo-rivitalo"             ; not digging operation
                                                          ]})
  => [["yleisten-alueiden-luvat"
       [["katulupa"
         [["liikennealueen-rajaaminen-tyokayttoon"
           [["muu-liikennealuetyo" :ya-katulupa-muu-liikennealuetyo]]]]]]]])
