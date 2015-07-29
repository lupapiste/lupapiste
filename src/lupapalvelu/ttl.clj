(ns lupapalvelu.ttl)

(def default-ttl (* 180 24 60 60 1000))               ; 180 days
(def reset-password-token-ttl (* 24 60 60 1000))      ; 1 day
(def create-user-token-ttl (* 180 24 60 60 1000))     ; 180 days
(def idf-create-user-token-ttl (* 180 24 60 60 1000)) ; 180 days
(def company-invite-ttl (* 90 24 60 60 1000))         ; 90 days
(def neighbor-token-ttl (* 3 7 24 60 60 1000))        ; 3 weeks
