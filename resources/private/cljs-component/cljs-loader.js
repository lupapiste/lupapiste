var cljsLoader = {

  getConfig: function(name, callback) {
    "use strict";
    if (_.startsWith(name, "cljs-") && name !== "cljs-component") {
      var cljsComponentName = _.join(_.drop(_.split(name,"-"),1), "-");
      var elemId = _.uniqueId(_.snakeCase(cljsComponentName));
      callback({cljsComponentName:  _.snakeCase(cljsComponentName),
                template: "<div id='" + elemId + "'></div>"});
    } else {
      callback(null);
    }
  },
  loadComponent: function(name, componentConfig, callback) {
    "use strict";
    if (_.has(componentConfig, "cljsComponentName")) {
      var lupapalvelu;
      var cljsComponentName = _.get(componentConfig, "cljsComponentName");
      var elemId = _.uniqueId(cljsComponentName);
      var elems = ko.utils.parseHtmlFragment("<div id='" + elemId + "'></div>");
      var doCallback = function() {
        callback({template: elems,
                  createViewModel: function(params /*, componentInfo */) {
                    var newParams = _.defaults(params, {elemId: elemId, componentName: cljsComponentName});
                    return new LUPAPISTE.CljsComponentModel(newParams);
                  }});
      };
      if (typeof goog !== "undefined") { // If Closure dependencies are already loaded, initialize component
        doCallback();
      } else { // If not loaded, fetch cljs bundle and initialize after
        $.getScript(LUPAPISTE.config.rumURL, function() {
          doCallback();
        });
      }
    } else {
      callback(null);
    }
  }
};


ko.components.loaders.unshift(cljsLoader);
