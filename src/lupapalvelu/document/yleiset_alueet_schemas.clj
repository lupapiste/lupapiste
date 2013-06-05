(ns lupapalvelu.document.yleiset-alueet-schemas
  (:use [lupapalvelu.document.schemas]))

;; TODO: Laitetaanko tämä "ammattipatevyys" liityteeksi, kuten parissa lomakkeessa on?

(def ammattipatevyys [{:name "koulutus" :type :string}
                      {:name "ammattipatevyysluokka" :type :select
                       :body [{:name "Tieturva1"}
                              {:name "Tieturva2"}]}])

(def tyomaasta-vastaava (body
                      henkilo-valitsin
                      designer-basic
                      {:name "patevyys" :type :group
                       :body ammattipatevyys}))

(def kohteen-tiedot (body
                      rakennuksen-kayttotarkoitus
                      {:name "kaupunginosa" :type :string}
                      {:name "kortteli" :type :string}
                      {:name "tontti" :type :string}
                      simple-osoite))

(def yleiset-alueet-schemas
  (to-map-by-name
    [{:info {:name "tyomaastaVastaava"
             :type :group
             :order 60}
      :body tyomaasta-vastaava}
     {:info {:name "laskutustiedot"
             :type :group
             :order 61}
                  :body party-with-required-hetu}
     {:info {:name "kohteenTiedot"
             :type :group
             :order 62}
      :body kohteen-tiedot}
     {:info {:name "tyo-/vuokra-aika"
             :type :group
             :order 63}
      :body [{:name "alkaa-pvm" :type :date}
             {:name "paattyy-pvm" :type :date}]}

     ;; TODO: LIITTEET
     ]))


;; -- Oma ehdotus --

;(def kaivuulupa {:info {:name "yleiset-alueet-kaivuu" :order 60}
;                 :body [{:name "tyomaastaVastaava"
;                         :type :group
;                         :body tyomaasta-vastaava}
;                        {:name "laskutustiedot"
;                         :type :group
;                         :body party-with-required-hetu}
;                        {:name "kohteenTiedot"
;                         :type :group
;                         :body kohteen-tiedot}
;                        {:name "tyo-/vuokra-aika"
;                         :type :group
;                         :body [{:name "alkaa-pvm" :type :date}
;                                {:name "paattyy-pvm" :type :date}]}
;
;                        ;; TODO: LIITTEET
;                        ]})
;
;(def yleiset-alueet-schemas
;  (to-map-by-name kaivuulupa))

