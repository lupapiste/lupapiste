(ns lupapalvelu.migration.foreman-role-mapping
  (:require [clojure.string :as str]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]))

;;; Foreman role coercion

(def foreman-resp     "vastaava työnjohtaja")
(def foreman-special  "erityisalojen työnjohtaja")
(def foreman-normal   "työnjohtaja")
(def foreman-kvv      "KVV-työnjohtaja")
(def foreman-iv       "IV-työnjohtaja")

(def coercions
  "Legacy foreman requirement tasknames, as lowercase and without dashes/whitespace for simpler matching"
  {"työnjohtaja"                                                                        foreman-normal
   "työnjohtajat"                                                                       foreman-normal
   "ivtyönjohtaja"                                                                      foreman-iv
   "muutyönjohtaja"                                                                     foreman-normal
   "kvvtyönjohtaja"                                                                     foreman-kvv
   "erityistyönjohtaja"                                                                 foreman-special
   "erityistyönjohtajan"                                                                foreman-special
   "ivtöidentyönjohtaja"                                                                foreman-iv
   "vastaavatyönjohtaja"                                                                foreman-resp
   "kvvtöidentyönjohtaja"                                                               foreman-kvv
   "ansvarigarbetsledare"                                                               foreman-resp
   "ivvastaavatyönjohtaja"                                                              foreman-iv
   "ilmanvaihtotyönjohtaja"                                                             foreman-iv
   "erityisalantyönjohtaja"                                                             foreman-special
   "kvvvastaavatyönjohtaja"                                                             foreman-kvv
   "vesijaviemärityönjohtaja"                                                           foreman-kvv
   "erityisalojentyönjohtaja"                                                           foreman-special
   "muuerityisalantyönjohtaja"                                                          foreman-special
   "purkamistöidentyönjohtaja"                                                          foreman-normal
   "muuerityisalojentyönjohtaja"                                                        foreman-special
   "kvvtöidenvastaavatyönjohtaja"                                                       foreman-kvv
   "ilmanvaihtotöidentyönjohtaja"                                                       foreman-iv
   "purkutöidenvastaavatyönjohtaja"                                                     foreman-resp
   "maisematyönvastaavatyönjohtaja"                                                     foreman-special
   "purkamistyönvastaavatyönjohtaja"                                                    foreman-resp
   "rakennustyönvastaavatyönjohtaja"                                                    foreman-resp
   "ansvarigarbetsledareförbyggarbet"                                                   foreman-resp
   "ansvrigarbetsledareförbyggarbete"                                                   foreman-resp
   "kvvlaitteistonvastaavatyönjohtaja"                                                  foreman-kvv
   "rakennustyönvastaavantyönjohtajan"                                                  foreman-resp
   "ansvarigarbetsledareförbyggarbete"                                                  foreman-resp
   "rakennustöidenvastaavatyönjohtaja"                                                  foreman-resp
   "vastaavatyönjohtajajarkkohakulinen"                                                 foreman-resp
   "rakennustöidenvastaavatyönjohtajaa"                                                 foreman-resp
   "vesijaviemärilaitteidentyönjohtaja"                                                 foreman-kvv
   "rakennushankkeenvastaavatyönjohtaja"                                                foreman-resp
   "ilmanvaihtolait.vastaavatyönjohtaja"                                                foreman-iv
   "vesijaviemärityönjohtajaulkopuoliset"                                               foreman-kvv
   "vesijaviemärityönjohtajaulkoviemärit"                                               foreman-kvv
   "arbetsledareförventilationsanordningar"                                             foreman-iv
   "jätevesijärjestelmänvastaavatyönjohtaja"                                            foreman-kvv
   "maalämpöjärjestelmänvastaavatyönjohtaja"                                            foreman-resp
   "rakennuksenpurkamisestavastaavatyönjohtaja"                                         foreman-resp
   "vesijaviemärilaitteistonvastaavatyönjohtaja"                                        foreman-kvv
   "rakennustyönvastaavatyönjohtaja(purkutyöstä)"                                       foreman-resp
   "vastaavatyönjohtajatalousrakennuksenperustukset"                                    foreman-resp
   "vastaavatyönjohtaja(vaativuudeltaanvähäinenhanke)"                                  foreman-resp
   "edellytetäänhaettavaksihankkeellerakennustyönjohtaja"                               foreman-resp
   "erityistyönjohtaja/hissinasennuksenvastaavatyönjohtaja."                            foreman-special
   "rakennustöidenvastaavatyönjohtaja/siltäosinkuntyötovatkesken"                       foreman-resp
   "kiinteistönvesijaviemärilaitteistonrakentamisestavastaavatyönjohtaja"               foreman-kvv
   "rakennustöidenvastaavatyönjohtaja/siltäosinmitätässäluvassaedellytetään"            foreman-resp
   "rakennustyötäjohtavavastaavatyönjohtaja,mrl122§,mra73§,ym4/601/2015,ym5/601/2015."  foreman-resp})

(defn coerce-str
  "Gets the formal role code for the freeform role-str, if able"
  [role]
  (-> role
      str
      ss/lower-case
      (str/replace #"(\s|-)+" "")
      coercions))

;;; Foreman tasks migration

(defn coerce-to-foreman-task
  "Tasks that are easy to match with an official foreman role"
  [task role-code]
  (util/deep-merge task {:schema-info             {:subtype "foreman"} ; Some applications have tasks without subtype
                         :data                    {:kuntaRoolikoodi {:value    role-code
                                                                     :modified (:created task)}}
                         :migrated-foreman-task    true}))

(defn coerce-to-generic-task
  "Tasks that the migration couldn't figure out the proper foreman role for"
  [task]
  (let [foreman-app-id  (get-in task [:data :asiointitunnus :value])
        is-party?       (get-in task [:data :osapuolena :value])
        osapuoli-text   (format "Osapuoli: %s" (if is-party? "Kyllä" "Ei"))
        maarays-text    (format "Vaadittu työnjohtaja: %s" (:taskname task))
        hakemus-text    (if (empty? foreman-app-id)
                          ""
                          (format "Työnjohtajahakemus: %s" foreman-app-id))]
    (-> task
        (util/dissoc-in [:data :asiointitunnus])
        (util/dissoc-in [:data :osapuolena])
        (util/dissoc-in [:data :kuntaRoolikoodi])
        (util/deep-merge {:schema-info  {:name "task-lupamaarays" :version 1}
                          :state        (if (ss/not-blank? foreman-app-id) "ok" "requires_user_action")
                          :data         {:maarays {:value     maarays-text}
                                         :kuvaus  {:modified  (:created task)
                                                   :value     (->> [osapuoli-text hakemus-text]
                                                                   (remove empty?)
                                                                   (ss/join "\n"))}}}
                         {:migrated-foreman-task        true
                          :vaaditutErityissuunnitelmat  {:value ""}}))))

(defn legacy-foreman-task-update-fn
  "Gets every task as an input; returns the results of the coercion"
  [task]
  (if (not= "task-vaadittu-tyonjohtaja" (get-in task [:schema-info :name]))
    task
    (if-let [role-code (coerce-str (:taskname task))]
      (coerce-to-foreman-task task role-code)
      (coerce-to-generic-task task))))

(def legacy-foreman-tasks-query
  {:foreman-fields-migrated {$ne true}
   :tasks {$elemMatch {:data.kuntaRoolikoodi.value {$exists false}
                       :schema-info.name "task-vaadittu-tyonjohtaja"}}})

;;; PATE verdicts foreman field migration

(defn empty-pate-verdict-foreman-fields
  "Does not completely empty the map keys so the verdict draft has to be fixed by the user"
  [foremen]
  (if (map? foremen)
    (->> foremen
         (map (fn [[k _]] [k {}]))
         (into {}))
    foremen)) ; Don't touch non-legacy verdict draft fields

(defn legacy-foreman-pate-verdict-update-fn
  "Empties all draft Pate legacy verdicts' foreman fields
   (they are no longer textfields and would generate invalid tasks)
   Non-draft verdicts are not important; their tasks have already been generated."
  [pate-verdict]
  (if (= "draft" (get-in pate-verdict [:state :_value]))
    (update-in pate-verdict [:data :foremen] empty-pate-verdict-foreman-fields)
    pate-verdict))

(def legacy-foreman-pate-verdicts-query
  {:foreman-fields-migrated {$ne true}
   :pate-verdicts           {$elemMatch {:state._value  "draft"
                                         :legacy?       true
                                         :data.foremen  {$gt {}}}}})

(def legacy-foreman-applications-query
  "Gets all the affected applications for flagging"
  {$or [legacy-foreman-pate-verdicts-query legacy-foreman-tasks-query]})
