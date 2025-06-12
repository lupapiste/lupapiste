(ns sade.async
  (:require [lupapalvelu.logging :as log]))

(defmacro future* [& body]
  `(future
     (try
       ~@body
       (catch Throwable e#
         (log/errorf e# (format "unhandled exception in future at %s:%d" *file* ~(-> &form meta :line)))
         (throw e#)))))
