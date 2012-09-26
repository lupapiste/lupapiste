(ns lupapalvelu.components.core-test
  (:use [lupapalvelu.components.core]
        [midje.sweet]))

(def ui-components
  {:root  {:js ["root.js"]}
   :c1    {:js ["c1.js"]
           :depends [:root]}
   :c2    {:js ["c2.js"]
           :depends [:root]}
   :main  {:js ["main.js" "other.js"]
           :depends [:c1 :c2]}})

(facts
  (fact (get-component ui-components :root) => {:js ["root.js"]})
  (fact (get-component ui-components :foo) => (throws Exception)))

(facts
  (fact (exclude [] [] => []))
  (fact (exclude [:a :b :c] []) => [])
  (fact (exclude [] [:a :b :c]) => [:a :b :c])
  (fact (exclude [:a :b :c] [:a :c :e]) => [:e]))

(facts
  (fact (get-dependencies ui-components [] :root) => [:root])
  (fact (get-dependencies ui-components [] :c1) => [:root :c1])
  (fact (get-dependencies ui-components [] :c2) => [:root :c2])
  (fact (get-dependencies ui-components [] :main) => [:root :c1 :c2 :main]))

(facts
  (fact (component-resources ui-components :js :root) => ["root/root.js"])
  (fact (component-resources ui-components :js :main) => ["main/main.js" "main/other.js"])
  (fact (component-resources ui-components :foo :main) => []))

(facts
  (fact (get-resources ui-components :js :root)  =>  ["root/root.js"])
  (fact (get-resources ui-components :js :c1)    =>  ["root/root.js" "c1/c1.js"])
  (fact (get-resources ui-components :js :c2)    =>  ["root/root.js" "c2/c2.js"])
  (fact (get-resources ui-components :js :main)  =>  ["root/root.js" "c1/c1.js" "c2/c2.js" "main/main.js" "main/other.js"]))
