(ns lupapalvelu.backing-system.krysp.common-reader-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.backing-system.krysp.common-reader :as common-reader]))

(deftest get-tunnus-path-test
  (testing "R"
    (is (= "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :R :application-id)))
    (is (= "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :R :kuntalupatunnus))))
  (testing "YA"
    (is (= "yak:luvanTunnisteTiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :YA :application-id)))
    (is (= "yak:luvanTunnisteTiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :YA :kuntalupatunnus))))
  (testing "YI"
    (is (= "ymi:luvanTunnistetiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :YI :application-id)))
    (is (= "ymi:luvanTunnistetiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :YI :kuntalupatunnus))))
  (testing "YL"
    (is (= "ymy:luvanTunnistetiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :YL :application-id)))
    (is (= "ymy:luvanTunnistetiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :YL :kuntalupatunnus))))
  (testing "YM"
    (is (= "yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :YM :application-id)))
    (is (= "yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :YM :kuntalupatunnus))))
  (testing "VVVL"
    (is (= "ymv:luvanTunnistetiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :VVVL :application-id)))
    (is (= "ymv:luvanTunnistetiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :VVVL :kuntalupatunnus))))
  (testing "P"
    (is (= "ppst:luvanTunnistetiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :P :application-id)))
    (is (= "ppst:luvanTunnistetiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :P :kuntalupatunnus))))
  (testing "MAL"
    (is (= "ymm:luvanTunnistetiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :MAL :application-id)))
    (is (= "ymm:luvanTunnistetiedot/yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :MAL :kuntalupatunnus))))
  (testing "KT"
    (is (= "kiito:toimitushakemustieto/kiito:Toimitushakemus/kiito:hakemustunnustieto/kiito:Hakemustunnus/yht:tunnus"
           (common-reader/get-tunnus-path :KT :application-id)))
    (is (= "kiito:toimitushakemustieto/kiito:Toimitushakemus/kiito:hakemustunnustieto/kiito:Hakemustunnus/yht:tunnus"
           (common-reader/get-tunnus-path :KT :kuntalupatunnus))))
  (testing "MM"
    (is (= "yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :MM :application-id)))
    (is (= "yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :MM :kuntalupatunnus))))
  (testing "ARK"
    (is (= "yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :ARK :application-id)))
    (is (= "yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :ARK :kuntalupatunnus))))
  (testing "A"
    (is (= "yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus"
           (common-reader/get-tunnus-path :A :application-id)))
    (is (= "yht:LupaTunnus/yht:kuntalupatunnus"
           (common-reader/get-tunnus-path :A :kuntalupatunnus)))))
