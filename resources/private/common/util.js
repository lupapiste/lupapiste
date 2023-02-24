var util = (function($) {
  "use strict";

  function zeropad(len, val) {
    return sprintf("%0" + len + "d", _.isString(val) ? parseInt(val, 10) : val);
  }

  function zp(e) { return zeropad.apply(null, e); }

  function fluentify(api, context) {
    return _.reduce(_.toPairs(api),
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
    if (l < 12)  { return "poor"; }
    if (l <= 15) { return "average"; }
    if (l <= 19) { return "good"; }
    return "excellent";
  }

  // Transforms eg. "0506547890" to "050 654 7890"
  function formatPhoneNumber(number) {
    if (!number) {
      return "";
    }
    var numString = _.replace(number.toString(), /\s+/g, ""); // Remove all whitespace
    var hasPlus = _.startsWith(numString, "+");
    var indices = hasPlus ? [9, 6, 4] : [6, 3];

    // Use array for inserting spaces before the indices
    var numArray = numString.split("");
    indices.forEach(function(index) {
      numArray.splice(index, 0, " ");
    });
    return _.trim(numArray.join(""));
  }

  // See http://emailregex.com/
  function isValidEmailAddress(val) {
    return /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/.test(val);
  }

  var propertyIdDbFormat = /^([0-9]{1,3})([0-9]{1,3})([0-9]{1,4})([0-9]{1,4})$/;
  var propertyIdHumanFormat = /^([1-9]{1}[0-9]{0,2})-([0-9]{0,3})-([0-9]{1,4})-([0-9]{1,4})$/;
  var maaraalaFormat = /^-?M[0-9]{1,4}$/;

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
    return _.join( _.map(p.slice(1), function(v) { return parseInt(v, 10); }), "-");
  }

  function propertyIdToDbFormat(id) {
    if (!id) { return null; }
    if (propertyIdDbFormat.test(id)) { return id; }
    if (!propertyIdHumanFormat.test(id)) { throw "Invalid property ID: " + id; }
    return _.join( _.map(_.zip([3, 3, 4, 4], id.split("-")), zp), "");
  }

  // Undefined is allowed
  function checkMaaraalaNumber( s ) {
    return _.isUndefined( s )
    || ( maaraalaFormat.test( "M" + s )
      &&  _.inRange( _.parseInt( s), 1, 10000 ));
  }

  // Maarala can be missing. We allow also shorter M numbers.
  function isPropertyIdWithMaaraalaInDbFormat( s ) {
    var parts = _.split( _.trim(s), "M" );
    return isPropertyIdInDbFormat( parts[0] )
        && ( _.size( parts ) === 1
          || checkMaaraalaNumber( parts[1] ));
  }

  function isPropertyIdWithMaaraalaInHumanFormat( s ) {
    var parts = _.split( _.trim(s), /-?M/ );
    return propertyIdHumanFormat.test( parts[0] )
        && ( _.size( parts ) === 1
          || checkMaaraalaNumber( parts[1] ));
  }

  function isPropertyIdWithMaaraala( s ) {
    return isPropertyIdWithMaaraalaInDbFormat( s )
        || isPropertyIdWithMaaraalaInHumanFormat( s );
  }

  function propertyIdWithMaaralaToHumanFormat( id ) {
    id = _.trim( id );
    if( isPropertyIdWithMaaraalaInHumanFormat( id ) ) {
      return id;
    }
    var parts = _.split( id, "M" );
    var propId = propertyIdToHumanFormat( parts[0] );
    if( _.size( parts ) === 1 ) {
      return propId;
    }
    if( checkMaaraalaNumber( parts[1])) {
      return sprintf( "%s-M%s", propId, _.parseInt( parts[1]));
    }
    // Invalid id
    return id;
  }

  function propertyIdWithMaaralaToDbFormat( id ) {
    id = _.trim( id );
    if( isPropertyIdWithMaaraalaInDbFormat( id ) ) {
      return id;
    }
    var parts = _.split( id, /-?M/ );
    var propId = propertyIdToDbFormat( parts[0] );
    if( _.size( parts ) === 1 ) {
      return propId;
    }
    if( checkMaaraalaNumber( parts[1])) {
      return sprintf( "%sM%04d", propId, _.parseInt( parts[1]));
    }
    // Invalid id
    return id;
  }

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
          .append($("<img src=\"/lp-static/img/ajax-loader.gif\" class=\"ajax-loader\" width=\"66\" height=\"66\">"))
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
    return s && s.match(/^\s*-??\d+\s*$/) !== null;
  }

  function isNonNegative(s) {
    return s && s.match(/^\s*\d+\s*$/) !== null;
  }

  function getIn(m, ks, defaultValue) {
    var value = ko.unwrap(m);
    if (_.isUndefined(value) || _.isNull(value)) {
      return defaultValue;
    }
    return _.isEmpty(ks) ? value : getIn(value[_.head(ks)], _.tail(ks), defaultValue);
  }

  function getFeatureName(feature) {
    for(var key in feature.properties) { // properties to lower case
      if (key.toLowerCase() === "nimi") {
        return feature.properties[key].toString();
      }
    }
    return undefined;
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
    var m = /^0037(\d{7})(\d)\w{0,5}$/.exec(ovt || ""),
        y = m && m[1],
        c = m && m[2];
    if (!y || !c) { return false; }
    return isValidY(y + "-" + c);
  }

  var personIdCn = ["0","1","2","3","4","5","6","7","8","9","A","B","C","D",
    "E","F","H","J","K","L","M","N","P","R","S","T","U","V",
    "W","X","Y"];

  function isValidPersonId(personId) {
    var m = /^(\d{6})[aA+-]([0-9]{3})([0-9A-Z])$/.exec(personId || ""),
        n = m && m[1] + m[2],
        c = m && m[3];

    if(!m) { return false; }

    return personIdCn[parseInt(n, 10) % 31] === c;
  }



  var extractErrors = function(filterFn, errors) {
    var errs = _.map(errors, function(errArray) {
      return _.filter(errArray, function(err) {
        return filterFn(err.result);
      });
    });
    errs = _.filter(errs, function(errArray) {
      return errArray.length > 0;
    });
    return errs;
  };

  var extractRequiredErrors = _.partial(extractErrors, function(errResult) {
    return _.includes(errResult, "illegal-value:required");
  });

  var extractWarnErrors = _.partial(extractErrors, function(errResult) {
    return _.includes(errResult, "warn");
  });



  function dissoc(m, k) {
    if( _.isObject( m ) ) {
      delete m[k];
    }
    return m;
  }

  function randomElementId(prefix) {
    var random = _.random(Number.MAX_SAFE_INTEGER);
    var id = prefix ? prefix + "-" + random : random.toString();
    if ($(id).length) {
      return randomElementId(prefix);
    }
    return id;
  }

  function withSuffix(strOrArr, suffix) {
    if (_.isArray(strOrArr)) {
      return _.map(strOrArr, function(s) {
        return s + suffix;
      });
    }
    return [strOrArr + suffix];
  }

  var defaultOptions = {
    data: [],
    query: "",
    selected: undefined,
    label: "label"
  };

  var compareItems = _.curry(function(a, b) {
    var ua = ko.toJS(a);
    var ub = ko.toJS(b);
    if (ua && ub && ua["type-group"] && ua["type-id"]) {
      return ua["type-group"] === ub["type-group"] && ua["type-id"] === ub["type-id"];
    } else {
      return _.isEqual(ua, ub);
    }
  });

  function filterDataByQuery(options) {
    options = _.defaults(options, defaultOptions);
    if (_.isEmpty(options.query) && _.isEmpty(options.selected)) {
      // if there is nothing to filter with, return the input options data as-is
      return options.data;
    } else {
      var filtered = _.filter(options.data, function(item) {
          return _.reduce(options.query.split(" "), function(result, word) {
            var dataForLabel = ko.unwrap(item[options.label]);
            var isSelected = _.isArray(options.selected) ? _.some(options.selected, compareItems(item)) : compareItems(options.selected, item);
            return !isSelected && dataForLabel !== undefined && _.includes(dataForLabel.toUpperCase(), word.toUpperCase()) && result;
          }, true);
        });

      // The options are sorted so that titles beginning with the user input string are first, and if several options
      // begin with the input, shorter options are preferred. I.e. for search string "appl" the first match will be
      // "Application".
      return filtered.sort(function (a, b) {
        var labelA = ko.unwrap(a[options.label]).toUpperCase();
        var labelB = ko.unwrap(b[options.label]).toUpperCase();
        var query = options.query.toUpperCase();
        if (_.startsWith(labelA, query) && _.startsWith(labelB, query)) {
          return labelA.length - labelB.length;
        } else if (_.startsWith(labelA, query)) {
          return -1;
        } else {
          return _.startsWith( labelB, query ) ? 1 : 0;
        }
      });
    }
  }

  function showSavedIndicator(response) {
    hub.send("indicator", {style: response.ok ? "positive" : "negative", message: response.text});
  }

  function showSavedIndicatorIcon(response) {
    if (response.ok) {
      hub.send("indicator-icon", {style: "positive"});
    } else {
      hub.send("indicator-icon", {style: "negative", message: response.text});
    }
  }

  // Shows OK dialog, with text (loc key) from respose
  // To define click handler for "OK" button, give options object looking like following:
  // {componentParams: {okFn: your-callback-function-here}}
  function showErrorDialog(response, options) {
    var defaultParams = {ltitle: "error.dialog.title",
                         size: "medium",
                         component: "ok-dialog",
                         componentParams: {ltext: response.text}};
    hub.send("show-dialog", _.merge(defaultParams, options));
  }

  function createSortableColumn(index, text, opts) {
    index = index || "";
    text = text || "";
    var colspan = util.getIn(opts, ["colspan"], 1);
    var sortable = util.getIn(opts, ["sortable"], true);
    var sortField = util.getIn(opts, ["sortField"], "");
    var currentSort = util.getIn(opts, ["currentSort"], {field: ko.observable(), asc: ko.observable(false)});

    function sortBy(target) {
      if ( target === currentSort.field() ) {
        currentSort.asc(!currentSort.asc()); // toggle direction
      } else {
        currentSort.field(target);
        currentSort.asc(false);
      }
    }

    var css = [index];
    if (sortable) {
      css.push("sorting");
    }

    return { clickFn: sortable ? _.partial(sortBy, sortField) : null,
             css: css.join(" "),
             ltext: text,
             attr: {colspan: colspan, "data-test-id": "search-column-" + loc(text)},
             isDescending: ko.pureComputed(function() {
               return currentSort.field() === sortField && !currentSort.asc();
             }),
             isAscending: ko.pureComputed(function() {
               return currentSort.field() === sortField && currentSort.asc();
             }) };
  }

  function elementInViewport(element) {
    var top = element.offsetTop;
    var left = element.offsetLeft;
    var width = element.offsetWidth;
    var height = element.offsetHeight;

    while(element.offsetParent) {
      element = element.offsetParent;
      top += element.offsetTop;
      left += element.offsetLeft;
    }

    return (
      top >= window.pageYOffset &&
      left >= window.pageXOffset &&
      (top + height) <= (window.pageYOffset + window.innerHeight) &&
      (left + width) <= (window.pageXOffset + window.innerWidth)
    );
  }

  function getSchemaElement(docSchema, path) {
    return _.reduce(path, function(acc, name) {
      return _.find(acc.body, ["name", name]);
    }, docSchema);
  }

  function bySchemaName(schemaName) {
    return function(task) {
      return util.getIn(task, ["schema-info", "name"]) === schemaName;
    };
  }

  function tasksDataBySchemaName(tasks, schemaName, mapper) {
    return _(tasks).filter(bySchemaName(schemaName)).map(mapper).value();
  }

  function calculateVerdictTasks(verdict, tasks) {
    // Manual verdicts have one paatokset item
    if (verdict.paatokset && verdict.paatokset.length === 1) {

      var myTasks = _.filter(tasks, function(task) {
        return task.source && task.source.type === "verdict" && task.source.id === verdict.id;
      });

      var lupamaaraykset = _(verdict.paatokset || []).map("lupamaaraykset").filter().value();

      if (lupamaaraykset.length === 0 && myTasks.length > 0) {
        var katselmukset = tasksDataBySchemaName(myTasks, "task-katselmus", function(task) {
          return {katselmuksenLaji: util.getIn(task, ["data", "katselmuksenLaji", "value"], "muu katselmus"), tarkastuksenTaiKatselmuksenNimi: task.taskname};
        });
        var tyonjohtajat = tasksDataBySchemaName(myTasks, "task-vaadittu-tyonjohtaja", _.property("taskname"));
        var muut = tasksDataBySchemaName(myTasks, "task-lupamaarays", _.property("taskname"));

        return {vaaditutTyonjohtajat: tyonjohtajat,
                muutMaaraykset: muut,
                vaaditutKatselmukset: katselmukset};
      }
    }
  }

  function verdictsWithTasks(application) {
    return _.map(application ? application.verdicts : [], function(verdict) {
      var lupamaaraykset = calculateVerdictTasks(verdict, application.tasks);
      if (lupamaaraykset) {
        verdict.paatokset[0].lupamaaraykset = lupamaaraykset;
      }
      return verdict;
    });
  }

  function getPreviousState(application) {
    var history = _.filter(getIn(application, ["history"]), "state"); // select only entries with 'state' value
    var previous = _.head(_.takeRight(history, 2));
    return ko.unwrap(previous.state);
  }

  function partyFullName(party) {
    return ko.unwrap(party.firstName) + " " + ko.unwrap(party.lastName);
  }

  // Path can be string "a.b.c" or array [a, b, c].
  function isEmpty( data, path ) {
    return _.isEmpty( getIn( data, _
                             .isString( path ) ? _.split( path, ".") : path));
  }

  function strictParseFloat( s ) {
    s =  _.replace(_.trim( s ), ",", ".");
    return _.every( _.split( s, "."), isNum  ) ? parseFloat( s ) : NaN;
  }

  // Zips given array into object.
  // Optional fun argument is the value function (default _.constant( true )):
  function arrayToObject( arr, fun ) {
    return _.zipObject( arr,
                        _.map( arr,
                               fun || _.constant( true )));
  }

  var usagePurposeFromUrl = _.once(function () {
    var match = window.location.pathname.match("/app/[a-z]{2}/([^/]*)/?([^/]*)");
    if (match) {
      var usagePurpose = {type: match[1]};
      if (match[2]) { usagePurpose.orgId = match[2]; }
      return usagePurpose;
    } else {
      return undefined;
    }
  });

  function identLogoutUrl() {
    return util.getIn(LUPAPISTE.config, ["identMethods", "logoutUrl"]);
  }

  function identLogoutRedirect() {
    var url = identLogoutUrl();
    var suffix = "/login/" + loc.getCurrentLanguage();
    if (url) {
      ajax.deleteReq("/api/vetuma/user")
        .success(function() {
          window.location = _.escape(url) + "?return=" + suffix;
        })
        .error(function() {
          window.location = _.escape(url) + "?return=" + suffix;
        }).call();
    }
  }

  function resolveIdentLogoutRedirectSuffix() {
    var startPage = lupapisteApp.startPage;
    if (startPage === "local-bulletins") {
      return "/app/" + loc.getCurrentLanguage() + "/local-bulletins?organization=" + pageutil.getURLParameter("organization");
    } else {
      return "/app/" + loc.getCurrentLanguage() + "/" + startPage;
    }
  }

  function identLogoutRedirectBulletins() {
    var url = identLogoutUrl();
    var suffix = resolveIdentLogoutRedirectSuffix();
    if (url) {
      ajax.deleteReq("/api/vetuma/user")
        .success(function() {
          window.location = _.escape(url) + "?return=" + _.escape(suffix);
        })
        .error(function() {
          window.location = _.escape(url) + "?return=" + _.escape(suffix);
        }).call();
    }
  }

  // True if every key in sub has an equal value in obj.
  function isSubObject( obj, sub ) {
    return _.isEqual( _.pick( obj, _.keys( sub )), sub );
  }

  function isOdd( number ) {
    return number % 2;
  }
  function isEven( number ) {
    return !isOdd(number);
  }

  function isValidBSONObjectId( s ) {
    return s && s.match(/^[0-9a-fA-F]{24}$/) !== null;
  }

  // L10n date formats only used when parsing.
  // Date is always displayed in the Finnish format.
  var dateFormats = {fi: "D.M.YYYY",
                     sv: "D.M.YYYY",
                     en: "M/D/YYYY",
                     iso: "YYYY-MM-DD"};

  // Converts date to moment. The first argument is considered
  // timestamp (string or int), if it can be converted to positive
  // integer and lang is not given. Returns valid moment or null.
  function toMoment( dateOrTimestamp, lang ) {
    var arg = _.trim( dateOrTimestamp );
    var m = null;
    if( arg ) {
      if( lang ) {
        m = moment( arg, dateFormats[lang], true );
      } else {
        var ts = _.toInteger( arg );
        m = ts > 0 ? moment( ts ) : null;
      }
    }
    return m && m.isValid() ? m : null;
  }

  function formatMoment( m, lang )  {
    lang = lang || loc.getCurrentLanguage();
    return m && m.format( dateFormats[lang] );
  }

  // Converts/enforces Finnish date representation.
  // Params: [optional]
  //  dateArg:  dateString, timestamp (ms from Unix epoch) or moment.
  //  [lang]: fi/sv/en Language for dateArg (if dateString)
  // Returns invalid dates unchanged. If you want to discard bad
  // dates, call toMoment first.
  function finnishDate( dateArg, lang ) {
    var m = moment.isMoment( dateArg) ? dateArg : toMoment( dateArg, lang );
    return m ? m.format( dateFormats.fi ) : dateArg;
  }

  function finnishDateAndTime( timestamp, fmt ) {
    return moment( timestamp ).format( fmt ? fmt : "D.M.YYYY, HH:mm:ss");
  }

  // v is unwrapped size value
  function sizeString( v ) {
    var result = "";
    if( _.trim( v ) ) {
      var value = parseFloat(v);
      var unit = "B";

      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "kB";
      }

      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "MB";
      }
      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "GB";
      }
      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "TB"; // Future proof?
      }

      if (unit !== "B") {
        value = value.toFixed(1);
      }
      result = value + "\u00a0" + unit;
    }
    return result;
  }

  /**
   * Compares two software version numbers (e.g. "1.7.1" or "1.2b").
   *
   * This function was born in http://stackoverflow.com/a/6832721.
   *
   * @param {string} v1 The first version to be compared.
   * @param {string} v2 The second version to be compared.
   * @param {object} [options] Optional flags that affect comparison behavior:
   * <ul>
   *     <li>
   *         <tt>lexicographical: true</tt> compares each part of the version strings lexicographically instead of
   *         naturally; this allows suffixes such as "b" or "dev" but will cause "1.10" to be considered smaller than
   *         "1.2".
   *     </li>
   *     <li>
   *         <tt>zeroExtend: true</tt> changes the result if one version string has less parts than the other. In
   *         this case the shorter string will be padded with "zero" parts instead of being considered smaller.
   *     </li>
   * </ul>
   * @returns {number|NaN}
   * <ul>
   *    <li>0 if the versions are equal</li>
   *    <li>a negative integer iff v1 < v2</li>
   *    <li>a positive integer iff v1 > v2</li>
   *    <li>NaN if either version string is in the wrong format</li>
   * </ul>
   *
   * @copyright by Jon Papaioannou (["john", "papaioannou"].join(".") + "@gmail.com")
   * @license This function is in the public domain. Do what you want with it, no strings attached.
   */

  function versionCompare(v1, v2, options) {
    var lexicographical = options && options.lexicographical,
        zeroExtend = options && options.zeroExtend,
        v1parts = v1.split("."),
        v2parts = v2.split(".");

    function isValidPart(x) {
      return (lexicographical ? /^\d+[A-Za-z]*$/ : /^\d+$/).test(x);
    }

    if (!v1parts.every(isValidPart) || !v2parts.every(isValidPart)) {
      return NaN;
    }

    if (zeroExtend) {
      while (v1parts.length < v2parts.length) {
        v1parts.push("0");
      }
      while (v2parts.length < v1parts.length) {
        v2parts.push("0");
      }
    }

    if (!lexicographical) {
      v1parts = v1parts.map(Number);
      v2parts = v2parts.map(Number);
    }

    for (var i = 0; i < v1parts.length; ++i) {
      if (v2parts.length === i) {
        return 1;
      }

      if (v1parts[i] === v2parts[i]) {
        continue;
      }
      else if (v1parts[i] > v2parts[i]) {
        return 1;
      }
      else {
        return -1;
      }
    }

    if (v1parts.length !== v2parts.length) {
      return -1;
    }

    return 0;
  }

  function localeCompare( locale, s1, s2 ) {
    if( s1 === s2 ) {
      return 0;
    }
    return _.isUndefined( s1 ) || _.isNull( s1 )
         ? -1
         : s1.localeCompare( s2, locale );
  }

  // Valid Finnish postal code has five digits
  function isValidZipCode( code ) {
    return /^\d{5}$/.test( code);
  }

  function isValidPhoneNumber( phoneNumber ) {
    return /^\+?[\d\s-]+$/.test( phoneNumber );
  }

  function isSupportedLang(lang) {
    return _.includes(LUPAPISTE.config.supportedLangs, lang);
  }

  // Trims and joins and trims.
  function nonBlankJoin( parts, sep ) {
    return _(parts)
      .reject( _.isBlank )
      .map( _.unary(_.trim )) // The second argument would mess up the trimming
      .join( sep )
      .trim();
  }

  function isObservableArray( obj ) {
    return ko.isObservable( obj )
      && !ko.isComputed( obj )
      && _.isFunction( obj.push );
  }

  function flipObservable( obs ) {
    obs( !obs() );
  }

  // KO validation utils. See ko.init.js and registration-models for
  // details and examples.

  function isValid( obs ) {
    // Non-validating observables are always valid.
    return obs.isValid ? obs.isValid() : true;
  }

  function isRequiredObservable( obs ) {
    // Observable is required if it is blank and not valid
    return _.isBlank( obs() ) && !isValid( obs );
  }

  function isNotValidObservable( obs ) {
    // Observable is not valid if it is neither valid NOR required.
    return !(isValid( obs ) || isRequiredObservable( obs ));
  }

  // Wrapper for the CLJC function
  function editDistance( a, b ) {
    return sade.shared_util.edit_distance(a, b);
  }

  // Convenience function for showing the integration error dialog
  // Options [optional]:
  // text/ltext/html/lhtml: Main error message. Only one should be given.
  // [ltitle/title]: Dialog title (default "integration.title")
  // [details]: Technical details, shown in a textarea.
  function showIntegrationError( options ) {
    var html = options.html;
    if( options.text || options.ltext ) {
      html = _.escapeHTML( options.ltext ? loc( options.ltext) : options.text );
    }
    if( options.lhtml ) {
      html = loc( options.html );
    }
    var title = options.ltitle ? loc( options.ltitle ) : options.title;
    var details = _.isBlank( options.details ) ? null : options.details;
    hub.send( "show-dialog", {
      component: "integration-error-dialog",
      title: title || loc( "integration.title"),
      size: details ? "medium" : "small",
      componentParams: {
        text: html,
        details: details
      }
    });
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
    isValidBSONObjectId: isValidBSONObjectId,
    isValidZipCode:      isValidZipCode,
    isValidPhoneNumber:  isValidPhoneNumber,
    lowerCase: function(s) {return _.isString(s) ? s.toLowerCase() : s;},
    upperCase: function(s) {return _.isString(s) ? s.toUpperCase() : s;},
    prop: {
      isPropertyId:           isPropertyId,
      isPropertyIdInDbFormat: isPropertyIdInDbFormat,
      toHumanFormat:          propertyIdWithMaaralaToHumanFormat,
      toDbFormat:             propertyIdToDbFormat,
      isPropertyIdWithMaaraala: isPropertyIdWithMaaraala,
      isPropertyIdWithMaaraalaInDbFormat: isPropertyIdWithMaaraalaInDbFormat,
      isPropertyIdWithMaaraalaInHumanFormat: isPropertyIdWithMaaraalaInHumanFormat,
      withMaaraalaToDbFormat: propertyIdWithMaaralaToDbFormat
    },
    buildingName: buildingName,
    constantly:   function(value) { return function() { return value; }; },
    autofocus:    autofocus,
    isNum:        isNum,
    formatPhoneNumber: formatPhoneNumber,
    getIn:        getIn,
    getFeatureName: getFeatureName,
    locKeyFromDocPath: locKeyFromDocPath,
    getDocumentOrder: getDocumentOrder,
    isPartyDoc: isPartyDoc,
    extractRequiredErrors: extractRequiredErrors,
    extractWarnErrors: extractWarnErrors,
    dissoc: dissoc,
    randomElementId: randomElementId,
    withSuffix: withSuffix,
    filterDataByQuery: filterDataByQuery,
    showSavedIndicator: showSavedIndicator,
    showSavedIndicatorIcon: showSavedIndicatorIcon,
    showErrorDialog: showErrorDialog,
    isNonNegative: isNonNegative,
    createSortableColumn: createSortableColumn,
    elementInViewport: elementInViewport,
    verdictsWithTasks: verdictsWithTasks,
    getPreviousState: getPreviousState,
    partyFullName: partyFullName,
    isEmpty: isEmpty,
    parseFloat: strictParseFloat,
    usagePurposeFromUrl: usagePurposeFromUrl,
    identLogoutUrl: identLogoutUrl,
    identLogoutRedirect: identLogoutRedirect,
    identLogoutRedirectBulletins: identLogoutRedirectBulletins,
    arrayToObject: arrayToObject,
    isSubObject: isSubObject,
    isOdd: isOdd,
    isEven: isEven,
    finnishDate: finnishDate,
    toMoment: toMoment,
    formatMoment: formatMoment,
    finnishDateAndTime: finnishDateAndTime,
    sizeString: sizeString,
    getSchemaElement: getSchemaElement,
    localeComparator: _.partial( localeCompare, loc.currentLanguage ),
    versionCompare: versionCompare,
    isSupportedLang: isSupportedLang,
    nonBlankJoin: nonBlankJoin,
    isObservableArray: isObservableArray,
    flipObservable: flipObservable,
    isRequiredObservable: isRequiredObservable,
    isNotValidObservable: isNotValidObservable,
    editDistance: editDistance,
    showIntegrationError: showIntegrationError
  };

})(jQuery);
