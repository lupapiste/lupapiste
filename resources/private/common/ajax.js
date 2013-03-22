var ajax = (function() {
  "use strict";

  var callId = 0;

  function Call(url, type) {
    var self = this;

    self.onComplete = function(jqXHR, textStatus) {
      if (self.pendingListener) {
        clearTimeout(self.pendingHandler);
        self.pendingListener(false);
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
        handler(e);
      },
      error: function(jqXHR, textStatus, errorThrown) {
        self.failHandler(jqXHR, textStatus, errorThrown);
        return false;
      },
      beforeSend: function(request) {
        _.each(self.headers, function(value, key) { request.setRequestHeader(key, value); });
        request.setRequestHeader("x-anti-forgery-token", $.cookie("lupapiste-token"));
      }
    };

    self.successHandler = function(e) { };
    self.errorHandler = function(e) { notify.error("error",e); };
    self.failHandler = function(jqXHR, textStatus, errorThrown) { error("Ajax: FAIL", self.request.url, jqXHR, textStatus, errorThrown); };
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
      self.request.data = data ? JSON.stringify(data) : null;
      self.request.contentType = "application/json";
      return self;
    };

    self.success = function(f) {
      self.successHandler = f;
      return self;
    };

    self.successEvent = function(n) {
      return self.success(function(e) { hub.send(n, e); });
    };

    self.error = function(f) {
      self.errorHandler = f;
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

    self.pending = function(listener, timeout) {
      if (!listener) return self;
      if (!_.isFunction(listener)) throw "Argument must be a function: " + listener;
      self.pendingListener = listener;
      self.pendingTimeout = timeout || 100;
      self.pendingListener(false);
      return self;
    };
    
    self.call = function() {
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

  return {
    post:      post,
    postJson:  postJson,
    get:       get,
    command:   command,
    query:     query
  };

})();
