var ajax = (function($) {
  "use strict";

  var callId = 0;

  function Call(url, type) {
    var self = this;

    var defaultError = function(e) {
      error("AJAX: ERROR", self.request.url, e);
      notify.ajaxError(e);
    };

    self.customErrorHandlers = {};

    var resolveErrorHandler = function(e) {
      return self.customErrorHandlers[e.text] ? self.customErrorHandlers[e.text] : self.errorHandler;
    };

    self.onComplete = function(jqXHR, textStatus) {
      if (self.pendingListener) {
        clearTimeout(self.pendingHandler);
        self.pendingListener(false);
      }
      if (self.processingListener) {
        self.processingListener(false);
      }
      self.completeHandler(jqXHR, textStatus);
    };

    self.callId = callId++;
    self.request = {
      url:       url,
      type:      type,
      dataType:  "json",
      data:      {},
      cache:     false,
      timeout:   60000,
      rawData:   false,
      complete:  self.onComplete,
      success: function(e) {
        if (self.rawData || e && e.ok) {
          self.successHandler.call(self.savedThis, e);
        } else if (e) {
          if (self.fuseListener) {
            self.fuseListener(false);
          }
          var res = resolveErrorHandler(e).call(self.savedThis, e);
          if (res && res.ok === false) {
            defaultError(e);
          }
        } else {
          error("Ajax: No response from " + self.request.url);
        }
      },
      error: function(jqXHR, textStatus, errorThrown) {
        self.failHandler(jqXHR, textStatus, errorThrown);
        return false;
      },
      beforeSend: function(request) {
        _.each(self.headers, function(value, key) { request.setRequestHeader(key, value); });
        request.setRequestHeader("x-anti-forgery-token", $.cookie("anti-csrf-token"));
      }
    };

    self.successHandler = function() { };
    self.errorHandler = defaultError;
    self.failHandler = function(jqXHR, textStatus, errorThrown) {
      if (self.fuseListener) {
        self.fuseListener(false);
      }
      if (jqXHR && jqXHR.status > 0 && jqXHR.readyState > 0) {
        switch (jqXHR.status) {
          case 403:
            // Ignored
            break;
          case 405:
            notify.error(loc("error.service-lockdown"));
            break;
          default:
            var maxLength = 200;
            var responseText = /<html/i.test(jqXHR.responseText) ?
                _.filter($.parseHTML(jqXHR.responseText).map(function(e) {
                  var text = $.trim(e.innerText || e.textContent || "");
                  return text.length > maxLength ? text.substring(0, maxLength) + "..." : text;
                }))
                : jqXHR.responseText;
            error("Ajax: FAIL", self.request.url, jqXHR.status + " " + errorThrown, responseText);
            break;
        }
      }
    };
    self.completeHandler = function() { };
    self.headers = {};

    self.raw = function(v) {
      self.rawData = (v === undefined) ? true : v;
      return self;
    };

    self.dataType = function(dataType) {
      self.request.dataType = dataType;
      return self;
    };

    self.param = function(name, value) {
      self.request.data[name] = value;
      return self;
    };

    self.params = function(data) {
      if (data) { _.each(data, function(v, k) { self.param(k, v); }); }
      return self;
    };

    self.json = function(data) {
      self.request.data = data ? JSON.stringify(data) : {};
      self.request.contentType = "application/json";
      return self;
    };

    self.form = function(formData) {
      self.request.data = formData;
      self.request.processData = false;
      self.request.contentType = false;
      self.request.enctype = "multipart/form-data";
      return self;
    };

    self.success = function(f, savedThis) {
      self.successHandler = f;
      self.savedThis = savedThis;
      return self;
    };

    self.successEvent = function(n) {
      return self.success(function(e) { hub.send(n, e); });
    };

    self.onError = function(errorText, f, savedThis) {
      self.customErrorHandlers[errorText] = f;
      self.savedThis = savedThis;
      return self;
    };

    self.error = function(f, savedThis) {
      self.errorHandler = f;
      self.savedThis = savedThis;
      return self;
    };

    self.errorEvent = function(n) {
      return self.error(function(e) { hub.send(n, e); });
    };

    self.fail = function(f) {
      self.failHandler = f;
      return self;
    };

    self.failEvent = function(n) {
      return self.fail(function(e) { hub.send(n, e); });
    };

    self.complete = function(f) {
      self.completeHandler = f;
      return self;
    };

    self.completeEvent = function(n) {
      return self.complete(function(e) { hub.send(n, e); });
    };

    self.timeout = function(v) {
      self.request.timeout = v;
      return self;
    };

    self.fuse = function(listener) {
      if (_.isFunction(listener)) {
        self.fuseListener = listener;
      } else {
        error("fuse listener must be a function", listener, self.request.url);
      }
      return self;
    };

    self.processing = function(listener) {
      if (_.isFunction(listener)) {
        self.processingListener = listener;
        self.processingListener(false);
      } else {
        error("processing listener must be a function", listener, self.request.url);
      }
      return self;
    };

    self.pending = function(listener, timeout) {
      if (_.isFunction(listener)) {
        self.pendingListener = listener;
        self.pendingTimeout = timeout || 100;
        self.pendingListener(false);
      } else {
        error("pending listener must be a function", listener, self.request.url);
      }
      return self;
    };

    self.call = function() {
      if (self.fuseListener) {
        if (self.fuseListener()) {
          debug("Fuse is blown, call aborted");
          return;
        }
        self.fuseListener(true);
      }
      if (self.processingListener) {
        self.processingListener(true);
      }
      if (self.pendingListener) {
        self.pendingHandler = setTimeout(_.partial(self.pendingListener, true), self.pendingTimeout);
      }
      return $.ajax(self.request);
    };

    self.header = function(key, value) {
      self.headers[key] = value;
      return self;
    };
  }

  function get(url) {
    return new Call(url, "GET").raw();
  }

  function post(url) {
    return new Call(url, "POST").raw();
  }

  function deleteReq(url) {
    return new Call(url, "DELETE").raw();
  }

  function postJson(url, data) {
    return new Call(url, "POST").raw().json(data);
  }

  function command(name, data) {
    return new Call("/api/command/" + name, "POST").json(data);
  }

  function query(name, data) {
    return new Call("/api/query/" + name, "GET").params(data);
  }

  function datatables(name, data) {
    return new Call("/api/datatables/" + name, "POST").json(data);
  }

  function form(name, formData) {
    return new Call("/api/raw/" + name, "POST").form(formData);
  }

  return {
    post:      post,
    postJson:  postJson,
    get:       get,
    deleteReq: deleteReq,
    command:   command,
    query:     query,
    datatables: datatables,
    form: form
  };

})(jQuery);
