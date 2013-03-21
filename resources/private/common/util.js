var util = (function() {
  "use strict";

  function fluentify(api, context) {
    return _.reduce(_.pairs(api),
                    function(m, pair) {
                      var k = pair[0],
                          f = pair[1];
                      if (!_.isFunction(f)) throw "The value of key '" + k + "' is not a function: " + f;
                      m[k] = function() { f.apply(context || m, arguments); return m; };
                      return m; },
                    {}); 
  }

  return {
    fluentify: fluentify
  };
  
})();
