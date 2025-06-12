(ns lupapalvelu.attachment.metadata-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment.metadata :as metadata]))

(deftest public-attachment-test
  (testing "non-public visibility makes attachment non-public"
    (is (not (metadata/public-attachment? {:metadata {:nakyvyys :asiakas-ja-viranomainen
                                                      :julkisuusluokka :julkinen}}))))

  (testing "non-public julkisuusluokka makes attachment non-public"
    (is (not (metadata/public-attachment? {:metadata {:nakyvyys :julkinen
                                                      :julkisuusluokka :salainen}}))))

  (testing "non-public nakyvyys without julkisuusluokka makes attachment non-public"
    (is (not (metadata/public-attachment? {:metadata {:nakyvyys :viranomainen}}))))

  (testing "non-public julkisuusluokka without nakyvyys makes attachment non-public"
    (is (not (metadata/public-attachment? {:metadata {:julkisuusluokka :salainen}}))))

  (testing "public nakyvyys and julkisuusluokka makes attachment public"
    (is (metadata/public-attachment? {:metadata {:julkisuusluokka :julkinen
                                                 :nakyvyys :julkinen}})))

  (testing "public nakyvyys only makes attachment public"
    (is (metadata/public-attachment? {:metadata {:nakyvyys :julkinen}})))

  (testing "public julkisuusluokka only makes attachment public"
    (is (metadata/public-attachment? {:metadata {:julkisuusluokka :julkinen}})))

  (testing "missing metadata makes attachment public"
    (is (metadata/public-attachment? {:metadata {}}))))
