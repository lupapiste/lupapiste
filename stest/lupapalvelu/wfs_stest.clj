(ns lupapalvelu.wfs-stest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.itest-util :refer [server-address]]
            [sade.env :as env]))

(facts "ktjkii returns"
  (against-background (env/feature? :disable-ktj-on-create) => false)
  (let [kiinteisto-tunnus "21146300010092"
        tiedot (wfs/rekisteritiedot-xml kiinteisto-tunnus)]
    (fact "kiinteistotunnus" (:kiinteistotunnus tiedot) => kiinteisto-tunnus)
    (fact "nimi" (:nimi tiedot) => "HIEKKAMETS\u00c4")
    (fact "maapintala" (:maapintaala tiedot) => "0.5990")))

;; Test urls
;;

(def local-wfs  (str (server-address) "/dev/krysp"))
(def local-private-wfs  (str (server-address) "/dev/private-krysp"))

(fact "nil url is not alive"
  (wfs/wfs-is-alive? nil nil nil) => falsey)
(fact "blank url is not alive"
  (wfs/wfs-is-alive? "" nil nil) => falsey)
(fact "invalid krysp wfs url is not alive"
  (wfs/wfs-is-alive? "invalid_url" nil nil) => nil)
(fact "local test krysp wfs url is alive"
  (wfs/wfs-is-alive? local-wfs nil nil) => true)
(fact "local private test krysp wfs url without credentials is not alive"
  (wfs/wfs-is-alive? local-private-wfs nil nil) => falsey)
(fact "local private test krysp wfs url without password is not alive"
  (wfs/wfs-is-alive? local-private-wfs "pena" nil) => falsey)
(fact "local private test krysp wfs url with credentials is alive"
  (wfs/wfs-is-alive? local-private-wfs "pena" "pena") => true)

(fact "Get our capabilities"
  (wfs/get-our-capabilities) => (contains "WMS_Capabilities"))
