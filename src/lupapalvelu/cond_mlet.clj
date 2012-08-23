(ns lupapalvelu.cond-mlet)

(defn error? [r]
  (and (vector? r) (= (r 0) :error)))

(defmacro cond-mlet [bindings & body]
  (let [[context f & more] bindings]
    `(let [t# ~f]
       (if (error? t#)
         (t# 1)
         (let [~context t#]
           ~(if more
              `(cond-mlet [~@more] ~@body)
              `(eval ~@body)))))))
