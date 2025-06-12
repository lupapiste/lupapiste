(ns lupapalvelu.ryhti.api.schema-utils-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.ryhti.api.schema-utils :as schema-utils]))

(deftest schema-paths-per-flag-test
  ;; This is documentation rather than a test really
  (is (= {:ryhti-measurements? {"rakennuskohteenLaajentaminen"   [[:mitat]]
                                "rakennuskohteenMuuttaminen"     [[:mitat]]
                                "rakennuskohteenPurkaminen"      [[:mitat]]
                                "rakennuskohteenPurkaminen-purkamisilmoitus" [[:mitat]]
                                "rakennuskohteenTietojenKorjaus" [[:mitat]]
                                "uusiRakennuskohde"              [[:mitat]]}
          :ryhti-address?      {"rakennushanke"                  [[:sijaintitiedot]]
                                "rakennuskohteenLaajentaminen"   [[:sijaintitiedot]]
                                "rakennuskohteenMuuttaminen"     [[:sijaintitiedot]]
                                "rakennuskohteenPurkaminen"      [[:sijaintitiedot]]
                                "rakennuskohteenPurkaminen-purkamisilmoitus" [[:sijaintitiedot]]
                                "rakennuskohteenTietojenKorjaus" [[:sijaintitiedot]]
                                "uusiRakennuskohde"              [[:sijaintitiedot]]}}
         schema-utils/schema-paths-per-flag)))
