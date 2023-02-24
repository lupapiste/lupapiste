var cljsLoader = {

  getConfig: function(name, callback) {
    "use strict";
    if (_.startsWith(name, "cljs-") && name !== "cljs-component") {
      var cljsComponentName = _.join(_.drop(_.split(name,"-"),1), "-");
      var cljsComponentPath = cljsComponentName.replace(/_/g, ".").replace(/-/g,"_");
      callback({cljsComponentName:  _.snakeCase(cljsComponentName),
                cljsComponentPath:  cljsComponentPath});
    } else {
      callback(null);
    }
  },
  loadComponent: function(name, componentConfig, callback) {
    "use strict";
    if (_.has(componentConfig, "cljsComponentName")) {
      var cljsComponentName = _.get(componentConfig, "cljsComponentName");
      var cljsComponentPath = _.get(componentConfig, "cljsComponentPath");
      var elemId = _.uniqueId(cljsComponentName);
      var elems = ko.utils.parseHtmlFragment("<div id='" + elemId + "'></div>");
      callback({template: elems,
                createViewModel: function(params /*, componentInfo */) {
                  var newParams = _.defaults(params, {elemId: elemId, componentPath: cljsComponentPath});
                  return new LUPAPISTE.CljsComponentModel(newParams);
                }});
    } else {
      callback(null);
    }
  }
};


ko.components.loaders.unshift(cljsLoader);
