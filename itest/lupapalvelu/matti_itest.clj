(ns lupapalvelu.matti-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(defn err [error]
  (partial expected-failure? error))

(defn prefix-keys [m prefix]
  (reduce (fn [acc [k v]]
            (assoc acc
                   (->> k name (str (name prefix)) keyword) v))
          {}
          m))

(facts "Settings"
  (fact "Bad category"
    (query sipoo :verdict-template-settings
           :category "foo") => (err :error.invalid-category))
  (fact "No settings"
    (query sipoo :verdict-template-settings
           :category "r")=> (just {:ok true}))
  (fact "Save to bad path"
    (command sipoo :save-verdict-template-settings-value
                   :category "r"
                   :path [:one :two]
                   :value ["a" "b" "c"])
    => (err :error.invalid-value-path))
  (fact "Save bad value"
    (command sipoo :save-verdict-template-settings-value
             :category "r"
             :path [:foremen]
             :value [:bad-tj])
    => (err :error.invalid-value))
  (fact "Save settings draft"
    (let [{modified :modified}
          (command sipoo :save-verdict-template-settings-value
                   :category "r"
                   :path [:verdict-code]
                   :value [:ehdollinen :ei-puollettu
                           :evatty :hyvaksytty])]
      modified => pos?
      (fact "Query settings"
        (query sipoo :verdict-template-settings
               :category "r")
        => (contains {:settings {:draft    {:verdict-code ["ehdollinen" "ei-puollettu"
                                                           "evatty" "hyvaksytty"]}
                                 :modified modified}})))))

(fact "Sipoo categories"
  (:categories (query sipoo :verdict-template-categories))
  => (contains ["r" "p" "ymp" "kt"] :in-any-order))

(fact "Create new template"
  (let [{:keys [id name draft modified category]}
        (command sipoo :new-verdict-template :category "r")]
    id => string?
    name => "P\u00e4\u00e4t\u00f6spohja"
    draft => {}
    modified => pos?
    category => "r"
    (fact "Fetch draft"
      (query sipoo :verdict-template :template-id id)
      => (contains {:id       id
                    :name     name
                    :draft    {}
                    :modified modified}))
    (fact "Template list"
      (query sipoo :verdict-templates)
      => (contains {:verdict-templates [{:id        id
                                         :name      name
                                         :modified  modified
                                         :deleted   false
                                         :category  "r"
                                         :published nil}]}))
    (fact "Change the name"
      (let [{later :modified}
            (command sipoo :set-verdict-template-name
                     :template-id id
                     :name "Uusi nimi")]
        (- later modified) => pos?
        (fact "Save draft data value"
          (let [{even-later :modified}
                (command sipoo :save-verdict-template-draft-value
                         :template-id id
                         :path [:giver]
                         :value :viranhaltija)]
            (- even-later later) => pos?
            (fact "Fetch draft again"
              (query sipoo :verdict-template :template-id id)
              => (contains {:id       id
                            :name     "Uusi nimi"
                            :draft    {:giver "viranhaltija"}
                            :modified even-later}))
            (fact "Enable anto date delta"
              (command sipoo :save-verdict-template-draft-value
                       :template-id id
                       :path [:anto :enabled]
                       :value true) => ok?)
            (fact "Set anto date delta"
              (command sipoo :save-verdict-template-draft-value
                       :template-id id
                       :path [:anto :delta]
                       :value 2)=> ok?)
            (fact "Set foremen removed"
              (command sipoo :save-verdict-template-draft-value
                       :template-id id
                       :path [:removed-sections :foremen]
                       :value true) => ok?)
            (fact "Set plans removed"
              (let [{last-edit :modified}
                    (command sipoo :save-verdict-template-draft-value
                             :template-id id
                             :path [:removed-sections :plans]
                             :value true) => ok?]
                (fact "Fetch draft to see the compound items are OK"
                  (query sipoo :verdict-template :template-id id)
                  => (contains {:id    id
                                :name  "Uusi nimi"
                                :draft {:giver            "viranhaltija"
                                        :anto             {:enabled true
                                                           :delta   2}
                                        :removed-sections {:foremen true
                                                           :plans   true}}
                                :modified last-edit}))
                (fact "Publish template"
                  (let [{published :published}
                        (command sipoo :publish-verdict-template
                                 :template-id id)]
                    (- published last-edit) => pos?
                    (fact "Delete template"
                      (command sipoo :toggle-delete-verdict-template
                               :template-id id
                               :delete true) => ok?)
                    (fact "Template list again. Publishing and deletion do not affect modified timestamp."
                      (query sipoo :verdict-templates)
                      => (contains {:verdict-templates [{:id        id
                                                         :name      "Uusi nimi"
                                                         :modified  last-edit
                                                         :deleted   true
                                                         :category  "r"
                                                         :published published}]}))
                    (fact "Name change not allowed for deleted template"
                      (command sipoo :set-verdict-template-name
                               :template-id id
                               :name "Foo")
                      => (err :error.verdict-template-deleted))
                    (fact "Draft data update not allowed for deleted template"
                      (command sipoo :save-verdict-template-draft-value
                               :template-id id
                               :path [:hii]
                               :value "Foo")
                      => (err :error.verdict-template-deleted))
                    (fact "Publish not allowed for deleted template"
                      (command sipoo :publish-verdict-template
                               :template-id id)
                      => (err :error.verdict-template-deleted))
                    (fact "Fetch draft not allowed for deleted template"
                      (query sipoo :verdict-template
                             :template-id id)
                      => (err :error.verdict-template-deleted))
                    (fact "Copying is allowed also for deleted templates"
                      (let [{:keys [copy-id copy-modified copy-published
                                    copy-deleted copy-draft copy-name
                                    copy-category]}
                            (prefix-keys (command sipoo :copy-verdict-template
                                                  :template-id id)
                                         :copy-)]
                        copy-id =not=> id
                        (- copy-modified published) => pos?
                        copy-published => nil
                        copy-category => "r"
                        copy-name => "Uusi nimi (kopio)"
                        copy-draft => {:giver            "viranhaltija"
                                       :anto             {:enabled true
                                                          :delta   2}
                                       :removed-sections {:foremen true
                                                          :plans   true}}
                        (fact "Editing copy draft does not affect original"
                          (command sipoo :save-verdict-template-draft-value
                                   :template-id copy-id
                                   :path [:paatosteksti]
                                   :value  "This is the verdict.") => ok?
                          (fact "Copy has new data"
                            (query sipoo :verdict-template
                                   :template-id copy-id)
                            => (contains {:draft {:giver            "viranhaltija"
                                                  :anto             {:enabled true
                                                                     :delta   2}
                                                  :removed-sections {:foremen true
                                                                     :plans   true}
                                                  :paatosteksti     "This is the verdict."}
                                          :name  "Uusi nimi (kopio)"}))
                          (fact "Restore the deleted template"
                            (command sipoo :toggle-delete-verdict-template
                                     :template-id id
                                     :delete false) => ok?)
                          (fact "The original (restored) template does not have new data"
                            (query sipoo :verdict-template
                                   :template-id id)
                            => (contains {:draft {:giver            "viranhaltija"
                                                  :anto             {:enabled true
                                                                     :delta   2}
                                                  :removed-sections {:foremen true
                                                                     :plans   true}}
                                          :name  "Uusi nimi"}))
                          (fact "Template list has both templates"
                            (->> (query sipoo :verdict-templates)
                                 :verdict-templates
                                 (map #(select-keys % [:id :name])))
                            =>  [{:id id :name "Uusi nimi"}
                                 {:id copy-id :name "Uusi nimi (kopio)"}]))))))))))))))

(fact "Delete nonexisting template"
  (command sipoo :toggle-delete-verdict-template
           :template-id "bad-id" :delete true)
  => (err :error.verdict-template-not-found))

(fact "Copy nonexisting template"
  (command sipoo :copy-verdict-template
           :template-id "bad-id")
  => (err :error.verdict-template-not-found))

(facts "Data path and value validation"
  (let [{id :id} (command sipoo :new-verdict-template :category "r")]
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:julkipano :enabled]
             :value "bad")=> (err :error.invalid-value)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:bad :path]
             :value false)=> (err :error.invalid-value-path)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:valitus :delta]
             :value -8) => (err :error.invalid-value)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:verdict-code]
             :value :bad) => (err :error.invalid-value)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:verdict-code]
             :value :hyvaksytty) => ok?))

(facts "Reviews"
  (fact "Initially empty"
    (query sipoo :verdict-template-reviews :category "r")
    => {:ok      true
        :reviews []})
  (fact "Add new review"
    (let [{id :id} (:review (command sipoo :add-verdict-template-review
                                     :category "r"))]
      id => truthy
      (fact "Fetch reviews again"
        (:reviews (query sipoo :verdict-template-reviews :category "r"))
        => (just [(contains {:name     {:fi "Katselmus"
                                        :sv "Syn"
                                        :en "Review"}
                             :category "r"
                             :deleted  false
                             :type     "muu-katselmus"})]))
      (fact "Give name to the review"
        (:review (command sipoo :update-verdict-template-review
                          :review-id id
                          :fi "Nimi" :sv "Namn" :en "Name"))
        => (contains {:id       id
                      :name     {:fi "Nimi" :sv "Namn" :en "Name"}
                      :deleted  false
                      :category "r"
                      :type     "muu-katselmus"}))
      (fact "New name available"
        (:reviews (query sipoo :verdict-template-reviews :category "r"))
        => (just [(contains {:name     {:fi "Nimi" :sv "Namn" :en "Name"}
                             :category "r"
                             :deleted  false
                             :type     "muu-katselmus"})]))
      (facts "Update review details"
        (fact "Finnish name"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :fi "Moro")
          => (contains {:review (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Namn"
                                                  :en "Name"}})}))
        (fact "Name cannot be empty"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :en "  ")
          => (err :error.name-blank))
        (fact "Review type"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous)
          => (contains {:review (contains {:id id
                                           :type "aloituskokous"})}))
        (fact "Invalid review type"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :hiihoo)
          => (err :error.invalid-review-type))
        (fact "Swedish and English names, review type"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :sv "Stockholm" :en "London" :type :lvi-katselmus)
          => (contains {:review (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Stockholm"
                                                  :en "London"}
                                           :type "lvi-katselmus"})}))
        (fact "Unsupported params"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous
                   :foo "bar")
          => (err :error.unsupported-parameters))
        (fact "Mark review as deleted"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :deleted true)
          => (contains {:review (contains {:id id
                                           :deleted true})}))
        (fact "Deleted review cannot be re-deleted"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :deleted true)
          => (err :error.settings-item-deleted))
        (fact "Deleted reviews cannot be edited"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous
                   :fi "Hei")
          => (err :error.settings-item-deleted))
        (fact "Deleted reviews can be restored (and edited)"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous
                   :fi "Hei"
                   :deleted false)
          => (contains {:review (contains {:id id
                                           :type "aloituskokous"
                                           :name (contains {:fi "Hei"})
                                           :deleted false})}))
        (fact "Review not found"
          (command sipoo :update-verdict-template-review
                   :review-id "notfoun"
                   :type :aloituskokous
                   :fi "Hei")
          => (err :error.settings-item-not-found))))))

(facts "Plans"
  (fact "Initially empty"
    (query sipoo :verdict-template-plans :category "r")
    => {:ok      true
        :plans []})
  (fact "Add new plan"
    (let [{id :id} (:plan (command sipoo :add-verdict-template-plan
                                     :category "r"))]
      id => truthy
      (fact "Fetch plans again"
        (:plans (query sipoo :verdict-template-plans :category "r"))
        => (just [(contains {:name     {:fi "Suunnitelmat"
                                        :sv "Planer"
                                        :en "Plans"}
                             :category "r"
                             :deleted  false})]))
      (fact "Give name to the plan"
        (:plan (command sipoo :update-verdict-template-plan
                          :plan-id id
                          :fi "Nimi" :sv "Namn" :en "Name"))
        => (contains {:id       id
                      :name     {:fi "Nimi" :sv "Namn" :en "Name"}
                      :deleted  false
                      :category "r"}))
      (fact "New name available"
        (:plans (query sipoo :verdict-template-plans :category "r"))
        => (just [(contains {:name     {:fi "Nimi" :sv "Namn" :en "Name"}
                             :category "r"
                             :deleted  false})]))
      (facts "Update plan details"
        (fact "Finnish name"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :fi "Moro")
          => (contains {:plan (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Namn"
                                                  :en "Name"}})}))
        (fact "Name cannot be empty"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :en "  ")
          => (err :error.name-blank))
        (fact "Swedish and English names"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :sv "Stockholm" :en "London")
          => (contains {:plan (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Stockholm"
                                                  :en "London"}})}))
        (fact "Unsupported params"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :type :aloituskokous)
          => (err :error.unsupported-parameters))
        (fact "Mark plan as deleted"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :deleted true)
          => (contains {:plan (contains {:id id
                                           :deleted true})}))
        (fact "Deleted plan cannot be re-deleted"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :deleted true)
          => (err :error.settings-item-deleted))
        (fact "Deleted plans cannot be edited"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :fi "Hei")
          => (err :error.settings-item-deleted))
        (fact "Deleted plans can be restored (and edited)"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :fi "Hei"
                   :deleted false)
          => (contains {:plan (contains {:id id
                                         :name (contains {:fi "Hei"})
                                         :deleted false})}))
        (fact "Plan not found"
          (command sipoo :update-verdict-template-plan
                   :plan-id "notfound"
                   :fi "Hei")
          => (err :error.settings-item-not-found))))))

(facts "Operation default verdict template"
  (fact "No defaults yet"
    (:templates (query sipoo :default-operation-verdict-templates))
    => {})
  (let [{template-id :id} (command sipoo :new-verdict-template :category "r")]
    (fact "Verdict template not published"
      (command sipoo :set-default-operation-verdict-template
               :operation "pientalo" :template-id template-id)
      => (err :error.verdict-template-not-published))
    (fact "Publish the verdict template"
      (command sipoo :publish-verdict-template :template-id template-id)
      => ok?)
    (fact "Delete verdict template"
      (command sipoo :toggle-delete-verdict-template
               :template-id template-id :delete true)
      => ok?)
    (fact "Verdict template not editable"
      (command sipoo :set-default-operation-verdict-template
               :operation "pientalo" :template-id template-id)
      => (err :error.verdict-template-deleted))
    (fact "Delete verdict template"
      (command sipoo :toggle-delete-verdict-template
               :template-id template-id :delete false)
      => ok?)
    (fact "Bad operation"
      (command sipoo :set-default-operation-verdict-template
               :operation "bad-operation" :template-id template-id)
      => (err :error.unknown-operation))
    (fact "Bad operation category"
      (command sipoo :set-default-operation-verdict-template
               :operation "rasitetoimitus" :template-id template-id)
      => (err :error.invalid-category))
    (fact "Set template for operation"
      (command sipoo :set-default-operation-verdict-template
               :operation "pientalo" :template-id template-id)
      => ok?)
    (fact "Defaults are no longer empty"
      (:templates (query sipoo :default-operation-verdict-templates))
      => {:pientalo template-id})
    (fact "Set template for another operation"
      (command sipoo :set-default-operation-verdict-template
               :operation "muu-laajentaminen" :template-id template-id)
      => ok?)
    (fact "Defaults has two items"
      (:templates (query sipoo :default-operation-verdict-templates))
      => {:pientalo template-id :muu-laajentaminen template-id})
    (fact "Empty template-id clears default"
      (command sipoo :set-default-operation-verdict-template
               :operation "muu-laajentaminen" :template-id "")
      => ok?
      (:templates (query sipoo :default-operation-verdict-templates))
      => {:pientalo template-id})
    (fact "Deleted template can no longer be default"
      (command sipoo :toggle-delete-verdict-template
               :template-id template-id :delete true)
      => ok?
      (:templates (query sipoo :default-operation-verdict-templates))
      => {})
    (fact "Undoing deletion makes template default again"
      (command sipoo :toggle-delete-verdict-template
               :template-id template-id :delete false)
      => ok?
      (:templates (query sipoo :default-operation-verdict-templates))
      => {:pientalo template-id})

    (facts "Application verdict templates"
      (let [{app-id :id} (create-and-submit-application pena
                                                        :operation :pientalo
                                                        :propertyId sipoo-property-id)]
        (fact "Create and delete verdict template"
          (let [{tmp-id :id} (command sipoo :new-verdict-template
                                      :category "r")]
            (command sipoo :toggle-delete-verdict-template
                     :template-id tmp-id :delete true) => ok?))
        (fact "Rename assumed default"
          (command sipoo :set-verdict-template-name
                   :template-id template-id
                   :name "Oletus"))
        (:templates (query sonja :application-verdict-templates :id app-id))
        => (just [(just {:id template-id
                         :default? true
                         :name "Oletus"})
                  (just {:id #"\w+"
                         :default? false
                         :name "Uusi nimi"})] :in-any-order)))))
