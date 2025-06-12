(ns lupapalvelu.invoices.transfer-batch-test
  (:require [lupapalvelu.invoices.transfer-batch :as tb ]
            [midje.sweet :refer :all]))

(fact "filename-prefix"
  (tb/filename-prefix {:empty "config"} nil) => ""
  (tb/filename-prefix {:empty "config"}) => ""
  (tb/filename-prefix {:empty "config"} "          ") => ""
  (tb/filename-prefix {:invoice-file-prefix "prefix"}) => "prefix"
  (tb/filename-prefix {:invoice-file-prefix "prefix"} "default-value") => "prefix"
  (tb/filename-prefix {:invoice-file-prefix ""} "default-value") => "default-value"
  (tb/filename-prefix {:invoice-file-prefix "    "} "default-value") => "default-value"
  (tb/filename-prefix {:invoice-file-prefix "  with-surrounding-whitespace  "} "default-value")
  => "with-surrounding-whitespace"
  (tb/filename-prefix {:invoice-file-prefix "  with    _a_    hole  "} "default-value")
  => "with_a_hole"
  (tb/filename-prefix {} "default-value") => "default-value")
