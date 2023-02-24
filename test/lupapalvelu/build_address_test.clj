(ns lupapalvelu.build-address-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.backing-system.krysp.reader :as kr]))

(defn parsed-xml [content]
  [{:tag     :osoite
    :attrs   nil
    :content content}])

(facts "XML address parsing"
  (fact "address are parsed according to http://docs.jhs-suositukset.fi/jhs-suositukset/JHS109/JHS109.html"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :porras, :attrs nil, :content ["B"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5 B"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :porras, :attrs nil, :content ["B"]}
                                   {:tag :jakokirjain, :attrs nil, :content ["b"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5 B b"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :huoneisto, :attrs nil, :content ["22"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5 22"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :porras, :attrs nil, :content ["B"]}
                                   {:tag :huoneisto, :attrs nil, :content ["22"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu B 22"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :porras, :attrs nil, :content ["B"]}
                                   {:tag :jakokirjain, :attrs nil, :content ["b"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu B b"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :jakokirjain, :attrs nil, :content ["b"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5b"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :porras, :attrs nil, :content ["B"]}
                                   {:tag :huoneisto, :attrs nil, :content ["22"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5 B 22"
    (kr/build-address (parsed-xml [{:tag :kunta, :attrs nil, :content ["186"]}
                                   {:tag :osoitenimi, :attrs nil, :content [{:tag :teksti, :attrs {:xml:lang "fi"}, :content ["Seesamikatu"]}]}
                                   {:tag :osoitenumero, :attrs nil, :content ["5"]}
                                   {:tag :porras, :attrs nil, :content ["A"]}
                                   {:tag :huoneisto, :attrs nil, :content ["1"]}
                                   {:tag :jakokirjain, :attrs nil, :content ["b"]}
                                   {:tag :postinumero, :attrs nil, :content ["04430"]}
                                   {:tag :postitoimipaikannimi, :attrs nil, :content ["KAUPUNKI"]}])
                      "fi") => "Seesamikatu 5 A 1b")

  (let [xml         (parsed-xml [{:tag :kunta :content ["186"]}
                                 {:tag     :osoitenimi
                                  :content [{:tag     :teksti :attrs {:xml:lang "fi"}
                                             :content ["Seesamikatu"]}
                                            {:tag     :teksti :attrs {:xml:lang "sv"}
                                             :content ["Sesamgatan"]}
                                            {:tag     :teksti :attrs {:xml:lang "und"}
                                             :content ["Sesame Street"]}]}
                                 {:tag :osoitenumero :content ["5"]}])
        no-lang-xml (parsed-xml [{:tag :kunta :content ["186"]}
                                 {:tag     :osoitenimi
                                  :content [{:tag :teksti :content ["Seesamikatu"]}]}
                                 {:tag :osoitenumero :content ["5"]}])]
    (facts "Address vs. language"
      (kr/build-address xml "fi") => "Seesamikatu 5"
      (kr/build-address xml "sv") => "Sesamgatan 5"
      (kr/build-address xml "und") => "Sesame Street 5"
      (kr/build-address xml "en") => "Seesamikatu 5"
      (kr/build-address no-lang-xml "fi") => "Seesamikatu 5"
      (kr/build-address no-lang-xml "??") => "Seesamikatu 5")))
