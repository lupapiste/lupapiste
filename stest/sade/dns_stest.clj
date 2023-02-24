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

(fact "foo.bar does not have a valid MX record"
  (valid-mx-domain? "foo.bar") => false
  (valid-mx-domain? "@foo.bar") => false
  (valid-mx-domain? "testi.suunnittelija@foo.bar") => false)
