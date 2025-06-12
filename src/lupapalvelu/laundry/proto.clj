(ns lupapalvelu.laundry.proto)

(defprotocol LaundryClient
  "A client for Laundry"
  (get-pdf-package [this content]))
