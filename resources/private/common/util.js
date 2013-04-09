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
  
  function isValidPassword(password) {
    return password.length >= LUPAPISTE.config.passwordMinLength;
  }
  
  function getPwQuality(password) {
    var l = password.length;
    if (l < 7)  { return "poor"; }
    if (l <= 8)  { return "low"; }
    if (l <= 10) { return "average"; }
    if (l <= 12) { return "good"; }
    return "excellent";
  }
  
  function isValidEmailAddress(val) {
    return val.indexOf("@") != -1;
  }

  var propertyIdDbFormat = /^([0-9]{1,3})([0-9]{1,3})([0-9]{1,4})([0-9]{1,4})$/
  var propertyIdHumanFormat = /^([0-9]{1,3})-([0-9]{1,3})-([0-9]{1,4})-([0-9]{1,4})$/;
  
  function isPropertyId(s) {
    return propertyIdDbFormat.test(s) || propertyIdHumanFormat.test(s);
  }

  function zp(e) {
    var p = e[0],
        v = e[1],
        l = v.length;
    while (l < p) {
      v = "0" + v;
      l += 1;
    }
    return v;
  }
  
  function propertyIdToHumanFormat(id) {
    if (!id) return null;
    if (propertyIdHumanFormat.test(id)) return id;
    var p = propertyIdDbFormat.exec(id);
    if (!p) throw "Invalid property ID: " + id;
    return _.partial(_.join, "-").apply(null, _.map(p.slice(1), function(v) { return parseInt(v, 10); }));
  }

  function propertyIdToDbFormat(id) {
    if (!id) return null;
    if (propertyIdDbFormat.test(id)) return id;
    if (!propertyIdHumanFormat.test(id)) throw "Invalid property ID: " + id;
    return _.partial(_.join, "").apply(null, _.map(_.zip([3, 3, 4, 4], id.split("-")), zp));
  }

  function makeAjaxMask() {
    return $("<div>")
      .addClass("ajax-loading-mask")
      .append($("<div>")
          .addClass("content")
          .append($("<img>").attr("src", "/img/ajax-loader.gif"))
          .append($("<div>").text(loc("sending"))))
      .fadeIn();
  }
  
  $.fn.ajaxMaskOn = function() {
    this.append(makeAjaxMask());
    return this;
  };
  
  $.fn.ajaxMaskOff = function() {
    this.find(".ajax-loading-mask").remove();
    return this;
  };
  
  $.fn.ajaxMask = function(on) { return on ? this.ajaxMaskOn() : this.ajaxMaskOff(); };
  
  return {
    fluentify: fluentify,
    getPwQuality: getPwQuality,
    isValidEmailAddress: isValidEmailAddress,
    isValidPassword: isValidPassword,
    prop: {
      isPropertyId: isPropertyId,
      toHumanFormat: propertyIdToHumanFormat,
      toDbFormat: propertyIdToDbFormat
    }
  };
  
})();
