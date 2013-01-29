var ajax = (function() {
  "use strict";

  var nop = function() { };

  var callId = 0;

  function Call(url, type) {
    var self = this;

    self.callId = callId++;
    self.request = {
      url:       url,
      type:      type,
      dataType:  "json",
      data:      {},
      cache:     false,
      timeout:   60000,
      rawData:   false,
      success: function(e) {
        var handler = (self.rawData || e.ok) ? self.successHandler : self.errorHandler;
        handler(e);
        onAjaxCallSuccess(self);
      },
      error: function(jqXHR, textStatus, errorThrown) {
        self.failHandler(jqXHR, textStatus, errorThrown);
        return false;
      },
      complete: function(jqXHR, textStatus) {
        self.completeHandler(jqXHR, textStatus);
      }
    };

    self.successHandler = function(e) { notify.success("ok",e); };
    self.errorHandler = function(e) { notify.error("error",e); };
    self.failHandler = function(jqXHR, textStatus, errorThrown) { error("Ajax: FAIL", jqXHR, textStatus, errorThrown); };
    self.completeHandler = function(jqXHR, textStatus) { };

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
      for (var key in data) {
        self.param(key, data[key]);
      }
      return self;
    };

    self.json = function(data) {
      self.request.data = JSON.stringify(data);
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

    self.call = function() {
      onAjaxRequestCall(self);
      $.ajax(self.request);
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

  function onAjaxRequestCall(c) {
    $('.ajax-calls').append('<span class="ajax-call" data-ajax-call-id="'+c.callId+'">Ajax: '+c.request.url+'</span><br/>');
  }

  function onAjaxCallSuccess(c) {
    $('.ajax-calls').children('span[data-ajax-call-id="'+c.callId+'"]').remove();
  }

  return {
    post:      post,
    postJson:  postJson,
    get:       get,
    command:   command,
    query:     query
  };

})();
