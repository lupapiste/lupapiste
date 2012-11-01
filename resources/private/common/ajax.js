/*
 * ajax.js:
 */

var ajax = function() {

  var nop = function() { };
  
  function Call(url, type) {
    hub.setPageNotReady();

    var self = this;
    
    self.request = {
      url:       url,
      type:      type,
      dataType:  "json",
      data:      {},
      cache:     false,
      timeout:   60000,
      success: function(e) {
        var handler = e.ok ? self.successHandler : self.errorHandler;
        handler(e);
      },
      error: function(jqXHR, textStatus, errorThrown) {
        self.failHandler(jqXHR, textStatus, errorThrown);
        return false;
      },
      complete: function(jqXHR, textStatus) {
        self.completeHandler(jqXHR, textStatus);
      }
    };
    
    self.successHandler = function(e) { notify.error("rest",e); };
    self.errorHandler = function(e) { notify.error("error",e); };
    self.failHandler = function(jqXHR, textStatus, errorThrown) { error("Ajax: FAIL", jqXHR, textStatus, errorThrown); };
    self.completeHandler = function(jqXHR, textStatus) { };
    
    self.dataType = function(dataType) {
      self.request.dataType = dataType;
      return self;
    }
    
    self.param = function(name, value) {
      self.request.data[name] = value;
      return self;
    } 
    
    self.params = function(data) {
      for (var key in data) {
        self.param(key, data[key]);
      }
      return self;
    } 
    
    self.json = function(data) {
      self.request.data = JSON.stringify(data);
      self.request.contentType = "application/json";
      return self;
    } 
    
    self.success = function(f) {
      self.successHandler = f;
      return self;
    } 

    self.successEvent = function(n) {
      return self.success(function(e) { hub.send(n, e); });
    } 

    self.error = function(f) {
      self.errorHandler = f;
      return self;
    } 
    
    self.errorEvent = function(n) {
      return self.error(function(e) { hub.send(n, e); });
    } 

    self.fail = function(f) {
      self.failHandler = f;
      return self;
    } 
    
    self.failEvent = function(n) {
      return self.fail(function(e) { hub.send(n, e); });
    }
    
    self.complete = function(f) {
      self.completeHandler = f;
      return self;
    } 
    
    self.completeEvent = function(n) {
      return self.complete(function(e) { hub.send(n, e); });
    }
    
    self.timeout = function(v) {
      self.request.timeout = v;
      return self;
    }

    self.call = function() {
      $.ajax(self.request);
      return self;
    }
  }

  function get(url) {
    return new Call(url, "GET");
  }
  
  function post(url) {
    return new Call(url, "POST");
  }
  
  function postJson(url, data) {
    return new Call(url, "POST").json(data);
  }
  
  function command(name, data) {
    // TODO: Do we need this copy?
    var message = {};
    for (var k in data) message[k] = data[k];
    return new Call("/rest/command/"+name, "POST").json(message);
  }

  function query(name, data) {
    var message = {};
    for (var k in data) message[k] = data[k];
    return new Call("/rest/query/"+name, "GET").params(message);
  }

  return {
    post:      post,
    postJson:  postJson,
    get:       get,
    command:   command,
    query:     query
  };
  
}();
