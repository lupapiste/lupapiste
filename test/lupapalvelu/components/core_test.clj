(ns lupapalvelu.components.core-test
  (:use [lupapalvelu.components.core]
        [midje.sweet]))

(def test-components
  {:root  {:js ["root.js"]}
   :c1    {:depends [:root]
           :js ["c1.js"]}
   :c2    {:depends [:root]
           :js ["c2.js"]
           :name "CC"}
   :main  {:depends [:c1 :c2]
           :js ["main.js" "other.js"]}})

(facts
  (fact (get-component test-components :root) => {:js ["root.js"]})
  (fact (get-component test-components :foo)  => (throws Exception)))

(facts
  (fact (exclude [] []                   => []))
  (fact (exclude [:a :b :c] [])          => [])
  (fact (exclude [] [:a :b :c])          => [:a :b :c])
  (fact (exclude [:a :b :c] [:a :c :e])  => [:e]))

(facts
  (fact (get-dependencies test-components [] :root) => [:root])
  (fact (get-dependencies test-components [] :c1)   => [:root :c1])
  (fact (get-dependencies test-components [] :c2)   => [:root :c2])
  (fact (get-dependencies test-components [] :main) => [:root :c1 :c2 :main]))

(facts
  (fact (component-resources test-components :js :root)  => ["root/root.js"])
  (fact (component-resources test-components :js :c1)    => ["c1/c1.js"])
  (fact (component-resources test-components :js :c2)    => ["CC/c2.js"])
  (fact (component-resources test-components :js :main)  => ["main/main.js" "main/other.js"])
  (fact (component-resources test-components :foo :main) => []))

(facts
  (fact (get-resources test-components :js :root)  =>  ["root/root.js"])
  (fact (get-resources test-components :js :c1)    =>  ["root/root.js" "c1/c1.js"])
  (fact (get-resources test-components :js :c2)    =>  ["root/root.js" "CC/c2.js"])
  (fact (get-resources test-components :js :main)  =>  ["root/root.js" "c1/c1.js" "CC/c2.js" "main/main.js" "main/other.js"]))
