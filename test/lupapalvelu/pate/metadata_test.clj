(ns lupapalvelu.pate.metadata-test
  (:require [lupapalvelu.pate.metadata :refer :all]
            [midje.sweet :refer :all]))

(fact wrap
  (wrap "person" 12345 "hello") => {:_value "hello"
                                    :_user "person"
                                    :_modified 12345}
  (wrap "person" 12345 99) => {:_value 99
                               :_user "person"
                               :_modified 12345}
  (wrap "person" 12345 nil) => {:_value nil
                                :_user "person"
                                :_modified 12345}
  (wrap 22 22 22) => (throws Exception)
  (wrap "person" 12345 {}) => {}
  (wrap "person" 12345 {:foo {:bar 8}}) => {:foo {:bar 8}})

(fact unwrap
  (unwrap 1) => 1
  (unwrap (wrap "person" 12345 "hi")) => "hi"
  (unwrap (wrap "person" 12345 {:_value 22})) => {:_value 22}
  (unwrap {:_value 22 :_modified 1234}) => {:_value 22 :_modified 1234}
  (unwrap {:_value 22 :_modified 1234 :_user "hoo"}) => 22
  (unwrap {:_value 22 :_modified 1234 :_user "hoo" :extra "field"})
  => {:_value 22 :_modified 1234 :_user "hoo" :extra "field"})

(fact wrapper
  ((wrapper "user" 98765) "world") => (wrap "user" 98765 "world" )
  ((wrapper {:user {:username "username"} :created 65432}) 90)
  => (wrap "username" 65432 90))

(fact wrap-all
  (wrap-all (wrapper "hi" 8765) {:foo {:bar 8
                                       :baz [:one :two]
                                       :m {:dum nil}}})
  => {:foo {:bar {:_value 8 :_modified 8765 :_user "hi"}
            :baz {:_value [:one :two] :_modified 8765 :_user "hi"}
            :m {:dum {:_value nil :_modified 8765 :_user "hi"}}}}
  (wrap-all (wrapper "hi" 8765) [1 2 {:hii 99}])
  => {:_value [1 2 {:hii 99}] :_modified 8765 :_user "hi"})

(fact wrap-all
  (unwrap-all {:foo {:bar {:_value 8 :_modified 8765 :_user "hi"}
                     :baz {:_value [:one :two] :_modified 8765 :_user "hi"}
                     :m {:dum {:_value nil :_modified 8765 :_user "hi"}}}} )
  => {:foo {:bar 8
            :baz [:one :two]
            :m {:dum nil}}}
  (unwrap-all {:_value [1 2 {:hii 99}] :_modified 8765 :_user "hi"})
  => [1 2 {:hii 99}]
  (unwrap-all [{:bar {:_value 8 :_modified 8765 :_user "hi"}}
               {:baz {:_value [:one :two] :_modified 8765 :_user "hi"}}
               {:dum {:_value nil :_modified 8765 :_user "hi"}}])
  => [{:bar 8} {:baz [:one :two]} {:dum nil}])
