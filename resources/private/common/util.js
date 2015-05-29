var util = (function($) {
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
    if (!password) {
      return null;
    }

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

  function propertyIdToDbFormat(id) {
    if (!id) { return null; }
    if (propertyIdDbFormat.test(id)) { return id; }
    if (!propertyIdHumanFormat.test(id)) { throw "Invalid property ID: " + id; }
    return _.partial(_.join, "").apply(null, _.map(_.zip([3, 3, 4, 4], id.split("-")), zp));
  }

  function zp(e) { return zeropad.apply(null, e); }

  function buildingName(building) {
    var buildingObj = (typeof building.index === "function") ? ko.mapping.toJS(building) : building;
    var id = buildingObj.buildingId ? " " + buildingObj.buildingId : "";
    var usage = buildingObj.usage ? " (" + buildingObj.usage + ")": "";
    var area = (buildingObj.area || "?") + " " + loc("unit.m2");
    return buildingObj.index + "." + id + usage + " - " + area;
  }

  function makeAjaxMask() {
    return $("<div>")
      .addClass("ajax-loading-mask")
      .append($("<div>")
          .addClass("content")
          .append($("<img src=\"/img/ajax-loader.gif\" class=\"ajax-loader\" width=\"66\" height=\"66\">"))
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

  function autofocus(baseElem$) {
    var base$ = baseElem$ || $("body");
    return base$.find("[autofocus]:enabled:visible:first").focus();
  }

  function isNum(s) {
    return s && s.match(/^\s*\d+\s*$/) !== null;
  }

  function getIn(m, keyArray, defaultValue) {
    if (m && keyArray && keyArray.length > 0) {
      var key = keyArray[0];
      if (m.hasOwnProperty(key)) {
        var val = ko.unwrap(m[key]);
        if (keyArray.length === 1) {
          return val;
        }
        return getIn(val, keyArray.splice(1, keyArray.length - 1), defaultValue);
      }
    }
    return defaultValue;
  }

    function locKeyFromDocPath(pathStr) {
    var res = (pathStr.replace(/\.+\d+\./g, ".")).replace(/\.+/g, ".");
    return res;
  }

  function getDocumentOrder(doc) {
    var num = doc.schema.info.order || 7;
    return num * 10000000000 + doc.created / 1000;
  }

  function isPartyDoc(doc) { return doc["schema-info"].type === "party"; }
  function isNotPartyDoc(doc) { return !isPartyDoc(doc); }

  function isValidY(y) {
    var m = /^(\d{7})-(\d)$/.exec(y || ""),
        number = m && m[1],
        check  = m && m[2];

    if (!m) { return false; }

    var cn = _(number)
      .chars()
      .map(function(c) { return parseInt(c, 10); })
      .zip([7, 9, 10, 5, 8, 4, 2])
      .map(function(p) { return p[0] * p[1]; })
      .reduce(function(acc, v) { return acc + v; });
    cn = cn % 11;
    cn = (cn === 0) ? cn : 11 - cn;
    return cn === parseInt(check, 10);
  }

  function isValidOVT(ovt) {
    var m = /^0037(\d{7})(\d)\d{0,5}$/.exec(ovt || ""),
        y = m && m[1],
        c = m && m[2];
    if (!y || !c) { return false; }
    return isValidY(y + "-" + c);
  }

  var personIdCn = ["0","1","2","3","4","5","6","7","8","9","A","B","C","D",
    "E","F","H","J","K","L","M","N","P","R","S","T","U","V",
    "W","X","Y"]

  function isValidPersonId(personId) {
    var m = /^(\d{6})[aA+-]([0-9]{3})([0-9A-Z])$/.exec(personId || ""),
        n = m && m[1] + m[2],
        c = m && m[3];

    if(!m) { return false; }

    return personIdCn[parseInt(n, 10) % 31] === c;
  }

  function extractRequiredErrors(errors) {
    var errs = _.map(errors, function(errArray) {
      return _.filter(errArray, function(err) {
        var ret = _.includes(err.result, "illegal-value:required");
        return ret;
      });
    });
    errs = _.filter(errs, function(errArray) {
      return errArray.length > 0;
    });
    return errs;
  }

  function dissoc(m, k) {
    delete m[k];
    return m;
  }

  return {
    zeropad:             zeropad,
    fluentify:           fluentify,
    getPwQuality:        getPwQuality,
    isValidEmailAddress: isValidEmailAddress,
    isValidPassword:     isValidPassword,
    isValidY:            isValidY,
    isValidOVT:          isValidOVT,
    isValidPersonId:     isValidPersonId,
    prop: {
      isPropertyId:           isPropertyId,
      isPropertyIdInDbFormat: isPropertyIdInDbFormat,
      toHumanFormat:          propertyIdToHumanFormat,
      toDbFormat:             propertyIdToDbFormat
    },
    buildingName: buildingName,
    nop:          nop,
    constantly:   function(value) { return function() { return value; }; },
    autofocus:    autofocus,
    isNum:        isNum,
    getIn:        getIn,
    locKeyFromDocPath: locKeyFromDocPath,
    getDocumentOrder: getDocumentOrder,
    isPartyDoc: isPartyDoc,
    isNotPartyDoc: isNotPartyDoc,
    extractRequiredErrors: extractRequiredErrors,
    dissoc: dissoc
  };

})(jQuery);
