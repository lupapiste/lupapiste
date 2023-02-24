(ns lupapalvelu.automatic-assignment-test
  (:require [lupapalvelu.automatic-assignment.core :as automatic]
            [lupapalvelu.automatic-assignment.schemas :as schemas]
            [lupapalvelu.fixture.minimal :as minimal]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(def sipoo-org (util/find-by-id "753-R" minimal/organizations))
(def sipoo-center {:location         {:x 404622.5149375
                                      :y 6693884.332}
                   :location-wgs84   [25.27055 60.37013]
                   :primaryOperation {:name "pientalo"}})
(def sipoo-other {:location         {:x 404115.68336401
                                     :y 6693114.6920006}
                  :location-wgs84   [25.26174 60.3631]
                  :primaryOperation {:name "purkaminen"}})

(defn pseudo-id [letter n] (str (ss/join "" (map (constantly letter) (range 23))) n))

(let [att-cv                        {:type {:type-group "osapuolet" :type-id "cv"}}
      att-verdict                   {:type {:type-group "paatoksenteko" :type-id "paatos"}}
      construction-form             {:type "construction"}
      terrain-form                  {:type "terrain"}
      [general-role-id kvv-role-id] (map :id (:handler-roles sipoo-org))]

  (fact "Suite setup sanity checks"
    general-role-id => truthy
    kvv-role-id => truthy)

  (facts "operations"
    (fact "Pientalo matches"
      (automatic/match-criteria :operations ["foo" "pientalo" "bar"] {:application  sipoo-center
                                                                      :organization sipoo-org})
      => true)
    (fact "Purkaminen does not"
      (automatic/match-criteria :operations ["foo" "pientalo" "bar"] {:application  sipoo-other
                                                                      :organization sipoo-org})
      => false)
    (facts "No operations criteria: ignored"
      (automatic/match-criteria :operations [] {:application  sipoo-other
                                                :organization sipoo-org})
      => nil
      (automatic/match-criteria :operations nil {:application  sipoo-other
                                                 :organization sipoo-org})
      => nil))

  (facts "attachment-types"
    (fact "CV matches"
      (automatic/match-criteria :attachment-types ["foo.bar" "hii.hoo" "osapuolet.cv"]
                                {:application     sipoo-center
                                 :organization    sipoo-org
                                 :attachment-type (:type att-cv)})
      => true)
    (fact "Verdict does not"
      (automatic/match-criteria :attachment-types ["foo.bar" "hii.hoo" "osapuolet.cv"]
                                {:application     sipoo-other
                                 :organization    sipoo-org
                                 :attachment-type (:type att-verdict)})
      => false)
    (facts "No attachment-types criteria: no match"
      (automatic/match-criteria :attachment-types [] {:application     sipoo-other
                                                      :organization    sipoo-org
                                                      :attachment-type (:type att-cv)})
      => false
      (automatic/match-criteria :attachment-types nil {:application     sipoo-other
                                                       :organization    sipoo-org
                                                       :attachment-type (:type att-cv)})
      => false)
    (fact "No attachment in options: ignored"
      (automatic/match-criteria :attachment-types ["foo.bar" "hii.hoo" "osapuolet.cv"]
                                {:application  sipoo-center
                                 :organization sipoo-org})
      => nil))

  (facts "notice-forms"
    (fact "Construction matches"
      (automatic/match-criteria :notice-forms ["construction" "location"]
                                {:application      sipoo-center
                                 :organization     sipoo-org
                                 :notice-form-type (:type construction-form)})
      => true)
    (fact "Terrain does not"
      (automatic/match-criteria :notice-forms ["construction" "location"]
                                {:application      sipoo-other
                                 :organization     sipoo-org
                                 :notice-form-type (:type terrain-form)})
      => false)
    (facts "No notice-forms criteria: no match"
      (automatic/match-criteria :notice-forms [] {:application      sipoo-other
                                                  :organization     sipoo-org
                                                  :notice-form-type (:type terrain-form)})
      => false
      (automatic/match-criteria :notice-forms nil {:application      sipoo-other
                                                   :organization     sipoo-org
                                                   :notice-form-type (:type terrain-form)})
      => false)
    (fact "No notice-form in options: ignored"
      (automatic/match-criteria :notice-forms ["foo.bar" "hii.hoo" "osapuolet.cv"]
                                {:application  sipoo-center
                                 :organization sipoo-org})
      => nil))

  (facts "foreman-roles"
    (fact "Enabled and listed: match"
      (automatic/match-criteria :foreman-roles ["vastaava työnjohtaja"
                                                "kvv-työnjohtaja"]
                                {:application  sipoo-center
                                 :organization sipoo-org
                                 :foreman-role "vastaava työnjohtaja"})
      => true)
    (fact "Case-insensitive"
      (automatic/match-criteria :foreman-roles ["vastaava työnjohtaja"
                                                "kvv-työnjohtaja"]
                                {:application  sipoo-center
                                 :organization sipoo-org
                                 :foreman-role (ss/join (map #(cond-> %
                                                                (pos? (rand-int 2)) ss/upper-case)
                                                             "vastaava työnjohtaja"))})
      => true)
    (fact "Not on the list: no match"
      (automatic/match-criteria :foreman-roles ["vastaava työnjohtaja"
                                                "kvv-työnjohtaja"]
                                {:application  sipoo-other
                                 :organization sipoo-org
                                 :foreman-role "iv-työnjohtaja"})
      => false
      (automatic/match-criteria :foreman-roles []
                                {:application  sipoo-other
                                 :organization sipoo-org
                                 :foreman-role "iv-työnjohtaja"})
      => false)
    (facts "No foreman-roles criteria: no match"
      (automatic/match-criteria :foreman-roles [] {:application  sipoo-other
                                                   :organization sipoo-org
                                                   :foreman-role "työnjohtaja"})
      => false
      (automatic/match-criteria :foreman-roles nil {:application  sipoo-other
                                                    :organization sipoo-org
                                                    :foreman-role "työnjohtaja"})
      => false)
    (fact "No foreman-role in options: ignored"
      (automatic/match-criteria :foreman-roles []
                                {:application  sipoo-center
                                 :organization sipoo-org})
      => nil))

  (facts "handler-role-id"
    (fact "Match"
      (automatic/match-criteria :handler-role-id kvv-role-id
                                {:application {:handlers [{:roledId "one"}
                                                          {:roleId kvv-role-id}
                                                          {:roledId "two"}]}})
      => true)
    (fact "No match: no handlers"
      (automatic/match-criteria :handler-role-id kvv-role-id
                                {:application {:handlers []}})
      => false
      (automatic/match-criteria :handler-role-id kvv-role-id
                                {:application {}})
      => false)
    (fact "No match: different handlers"
      (automatic/match-criteria :handler-role-id kvv-role-id
                                {:application {:handlers {:roleId general-role-id}}})
      => false)
    (fact "Ignored: no handler-role-id"
      (automatic/match-criteria :handler-role-id nil
                                {:application {:handlers {:roleId general-role-id}}})
      => nil
      (automatic/match-criteria :handler-role-id nil
                                {:application {:handlers []}})
      => nil
      (automatic/match-criteria :handler-role-id nil
                                {:application {}})
      => nil))

  (facts "wildcard-matches?"
    (automatic/wildcard-matches? nil nil) => false
    (automatic/wildcard-matches? "" "") => false
    (automatic/wildcard-matches? "good" "good") => true
    (automatic/wildcard-matches? "  gOoD  " " Good ") => true
    (automatic/wildcard-matches? "*" "good") => true
    (automatic/wildcard-matches? "*" "") => false
    (automatic/wildcard-matches? "*" nil) => false
    (automatic/wildcard-matches? "*" "matches an*thing") => true
    (automatic/wildcard-matches? "*end" " this is the end") => true
    (automatic/wildcard-matches? "*eNd" " this is the enD  ") => true
    (automatic/wildcard-matches? "*end" "end  ") => true
    (automatic/wildcard-matches? "*end" "  end  ") => true
    (automatic/wildcard-matches? "*end" " this end is not") => false
    (automatic/wildcard-matches? "start*" "start here ") => true
    (automatic/wildcard-matches? "start*" "start ") => true
    (automatic/wildcard-matches? "stArt*" "  herE Start ") => false
    (automatic/wildcard-matches? "start*" " start here ") => true
    (automatic/wildcard-matches? "one * two * three" "one two three") => false
    (automatic/wildcard-matches? "one * two * three" "one  two  three") => true
    (automatic/wildcard-matches? "one * two * three" " one three two one  three ") => true
    (automatic/wildcard-matches? "he??o" "hello") => false
    (automatic/wildcard-matches? "he*o" "hello") => true
    (automatic/wildcard-matches? "he*o" "heo") => true
    (automatic/wildcard-matches? "he*o" "herneenpalko") => true)

  (facts "reviews"
    (fact "Match"
      (automatic/match-criteria :reviews ["*loppu*"]
                                {:review-name "Osittainen loppukatselmus"})
      => true
      (automatic/match-criteria :reviews ["foo*" " *loppu* " "bar" ""]
                                {:review-name "Osittainen loppukatselmus"})
      => true)
    (fact "No match"
      (automatic/match-criteria :reviews ["foo*" "loppu*" "bar"]
                                {:review-name "Osittainen loppukatselmus"})
      => false)
    (fact "Ignored: no review-name"
      (automatic/match-criteria :reviews ["foo*" "loppu*" "bar"]
                                {})
      => nil))

  (let [fltr1     {:id       (pseudo-id "f" 1)
                   :name     "F1"
                   :rank     0
                   :modified 123
                   :criteria {:notice-forms     ["terrain" "construction"]
                              :attachment-types ["foo.bar" "to.do"]}
                   :target   {:handler-role-id kvv-role-id}}
        fltr2     {:id       (pseudo-id "f" 2)
                   :name     "F2"
                   :rank     0
                   :modified 123
                   :criteria {:notice-forms     ["terrain" "construction"]
                              :attachment-types ["foo.bar"]
                              :operations       ["pientalo"]}
                   :target   {:user-id "agile-authority"}}
        fltr3     {:id       (pseudo-id "f" 3)
                   :name     "F3"
                   :rank     0
                   :modified 123
                   :criteria {:attachment-types ["foo.bar"]}
                   :target   {:handler-role-id (pseudo-id "b" 3)}}
        fltr-tj   {:id       (pseudo-id "e" 4)
                   :name     "TJ"
                   :rank     0
                   :modified 123
                   :criteria {:foreman-roles ["työnjohtaja"]}}
        fltr-both {:id       (pseudo-id "f" 5)
                   :name     "F5"
                   :rank     0
                   :modified 123
                   :criteria {:notice-forms     ["terrain" "construction"]
                              :attachment-types ["foo.bar" "to.do"]}
                   :target   {:user-id         "agile-authority"
                              :handler-role-id kvv-role-id}}]

    (facts "top-matching-filters"
      (fact "No filters, no matches"
        (automatic/top-matching-filters {:organization     sipoo-org
                                         :application      sipoo-center
                                         :notice-form-type "construction"})
        => nil)
      (fact "Not matching filters, no matches"
        (automatic/top-matching-filters {:organization     (assoc sipoo-org
                                                                  automatic/FILTERS [fltr1 fltr2])
                                         :application      sipoo-center
                                         :notice-form-type "location"})
        => nil)
      (fact "One matching filter"
        (automatic/top-matching-filters {:organization    (assoc sipoo-org
                                                                 automatic/FILTERS [fltr1 fltr2])
                                         :application     sipoo-other
                                         :attachment-type {:type-group "foo" :type-id "bar"}})
        => [fltr1])
      (fact "Two matching filters, but the one with higher rank is selected."
        (automatic/top-matching-filters {:organization    (assoc sipoo-org
                                                                 automatic/FILTERS [fltr1 fltr2])
                                         :application     sipoo-center
                                         :attachment-type {:type-group "foo" :type-id "bar"}})
        => (just [fltr1 fltr2] :in-any-order))
      (fact "Two matching filters, but the one with higher rank is selected."
        (automatic/top-matching-filters {:organization    (assoc sipoo-org
                                                                 automatic/FILTERS [fltr1 (assoc fltr2 :rank 10)])
                                         :application     sipoo-center
                                         :attachment-type {:type-group "foo" :type-id "bar"}})
        => [(assoc fltr2 :rank 10)])
      (fact "Two matching filters with the same rank"
        (automatic/top-matching-filters {:organization    (assoc sipoo-org
                                                                 automatic/FILTERS [fltr1 fltr2 fltr3])
                                         :application     sipoo-other
                                         :attachment-type {:type-group "foo" :type-id "bar"}})
        => (just [fltr1 fltr3] :in-any-order)))
    (facts "recipient--application-handler-role"
      (fact "Filter has a different target"
        (automatic/recipient--application-handler-role fltr2 {:organization {}
                                                              :application  {:handlers [{:firstName "Other"
                                                                                         :lastName  "Otter"
                                                                                         :roleId    "other-otter"}]}})
        => nil)
      (fact "No matching handlers in application"
        (automatic/recipient--application-handler-role fltr1 {:organization {}
                                                              :application  {}}) => nil
        (automatic/recipient--application-handler-role fltr1 {:organization {}
                                                              :application  {:handlers []}}) => nil
        (automatic/recipient--application-handler-role fltr1 {:organization {}
                                                              :application  {:handlers [{:firstName "Other"
                                                                                         :lastName  "Otter"
                                                                                         :roleId    "other-otter"}]}})
        => nil)
      (fact "Matching handler in application"
        (automatic/recipient--application-handler-role fltr1 {:organization {}
                                                              :application  {:handlers [{:firstName "Happy"
                                                                                         :lastName  "Handler"
                                                                                         :roleId    kvv-role-id}]}})
        => {:handler-role-id kvv-role-id
            :handler         {:firstName "Happy" :lastName "Handler" :roleId kvv-role-id}}))
    (facts "recipient--user-id"
      (fact "Filter has a different target"
        (automatic/recipient--user-id fltr1 {:organization {}
                                             :application  {}}) => nil)
      (fact "No matching _authority_ user in the organization"
        (automatic/recipient--user-id fltr2 {:organization {:id "org-id"}
                                             :application  {}})
        => {:error :bad-user}
        (provided (lupapalvelu.mongo/select-one :users anything) => nil))
      (fact "Matching authority in the organization"
        (automatic/recipient--user-id fltr2 {:organization {:id "org-id"}
                                             :application  {}})
        =>  {:user {:id        "agile-authority"
                    :firstName "Agile"
                    :lastName  "Authority"}}
        (provided (lupapalvelu.mongo/select-one :users anything) => {:id        "agile-authority"
                                                                     :firstName "Agile"
                                                                     :lastName  "Authority"
                                                                     :private   {:password "top-secret"}})))
    (facts "recipient--organization-handler-role"
      (fact "Filter has a different target"
        (automatic/recipient--organization-handler-role fltr2 {:organization sipoo-org
                                                               :application  {}}) => nil)
      (fact "Filter role-id not in the organization. This should not ever happen."
        (automatic/recipient--organization-handler-role fltr3 {:organization sipoo-org
                                                               :application  {}})
        => {:error :bad-role-id})
      (fact "Filter role-id in the organization, but disabled."
        (automatic/recipient--organization-handler-role fltr1
                                                        {:organization (update sipoo-org :handler-roles
                                                                               (partial map
                                                                                        (fn [{id :id :as h}]
                                                                                          (cond-> h
                                                                                            (= id kvv-role-id) (assoc :disabled true)))))
                                                         :application  {}})
        => {:error :bad-role-id})
      (fact "Filter role-id matches organization handler role"
        (automatic/recipient--organization-handler-role fltr1 {:organization sipoo-org
                                                               :application  {}})
        => {:handler-role-id kvv-role-id}))

    (let [organization (assoc sipoo-org automatic/FILTERS [fltr1 fltr2 fltr3 fltr-tj
                                                           (assoc fltr2
                                                                  :id (pseudo-id "f" 4)
                                                                  :name "F4"
                                                                  :rank 10
                                                                  :target {:handler-role-id kvv-role-id})])
          application  (assoc sipoo-center
                              :handlers [{:id        (pseudo-id "a" 1)
                                          :userId    "hh"
                                          :firstName "Happy"
                                          :lastName  "Handler"
                                          :roleId    kvv-role-id}]
                              :state "submitted")
          options      {:organization organization
                        :application  application}
          command      (update options :organization #(delay %))]
      (facts "resolve-filters"
        (fact "No filter matches"
          (automatic/resolve-filters (assoc options :notice-form-type "location")) => nil)
        (let [result {:filter-id   (pseudo-id "f" 4)
                      :filter-name "F4"
                      :recipient   {:id       "hh"      :username "happy"     :firstName "Happy"
                                    :lastName "Handler" :role     "authority" :handlerId (pseudo-id "a" 1)
                                    :roleId   kvv-role-id}}]
          (fact "Multiple filters match, top ranked are selected"
            (count (automatic/top-matching-filters (assoc options :notice-form-type "construction"))) => 1
            (automatic/resolve-filters (assoc options :notice-form-type "construction")) => [result]
            (provided (lupapalvelu.user/get-user-by-id "hh") => {:username "happy" :role "authority"})
            (automatic/resolve-filters command :notice-form-type "construction") => [result]
            (provided (lupapalvelu.user/get-user-by-id "hh") => {:username "happy" :role "authority"}))

          (fact "No match if application is draft"
            (automatic/resolve-filters (assoc-in command [:application :state] "draft")
                                       :notice-form-type "construction") => nil))

        (fact "Multiple filters match, one with bad target"
          (let [user        {:id       "agile-authority" :username "agile" :firstName "Agile"
                             :lastName "Authority"       :role     "authority"}
                result      (just [{:filter-id   (pseudo-id "f" 2)
                                    :filter-name "F2"
                                    :recipient   user}
                                   {:filter-id   (:id fltr1)
                                    :filter-name "F1"
                                    :recipient   {:roleId kvv-role-id}}] :in-any-order)
                application (assoc application :handlers [])
                att-type    {:type-group "foo" :type-id "bar"}
                options     (-> options
                                (assoc :application application)
                                (update-in [:organization automatic/FILTERS] drop-last)
                                (assoc :attachment-type att-type))]
            (count (automatic/top-matching-filters options)) => 3
            (automatic/resolve-filters options) => result
            (provided
              (lupapalvelu.mongo/select-one :users {:_id            "agile-authority"
                                                    :enabled        true
                                                    :role           "authority"
                                                    :orgAuthz.753-R "authority"})
              => user
              (lupapalvelu.user/get-user-by-id "agile-authority") => anything :times 0)
            (automatic/resolve-filters {:application  application
                                        :organization (delay (:organization options))}
                                      :attachment-type att-type) => result
            (provided
              (lupapalvelu.mongo/select-one :users {:_id            "agile-authority"
                                                    :enabled        true
                                                    :role           "authority"
                                                    :orgAuthz.753-R "authority"})
              => user
              (lupapalvelu.user/get-user-by-id "agile-authority") => anything :times 0)))

        (fact "Organization handler role recipient"
          (let [application (assoc application :handlers [])
                att-type    {:type-group "to" :type-id "do"}
                options     (-> options
                                (assoc :application application)
                                (assoc :attachment-type att-type))
                result      [{:filter-id   (pseudo-id "f" 1)
                              :filter-name "F1"
                              :recipient   {:roleId kvv-role-id}}]]
            (count (automatic/top-matching-filters options)) => 1
            (automatic/resolve-filters options) => result
            (provided
              (lupapalvelu.mongo/select-one :users anything) => anything :times 0
              (lupapalvelu.user/get-user-by-id anything) => anything :times 0)
            (automatic/resolve-filters (assoc command :application application)
                                      :attachment-type att-type) => result
            (provided
              (lupapalvelu.mongo/select-one :users anything) => anything :times 0
              (lupapalvelu.user/get-user-by-id anything) => anything :times 0)
            (fact "Attachment-type option can be string"
              (automatic/resolve-filters (assoc command :application application)
                                        :attachment-type "to.do")
              => result)))
        (fact "Filter without target is the only match"
          (count (automatic/top-matching-filters (assoc options :foreman-role "työnjohtaja"))) => 1
          (automatic/resolve-filters (assoc options :foreman-role "työnjohtaja"))
          => [{:filter-id   (:id fltr-tj)
               :filter-name "TJ"}])
        (fact "User-id selected if the role-id is not present in the application"
          (let [user         {:id       "agile-authority" :username "agile" :firstName "Agile"
                              :lastName "Authority"       :role     "authority"}
                application  (assoc application :handlers [])
                organization (assoc organization automatic/FILTERS [fltr-both])
                att-type     {:type-group "to" :type-id "do"}
                options      (-> options
                                 (assoc :organization organization)
                                 (assoc :application application)
                                 (assoc :attachment-type att-type))
                result       [{:filter-id   (pseudo-id "f" 5)
                               :filter-name "F5"
                               :recipient   user}]]
            (count (automatic/top-matching-filters options)) => 1
            (automatic/resolve-filters options) => result
            (provided
              (lupapalvelu.mongo/select-one :users {:_id            "agile-authority"
                                                    :enabled        true
                                                    :role           "authority"
                                                    :orgAuthz.753-R "authority"})
              => user
              (lupapalvelu.user/get-user-by-id anything) => anything :times 0)
            (automatic/resolve-filters (assoc command
                                              :organization (delay organization)
                                              :application application)
                                      :attachment-type att-type) => result
            (provided
              (lupapalvelu.mongo/select-one :users {:_id            "agile-authority"
                                                    :enabled        true
                                                    :role           "authority"
                                                    :orgAuthz.753-R "authority"})
              => user
              (lupapalvelu.user/get-user-by-id anything) => anything :times 0)
            (fact "Role-id is selected over user-id if the application has a corresponding handler"
              (let [application (assoc application :handlers [{:id        (pseudo-id "a" 1)
                                                               :userId    "hh"
                                                               :firstName "Happy"
                                                               :lastName  "Handler"
                                                               :roleId    kvv-role-id}])
                    result      (assoc-in result [0 :recipient] {:id        "hh"
                                                                 :username  "happy"
                                                                 :firstName "Happy"
                                                                 :lastName  "Handler"
                                                                 :role      "authority"
                                                                 :handlerId (pseudo-id "a" 1)
                                                                 :roleId    kvv-role-id})
                    options     (assoc options :application application)]
                (count (automatic/top-matching-filters options)) => 1
            (automatic/resolve-filters options) => result
            (provided
              (lupapalvelu.mongo/select-one :users anything) => anything :times 0
              (lupapalvelu.user/get-user-by-id "hh") => {:username "happy" :role "authority"})
            (automatic/resolve-filters (assoc command
                                              :organization (delay organization)
                                              :application application)
                                       :attachment-type att-type) => result
            (provided
              (lupapalvelu.mongo/select-one :users anything) => anything :times 0
              (lupapalvelu.user/get-user-by-id "hh") => {:username "happy" :role "authority"})))))))))

(defn mAnGLe [s] (apply str (map (util/fn-> str (cond-> (zero? (rand-int 2)) ss/upper-case)) s)))

(facts "Resolve foreman role"
  (doseq [role schemas/foreman-roles]
    (fact {:midje/description role}
      (automatic/resolve-foreman-role (mAnGLe role)) => role))
  (fact "ei tiedossa"
    (automatic/resolve-foreman-role nil) => "ei tiedossa"
    (automatic/resolve-foreman-role "") => "ei tiedossa"
    (automatic/resolve-foreman-role "bad") => "ei tiedossa"
    (automatic/resolve-foreman-role " työnjohtaja ") => "ei tiedossa"))
