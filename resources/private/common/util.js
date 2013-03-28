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
  
  function getPwQuality(password) {
    var l = password.length;
    if (l <= 6)  { return "poor"; }
    if (l <= 8)  { return "low"; }
    if (l <= 10) { return "average"; }
    if (l <= 12) { return "good"; }
    return "excellent";
  }
  
  function isValidEmailAddress(val) {
    return val.indexOf("@") != -1;
  }

  return {
    fluentify: fluentify,
    getPwQuality: getPwQuality,
    isValidEmailAddress: isValidEmailAddress
  };
  
})();
