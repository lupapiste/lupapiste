(ns lupapalvelu.perf-test)

(defn a []
  (Thread/sleep 100)
  "a")

(defn b []
  ; (throw (Exception. "Ups!"))
  (Thread/sleep 200)
  "b")

(defn c []
  (a)
  (a)
  (b))

(defn d []
  (c)
  (a))
