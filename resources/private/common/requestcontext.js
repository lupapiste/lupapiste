var RequestContext = function(listener) {
  var self = this;

  listener = listener || {};
  self.onBegin = listener.begin || util.nop;
  self.onDone = listener.done|| util.nop;
  
  self.id = 0;

  self.begin = function() {
    self.onBegin();
    self.id++;
    return self;
  };

  self.onResponse = function(fn) {
    var requestId = self.id;
    return function(result) {
      self.onDone();
      if (requestId === self.id && typeof fn === "function") {
        fn(result);
      }
    };
  };
};
