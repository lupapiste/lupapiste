(ns sade.coordinate-test
  (:require [midje.sweet :refer :all]
            [sade.coordinate :as coordinate]))

(fact "EPSG:3067 to WGS84 works"
  (coordinate/convert "EPSG:3067" "WGS84" 3 [420893.934152 7177728.76993]) => [25.340 64.715])

(fact "WGS84 to EPSG:3067 works"
  (coordinate/convert "WGS84" "EPSG:3067" 4 [25.34 64.715]) => [420893.9342 7177728.7700])

(fact "EPSG:3879 to EPSG:3067 works"
  (coordinate/convert "EPSG:3879" "EPSG:3067" 3 [2.5502936E7 6708332.0]) => [393033.614 6707228.994])

(facts "coordinate validator"
  (coordinate/validate-x {:data {:x nil}}) => nil
  (coordinate/validate-y {:data {:y nil}}) => nil
  (coordinate/validate-x {:data {:x ""}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-x {:data {:x "0"}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-x {:data {:x "1000"}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-x {:data {:x "10001"}}) => nil
  (coordinate/validate-x {:data {:x "799999"}}) => nil
  (coordinate/validate-x {:data {:x "800000"}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-y {:data {:y ""}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-y {:data {:y "0"}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-y {:data {:y "6609999"}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-y {:data {:y "6610000"}}) => nil
  (coordinate/validate-y {:data {:y "7780000"}}) => {:ok false :text "error.illegal-coordinates"}
  (coordinate/validate-y {:data {:y "7779999"}}) => nil)

(facts "coordinate validation"
  (coordinate/valid-x? nil) => false
  (coordinate/valid-y? nil) => false
  (coordinate/valid-x? "") => false
  (coordinate/valid-x? "0") => false
  (coordinate/valid-x? "1000") => false
  (coordinate/valid-x? "") => false
  (coordinate/valid-x? "799999") => true
  (coordinate/valid-x? "800000") => false
  (coordinate/valid-y? "") => false
  (coordinate/valid-y? "0") => false
  (coordinate/valid-y? "6609999") => false
  (coordinate/valid-y? "6610000") => true
  (coordinate/valid-y? "7780000") => false
  (coordinate/valid-y? "7779999") => true

  (fact "works with strings and numbers"
    (coordinate/validate-coordinates [10001 6610000]) => nil
    (coordinate/validate-coordinates ["10001" "6610000"]) => nil
    (coordinate/validate-coordinates [800000 7780000]) => {:ok false :text "error.illegal-coordinates"}
    (coordinate/validate-coordinates ["800000" "7780000"]) => {:ok false :text "error.illegal-coordinates"}))

(fact "Known bad coordinates are invalid"
  (every? coordinate/validate-coordinates
          coordinate/known-bad-coordinates) => true
  (coordinate/validate-coordinates [0.0 0.0]) => truthy)
