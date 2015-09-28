var RequestContext = function(listener) {
  "use strict";

  var self = this;

  listener = listener || {};
  self.onBegin = listener.begin || _.noop;
  self.onDone = listener.done|| _.noop;
  if (!_.isFunction(self.onBegin)) { throw "RequestContext: onBegin must be a function"; }
  if (!_.isFunction(self.onDone)) { throw "RequestContext: onDone must be a function"; }

  self.id = 0;

  self.begin = function() {
    self.onBegin();
    self.id++;
    return self;
  };

  self.onResponse = function(fn) {
    if (fn && !_.isFunction(fn)) { throw "RequestContext.onResponse: fn must be a function: " + fn; }
    var requestId = self.id;
    return function(result) {
      self.onDone();
      if (requestId === self.id && fn) { fn(result); }
    };
  };
};
