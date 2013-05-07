var RequestContext = function() {
  var self = this;
  self.id = 0;

  self.begin = function() {
    self.id++;
    return self;
  };

  self.onResponse = function(fn) {
    var requestId = self.id;
    return function(result) {
      if (requestId === self.id && typeof fn === "function") {
        fn(result);
      }
    };
  };
};
