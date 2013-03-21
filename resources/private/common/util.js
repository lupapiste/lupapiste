var util = (function() {
  "use strict";

  function fluentify(api, context) {
    return _.reduce(_.pairs(api),
                    function(m, pair) {
                      var k = pair[0],
                          f = pair[1];
                      m[k] = function() { f.apply(context || m, arguments); return m; };
                      return m; },
                    {}); 
  }

  return {
    fluentify: fluentify
  };
  
})();
