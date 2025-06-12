(ns lupapalvelu.ui2.util)

(defmacro defuic
  "Define a function for an UI component.

  UI component is a function that should takes optionally
  a properties map as a first parameter, and all the following
  parameters are considered the children elements.

  Usually UI component parameters binding vector should look like `[props children]`,
  though other names can be used also. Props is either the map or nil."
  {:arglists '([name doc-string? attr-map? params & body])}
  [name & args]
  (let [;; Might be nice (or maybe even important in some case) to forward
        ;; doc-string and attr-map to the output? But doesn't matter currently.
        ;; Need to parse them here to find out the params vector from args.
        [_docstring args] (if (string? (first args))
                            [(first args) (rest args)]
                            [nil args])
        [_attr-map args] (if (map? (first args))
                           [(first args) (rest args)]
                           [nil args])
        [params & body] args]
    `(defn ~name
       [& args#]
       (let [~params (if (map? (first args#))
                       [(first args#) (rest args#)]
                       [nil args#])]
         ~@body))))
