(ns sade.security-headers)

(defn add-security-headers
  "Ring middleware.
   Sets X-XSS-Protection, X-Frame-Options and X-Content-Type-Options headers.
   Ported from Debian Unstable apache2.2-common /etc/apache2/conf.d/security,
   see also http://en.wikipedia.org/wiki/List_of_HTTP_header_fields#Common_non-standard_response_headers"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (when response
        (-> response
          ; Some browsers have a built-in XSS filter that will detect some cross site
          ; scripting attacks. By default, these browsers modify the suspicious part of
          ; the page and display the result. This behavior can create various problems
          ; including new security issues. This header will tell the XSS filter to
          ; completely block access to the page instead.
          (assoc-in [:headers "X-XSS-Protection"] "1; mode=block")

          ; Prevents other sites from embedding pages from this
          ; site as frames. This defends against clickjacking attacks.
          (assoc-in [:headers "X-Frame-Options"] "sameorigin")

          ; Prevents MSIE from interpreting files as something
          ; else than declared by the content type in the HTTP headers.
          (assoc-in [:headers "X-Content-Type-Options"] "nosniff"))))))

