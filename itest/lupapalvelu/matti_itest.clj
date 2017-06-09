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

(fact "Create new template"
  (let [{:keys [id name draft modified]} (command sipoo :new-verdict-template)]
    id => string?
    name => "Päätöspohja"
    draft => {}
    modified => pos?
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
                         :path [:one :two]
                         :value "hello")]
            (- even-later later) => pos?
            (fact "Fetch draft again"
              (query sipoo :verdict-template :template-id id)
              => (contains {:id       id
                            :name     "Uusi nimi"
                            :draft    {:one {:two "hello"}}
                            :modified even-later}))
            (fact "Publish template"
              (let [{published :published}
                    (command sipoo :publish-verdict-template
                             :template-id id)]
                (- published even-later) => pos?
                (fact "Delete template"
                  (command sipoo :toggle-delete-verdict-template
                           :template-id id
                           :delete true) => ok?)
                (fact "Template list again. Publishing and deletion do not affect modified timestamp."
                  (query sipoo :verdict-templates)
                  => (contains {:verdict-templates [{:id        id
                                                     :name      "Uusi nimi"
                                                     :modified  even-later
                                                     :deleted   true
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
                                copy-deleted copy-draft copy-name]}
                        (prefix-keys (command sipoo :copy-verdict-template
                                              :template-id id)
                                     :copy-)]
                    copy-id =not=> id
                    (- copy-modified published) => pos?
                    copy-published => nil
                    copy-name => "Uusi nimi (kopio)"
                    copy-draft => {:one {:two "hello"}}
                    (fact "Editing copy draft does not affect original"
                      (command sipoo :save-verdict-template-draft-value
                               :template-id copy-id
                               :path [:three :four]
                               :value "world") => ok?
                      (fact "Copy has new data"
                        (query sipoo :verdict-template
                               :template-id copy-id)
                        => (contains {:draft {:one   {:two "hello"}
                                              :three {:four "world"}}
                                      :name "Uusi nimi (kopio)"}))
                      (fact "Restore the deleted template"
                        (command sipoo :toggle-delete-verdict-template
                                 :template-id id
                                 :delete false) => ok?)
                      (fact "The original (restored) template does not have new data"
                        (query sipoo :verdict-template
                               :template-id id)
                        => (contains {:draft {:one {:two "hello"}}
                                      :name "Uusi nimi"}))
                      (fact "Template list has both templates"
                        (->> (query sipoo :verdict-templates)
                             :verdict-templates
                             (map #(select-keys % [:id :name])))
                        =>  [{:id id :name "Uusi nimi"}
                             {:id copy-id :name "Uusi nimi (kopio)"}]))))))))))))
