(ns lupapalvelu.mock.user
  (:require  [lupapalvelu.fixture.minimal :as minimal]
             [sade.util :refer [key-by mongerify]]))


(defn users-by-key [k]
  (let [mongerified-users (map mongerify minimal/users)]
    (key-by k mongerified-users)))

(def users-by-username
  (users-by-key :username))

(def users-by-id
  (users-by-key :id))

(def kaino       (users-by-username "kaino@solita.fi"))
(def erkki       (users-by-username "erkki@example.com"))
(def pena        (users-by-username "pena"))
(def mikko       (users-by-username "mikko@example.com"))
(def teppo       (users-by-username "teppo@example.com"))
(def sven        (users-by-username "sven@example.com"))
(def veikko      (users-by-username "veikko"))
(def sonja       (users-by-username "sonja"))
(def ronja       (users-by-username "ronja"))
(def luukas      (users-by-username "luukas"))
(def kosti       (users-by-username "kosti"))
(def sipoo       (users-by-username "sipoo"))
(def sipoo-ya    (users-by-username "sipoo-ya"))
(def tampere-ya  (users-by-username "tampere-ya"))
(def naantali    (users-by-username "admin@naantali.fi"))
(def oulu        (users-by-username "ymp-admin@oulu.fi"))
(def dummy       (users-by-username "dummy"))
(def admin       (users-by-username "admin"))
(def raktark-jarvenpaa (users-by-username "rakennustarkastaja@jarvenpaa.fi"))
(def arto       (users-by-username "arto"))
(def kuopio     (users-by-username "kuopio-r"))
(def velho      (users-by-username "velho"))
(def jarvenpaa  (users-by-username "admin@jarvenpaa.fi"))
(def olli       (users-by-username "olli"))
(def raktark-helsinki (users-by-username "rakennustarkastaja@hel.fi"))
(def jussi      (users-by-username "jussi"))
