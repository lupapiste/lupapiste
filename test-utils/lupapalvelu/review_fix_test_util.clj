(ns lupapalvelu.review-fix-test-util
  (:require [lupapalvelu.conversion-test-util :as conv-test]
            [sade.date :as date]
            [sade.util :as util]))

(defn make-xml [m]
  (-> (util/strip-nils m)
      util/strip-empty-maps
      conv-test/->xml))

(defn make-xml-building [{:keys [vtj-prt kspr in-use scope]}]
  {:KatselmuksenRakennus
   {:valtakunnallinenNumero vtj-prt
    :kunnanSisainenPysyvaRakennusnumero kspr
    :kayttoonottoKytkin in-use
    :katselmusOsittainen scope}})

(defn make-xml-other-id [xs]
  (when (seq xs)
    {:MuuTunnus {:tunnus   (first xs)
                 :sovellus (or (second xs) "")}}))

(defn make-xml-review
  [name date type scope other & buildings]
  {:Katselmus
   {:katselmuksenRakennustieto       (some->> buildings
                                              seq
                                              (map make-xml-building))
    :muuTunnustieto                  (make-xml-other-id other)
    :pitoPvm                         (date/xml-date date)
    :katselmuksenLaji                type
    :vaadittuLupaehtonaKytkin        true
    :osittainen                      scope
    :tarkastuksenTaiKatselmuksenNimi name}})

(defn make-xml-note-review
  [name date type scope other note]
  (-> (make-xml-review name date type scope other)
      (assoc-in [:Katselmus :huomautukset] [{:huomautus {:kuvaus note}}])
      util/strip-nils
      util/strip-empty-maps))

(defn make-xml-done-review
  [name date type scope other authority]
  (-> (make-xml-review name date type scope other)
      (assoc-in [:Katselmus :pitaja] authority)
      util/strip-nils
      util/strip-empty-maps))

(defn make-kuntagml [& reviews]
  (make-xml {:RakennusvalvontaAsia {:katselmustieto reviews}}))

(defn make-mongo-building [{:keys [vtj-prt kspr in-use scope]}]
  (-> {:rakennus {:valtakunnallinenNumero             vtj-prt
                  :kunnanSisainenPysyvaRakennusnumero kspr}
       :tila     {:kayttoonottava in-use
                  :tila           scope}}
      util/strip-nils
      util/strip-empty-maps
      not-empty))

(defn make-mongo-review
  [id name date type scope other-id & buildings]
  {:id          id
   :taskname    name
   :state       "sent"
   :schema-info {:name    (rand-nth ["task-katselmus" "task-katselmus-backend"])
                 :subtype "review"}
   :data        {:katselmuksenLaji type
                 :muuTunnus        other-id
                 :katselmus        {:pitoPvm (date/finnish-date date)
                                    :tila    scope}
                 :rakennus         (some->> buildings
                                            (keep-indexed (fn [i build]
                                                            (some->> (make-mongo-building build)
                                                                     (hash-map (util/make-kw i)))))
                                            seq
                                            (into {}))}})

(defn make-mongo-note-review
  [id name date type scope other-id note]
  (-> (make-mongo-review id name date type scope other-id)
      (assoc-in [:data :katselmus :huomautukset :kuvaus] note)
      util/strip-nils))

(defn make-mongo-done-review
  [id name date type scope other-id authority]
  (-> (make-mongo-review id name date type scope other-id)
      (assoc-in [:data :katselmus :pitaja] authority)
      util/strip-nils))

(defn make-application [app-id & reviews]
  {:id app-id :tasks reviews})
