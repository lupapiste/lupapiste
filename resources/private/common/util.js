var util = (function() {
  "use strict";

  function nop() {}

  function zeropad(len, val) {
    return _.sprintf("%0" + len + "d", _.isString(val) ? parseInt(val, 10) : val);
  }

  function fluentify(api, context) {
    return _.reduce(_.pairs(api),
                    function(m, pair) {
                      var k = pair[0],
                          f = pair[1];
                      if (!_.isFunction(f)) { throw "The value of key '" + k + "' is not a function: " + f; }
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
    return /\S+@\S+\.\S+/.test(val);
  }

  var propertyIdDbFormat = /^([0-9]{1,3})([0-9]{1,3})([0-9]{1,4})([0-9]{1,4})$/;
  var propertyIdHumanFormat = /^([0-9]{1,3})-([0-9]{1,3})-([0-9]{1,4})-([0-9]{1,4})$/;

  function isPropertyId(s) {
    return propertyIdDbFormat.test(s) || propertyIdHumanFormat.test(s);
  }

  function isPropertyIdInDbFormat(s) {
    return propertyIdDbFormat.test(s);
  }

  function propertyIdToHumanFormat(id) {
    if (!id) { return null; }
    if (propertyIdHumanFormat.test(id)) { return id; }
    var p = propertyIdDbFormat.exec(id);
    if (!p) { return id; }
    return _.partial(_.join, "-").apply(null, _.map(p.slice(1), function(v) { return parseInt(v, 10); }));
  }

  function zp(e) { return zeropad.apply(null, e); }

  function propertyIdToDbFormat(id) {
    if (!id) { return null; }
    if (propertyIdDbFormat.test(id)) { return id; }
    if (!propertyIdHumanFormat.test(id)) { throw "Invalid property ID: " + id; }
    return _.partial(_.join, "").apply(null, _.map(_.zip([3, 3, 4, 4], id.split("-")), zp));
  }

  function makeAjaxMask() {
    return $("<div>")
      .addClass("ajax-loading-mask")
      .append($("<div>")
          .addClass("content")
          .append($('<img src="/img/ajax-loader.gif" class="ajax-loader" width="66" height="66">'))
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

  function isNum(s) {
    return s && s.match(/^\s*\d+\s*$/) !== null;
  }

  return {
    zeropad: zeropad,
    fluentify: fluentify,
    getPwQuality: getPwQuality,
    isValidEmailAddress: isValidEmailAddress,
    isValidPassword: isValidPassword,
    prop: {
      isPropertyId: isPropertyId,
      isPropertyIdInDbFormat: isPropertyIdInDbFormat,
      toHumanFormat: propertyIdToHumanFormat,
      toDbFormat: propertyIdToDbFormat
    },
    nop: nop,
    constantly: function(value) { return function() { return value; }; },
    isNum: isNum
  };

})();
