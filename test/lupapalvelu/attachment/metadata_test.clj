(ns lupapalvelu.attachment.metadata-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.attachment.metadata :refer :all]))

(facts "about attachment publicity check"
  (fact "non-public visibility makes attachment non-public"
    (public-attachment? {:metadata {:nakyvyys :asiakas-ja-viranomainen
                                    :julkisuusluokka :julkinen}}) => falsey)

  (fact "non-public julkisuusluokka makes attachment non-public"
    (public-attachment? {:metadata {:nakyvyys :julkinen
                                    :julkisuusluokka :salainen}}) => falsey)

  (fact "non-public nakyvyys without julkisuusluokka makes attachment non-public"
    (public-attachment? {:metadata {:nakyvyys :viranomainen}}) => falsey)

  (fact "non-public julkisuusluokka without nakyvyys makes attachment non-public"
    (public-attachment? {:metadata {:julkisuusluokka :salainen}}) => falsey)

  (fact "public nakyvyys and julkisuusluokka makes attachment public"
    (public-attachment? {:metadata {:julkisuusluokka :julkinen
                                    :nakyvyys :julkinen}}) => truthy)

  (fact "public nakyvyys only makes attachment public"
    (public-attachment? {:metadata {:nakyvyys :julkinen}}) => truthy)

  (fact "public julkisuusluokka only makes attachment public"
    (public-attachment? {:metadata {:julkisuusluokka :julkinen}}) => truthy)

  (fact "missing metadata makes attachment public"
    (public-attachment? {:metadata {}}) => truthy))
