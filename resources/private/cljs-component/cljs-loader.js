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

      var cljsComponentName = _.get(componentConfig, "cljsComponentName");
      var elemId = _.uniqueId(cljsComponentName);
      var component = lupapalvelu.ui[cljsComponentName]; // if Rum app not yet loaded, will be undefined
      var elems = ko.utils.parseHtmlFragment("<div id='" + elemId + "'></div>");
      var doCallback = function() {
        callback({template: elems,
                  createViewModel: function(params /*, componentInfo */) {
                    var newParams = _.defaults(params, {elemId: elemId, componentName: cljsComponentName});
                    return new LUPAPISTE.CljsComponentModel(newParams);
                  }});
      };
      if (component) { // If Rum already loaded, initialize component
        doCallback();
      } else { // If not loaded, fetch rum-app.js and initialize after
        $.getScript("/lp-static/js/rum-app.js", function() {
          doCallback();
        });
      }
    } else {
      callback(null);
    }
  }
};


ko.components.loaders.unshift(cljsLoader);
