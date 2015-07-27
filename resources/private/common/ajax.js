var ajax = (function($) {
  "use strict";

  var callId = 0;

  function Call(url, type) {
    var self = this;

    var defaultError = function(e) {
      error("AJAX: ERROR", self.request.url, e);
      notify.error(loc(e.text));
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
        var handler = (self.rawData || e.ok) ? self.successHandler : self.errorHandler;
        var res = handler.call(self.savedThis, e);
        if ( res && !res.ok ) {
          defaultError(e);
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
      if (jqXHR && jqXHR.status > 0 &&jqXHR.status !== 403 && jqXHR.readyState > 0) {
        error("Ajax: FAIL", self.request.url, jqXHR, textStatus, errorThrown);
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

    self.success = function(f, savedThis) {
      self.successHandler = f;
      self.savedThis = savedThis;
      return self;
    };

    self.successEvent = function(n) {
      return self.success(function(e) { hub.send(n, e); });
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

    self.processing = function(listener) {
      if (!listener) { return self; }
      if (!_.isFunction(listener)) { throw "Argument must be a function: " + listener; }
      self.processingListener = listener;
      self.processingListener(false);
      return self;
    };

    self.pending = function(listener, timeout) {
      if (!listener) { return self; }
      if (!_.isFunction(listener)) { throw "Argument must be a function: " + listener; }
      self.pendingListener = listener;
      self.pendingTimeout = timeout || 100;
      self.pendingListener(false);
      return self;
    };

    self.call = function() {
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

  return {
    post:      post,
    postJson:  postJson,
    get:       get,
    command:   command,
    query:     query,
    datatables: datatables
  };

})(jQuery);
