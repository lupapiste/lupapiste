var RequestContext = function(listener) {
  var self = this;

  listener = listener || {};
  self.onBegin = listener.begin || util.nop;
  self.onDone = listener.done|| util.nop;
  if (!_.isFunction(self.onBegin)) throw "RequestContext: onBegin must be a function";
  if (!_.isFunction(self.onDone)) throw "RequestContext: onDone must be a function";
  
  self.id = 0;

  self.begin = function() {
    self.onBegin();
    self.id++;
    return self;
  };

  self.onResponse = function(fn) {
    if (!_.isFunction(fn)) throw "RequestContext.onResponse: fn must be a function";
    var requestId = self.id;
    return function(result) {
      self.onDone();
      if (requestId === self.id) fn(result);
    };
  };
};
