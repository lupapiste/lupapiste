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
    return _.join( _.map(p.slice(1), function(v) { return parseInt(v, 10); }), "-");
  }

  function propertyIdToDbFormat(id) {
    if (!id) { return null; }
    if (propertyIdDbFormat.test(id)) { return id; }
    if (!propertyIdHumanFormat.test(id)) { throw "Invalid property ID: " + id; }
    return _.join( _.map(_.zip([3, 3, 4, 4], id.split("-")), zp), "");
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

  function filterDataByQuery(options) {
    options = _.defaults(options, defaultOptions);
    return _.filter(options.data, function(item) {
      return _.reduce(options.query.split(" "), function(result, word) {
        var dataForLabel = ko.unwrap(item[options.label]);
        var isSelected = _.isArray(options.selected) ? _.some(options.selected, item) : options.selected === item;
        return !isSelected && dataForLabel !== undefined && _.includes(dataForLabel.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
  }

  function showSavedIndicator(response) {
    if (response.ok) {
      hub.send("indicator", {style: "positive"});
    } else {
      hub.send("indicator", {style: "negative", message: response.text});
    }
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

    return { click: sortable ? _.partial(sortBy, sortField) : _.noop,
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

      var lupamaaraukset = _(verdict.paatokset || []).map("lupamaaraykset").filter().value();

      if (lupamaaraukset.length === 0 && myTasks.length > 0) {
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

  function identLogoutUrl() {
    return util.getIn(LUPAPISTE.config, ["identMethods", "logoutUrl"]);
  }

  function identLogoutRedirect() {
    var url = identLogoutUrl();
    var suffix = "/app/" + loc.getCurrentLanguage() + "/welcome#!/welcome";
    if (url) {
      window.location = _.escape(url) + "?return=" + suffix;
    }
  }

  function identLogoutRedirectBulletins() {
    var url = identLogoutUrl();
    var suffix = "/app/" + loc.getCurrentLanguage() + "/bulletins";
    if (url) {
      window.location = _.escape(url) + "?return=" + suffix;
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

  return {
    zeropad:             zeropad,
    fluentify:           fluentify,
    getPwQuality:        getPwQuality,
    isValidEmailAddress: isValidEmailAddress,
    isValidPassword:     isValidPassword,
    isValidY:            isValidY,
    isValidOVT:          isValidOVT,
    isValidPersonId:     isValidPersonId,
    lowerCase: function(s) {return _.isString(s) ? s.toLowerCase() : s;},
    upperCase: function(s) {return _.isString(s) ? s.toUpperCase() : s;},
    prop: {
      isPropertyId:           isPropertyId,
      isPropertyIdInDbFormat: isPropertyIdInDbFormat,
      toHumanFormat:          propertyIdToHumanFormat,
      toDbFormat:             propertyIdToDbFormat
    },
    buildingName: buildingName,
    constantly:   function(value) { return function() { return value; }; },
    autofocus:    autofocus,
    isNum:        isNum,
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
    identLogoutUrl: identLogoutUrl,
    identLogoutRedirect: identLogoutRedirect,
    identLogoutRedirectBulletins: identLogoutRedirectBulletins,
    arrayToObject: arrayToObject,
    isSubObject: isSubObject,
    isOdd: isOdd,
    isEven: isEven
  };

})(jQuery);
