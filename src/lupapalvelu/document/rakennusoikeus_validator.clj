(ns lupapalvelu.document.rakennusoikeus-validator
  (:require [clojure.set :as set]
            [lupapalvelu.document.validator :refer [defvalidator]]
            [sade.strings :as ss]))

(def ^:ryhti ryhti-schemas
  #{"uusiRakennuskohde"
    "rakennuskohteenPurkaminen"
    "rakennuskohteenPurkaminen-purkamisilmoitus"
    "rakennuskohteenMuuttaminen"
    "rakennuskohteenTietojenKorjaus"})

(def common-mitat-schemas
  #{"aiemman-luvan-toimenpide"
    "purkaminen"
    "purkamisilmoitus"
    "rakennuksen-muuttaminen"
    "rakennuksen-muuttaminen-ei-huoneistoja"
    "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
    "rakennustietojen-korjaus"
    "sijoittamislupa"
    "uusi-rakennus-ei-huoneistoa"
    "uusiRakennus"})

(def laajentaminen-schemas
  #{"rakennuksen-laajentaminen"
    "rakennuksen-laajentaminen-ei-huoneistoja"
    "rakennuskohteenLaajentaminen"})

(def ^:ryhti ryhti-laajentaminen-schemas
  #{"rakennuskohteenLaajentaminen"
    "rakennuskohteenMuuttaminen"})

(def sijoittamislupa-schemas
  #{"sijoittamislupa-v2"
    "sijoittamislupa-v3"
    "puhtaan-siirtyman-sijoittamislupa"})

(def mitat-schemas (set/union common-mitat-schemas
                              laajentaminen-schemas
                              sijoittamislupa-schemas
                              ryhti-schemas
                              ryhti-laajentaminen-schemas))

(defn invalid-rakennusoikeus-fields?
  "Check if a) both are filled or b) neither is filled"
  [valinta tilavuus korkeus]
  ;; If user has chosen to report both..
  (when (= "harjakorkeus" valinta)
    ;; ..then either both are present or neither are present
    (not= (some? (ss/blank-as-nil tilavuus))
          (some? (ss/blank-as-nil korkeus)))))

(defvalidator :rakennusoikeusKuutiometreina-xor-harjankorkeusRakennusalalla
  {:doc     "rakennusoikeusKuutiometreina ja harjanKorkeusrakennusalalla pakollisia jos toinen annettu"
   :schemas mitat-schemas
   :fields  [valinta [:mitat :rakennusoikeusValinta]
             tilavuus [:mitat :rakennusoikeusKuutiometreina]
             korkeus  [:mitat :harjanKorkeusrakennusalalla]]
   :facts   {:ok   [["harjakorkeus" "12" "1"]
                    ["harjakorkeus" "" ""]
                    ["tilavuus" "12" ""]
                    ["kerrosala" "12" ""]]
             :fail [["harjakorkeus" "1" ""]
                    ["harjakorkeus" "" "7"]]}}
  (invalid-rakennusoikeus-fields? valinta tilavuus korkeus))

(defvalidator :rakennusoikeusKuutiometreina-xor-harjankorkeusRakennusalalla-laajentaminen
  {:doc     "rakennusoikeusKuutiometreina ja harjanKorkeusrakennusalalla pakollisia jos toinen annettu"
   :schemas laajentaminen-schemas
   :fields  [valinta [:laajennuksen-tiedot :mitat :rakennusoikeusValinta]
             tilavuus [:laajennuksen-tiedot :mitat :rakennusoikeusKuutiometreina]
             korkeus  [:laajennuksen-tiedot :mitat :harjanKorkeusrakennusalalla]]
   :facts   {:ok   [["harjakorkeus" "12" "1"]
                    ["harjakorkeus" "" ""]
                    ["tilavuus" "12" ""]
                    ["kerrosala" "12" ""]]
             :fail [["harjakorkeus" "1" ""]
                    ["harjakorkeus" "" "7"]]}}
  (invalid-rakennusoikeus-fields? valinta tilavuus korkeus))

(defvalidator :rakennusoikeusKuutiometreina-xor-harjankorkeusRakennusalalla-sijoittamislupa
  {:doc     "rakennusoikeusKuutiometreina ja harjanKorkeusrakennusalalla pakollisia jos toinen annettu"
   :schemas sijoittamislupa-schemas
   :fields  [valinta  [:uusiRakennus :mitat :rakennusoikeusValinta]
             tilavuus [:uusiRakennus :mitat :rakennusoikeusKuutiometreina]
             korkeus  [:uusiRakennus :mitat :harjanKorkeusrakennusalalla]]
   :facts   {:ok   [["harjakorkeus" "12" "1"]
                    ["harjakorkeus" "" ""]
                    ["tilavuus" "12" ""]
                    ["kerrosala" "12" ""]]
             :fail [["harjakorkeus" "1" ""]
                    ["harjakorkeus" "" "7"]]}}
  (invalid-rakennusoikeus-fields? valinta tilavuus korkeus))
