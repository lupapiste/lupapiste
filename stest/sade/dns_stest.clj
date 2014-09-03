(ns sade.dns-stest
  (:require [midje.sweet :refer :all]
            [sade.dns :refer :all]))

(fact "nil is not a valid MX record" (valid-mx-domain? nil) => false)
(fact "blank is not a valid MX record" (valid-mx-domain? "") => false)
(fact "blank domain is not a valid MX record" (valid-mx-domain? "user@") => false)

(fact "Gmail has a valid MX record"
  (valid-mx-domain? "gmail.com") => true
  (valid-mx-domain? "@gmail.com") => true
  (valid-mx-domain? "testi.suunnittelija@gmail.com") => true)

(fact "Example.com does not have a valid MX record"
  (valid-mx-domain? "example.com") => false
  (valid-mx-domain? "@example.com") => false
  (valid-mx-domain? "testi.suunnittelija@example.com") => false)