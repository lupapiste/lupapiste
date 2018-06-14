(ns lupapalvelu.conversion.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.conversion.util :as util]
            [sade.strings :as ss]))

(fact "Conversion of permit ids from 'database-format' works as expected"
  (facts "ids in 'database-format' are converted into 'ui-format'"
    (util/normalize-permit-id "12-0089-D 17") => "17-0089-12-D"
    (util/normalize-permit-id "10-0088-A 98") => "98-0088-10-A"
    (util/normalize-permit-id "16-0549-DJ 75") => "75-0549-16-DJ")
  (facts "ids already in 'ui-format' are not altered"
    (util/normalize-permit-id "17-0089-12-D") => "17-0089-12-D"
    (util/normalize-permit-id "98-0088-10-A") => "98-0088-10-A"
    (util/normalize-permit-id "75-0549-16-DJ") => "75-0549-16-DJ"))

(fact "Permit ids can be destructured into their constituent parts"
  (facts "Destructuring gives same results despite the input format "
    (util/destructure-permit-id "12-0089-D 17") => (util/destructure-permit-id "17-0089-12-D")
    (util/destructure-permit-id "98-0088-10-A") => (util/destructure-permit-id "10-0088-A 98"))
  (facts "The results are a map with four keys"
    (util/destructure-permit-id "75-0549-16-DJ") => {:kauposa "75" :no "0549" :tyyppi "DJ" :vuosi "16"}
    (-> (util/destructure-permit-id "16-0549-DJ 75") keys count) => 4)
  (facts "Destructuring invalid ids results in nil"
    (util/destructure-permit-id "Hei \00e4ij\00e4t :D Mit\00e4 \00e4ij\00e4t :D Siistii n\00e4h\00e4 teit :D") => nil
    (util/destructure-permit-id "75-0549-4242-A") => nil
    (util/destructure-permit-id "75 0549-4242-A") => nil
    (util/destructure-permit-id "751-0549-42-A") => nil))

