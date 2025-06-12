// See https://knockoutjs.com/documentation/component-loaders.html

var cljsLoader = {

  getConfig: function(name, callback) {
    "use strict";
    // Check if we are processing a "<cljs-foobar ..>" component
    // and not some other knockout managed component (e.g "<modal-dialog>", "<card>" ..)
    if (_.startsWith(name, "cljs-") && name !== "cljs-component") {
      // Drop the prefix: cljs-foo_bar-baz -> foo_bar-baz
      var cljsComponentName = _.join(_.drop(_.split(name,"-"),1), "-");
      // Underscores to dots: foo_bar-baz -> foo.bar-baz
      var cljsComponentPath = cljsComponentName.replace(/_/g, ".").replace(/-/g,"_");
      // snake case name: foo_bar-baz -> foo_bar_baz
      callback({cljsComponentName:  _.snakeCase(cljsComponentName),
                cljsComponentPath:  cljsComponentPath});
      // In other words, something like <cljs-admin_ad-login_ad-login-settings> becomes
      // componentName: admin_ad_login_ad_login_settings
      // componentPath: admin.ad-login.ad-login-settings

      // Notice how the componentPath matches the suffix of the cljs namespace
      // (lupapalvelu.ui.admin.ad-login.ad-login-settings)!
    } else {
      callback(null);
    }
  },
  loadComponent: function(name, componentConfig, callback) {
    "use strict";
    if (_.has(componentConfig, "cljsComponentName")) {
      // componentConfig is the config registered above with getConfig
      var cljsComponentName = _.get(componentConfig, "cljsComponentName");
      var cljsComponentPath = _.get(componentConfig, "cljsComponentPath");
      var elems = ko.utils.parseHtmlFragment("<div data-bind='attr: {id: elemId}'></div>");
      callback({template: elems,
                createViewModel: function(params /*, componentInfo */) {
                  // Create custom unique element id per instance of the component
                  var elemId = _.uniqueId(cljsComponentName);
                  var newParams = _.defaults(params, {elemId: elemId, componentPath: cljsComponentPath});
                  return new LUPAPISTE.CljsComponentModel(newParams);
                }});
    } else {
      callback(null);
    }
  }
};

// Register the CLJS loader as the highest priority loader
ko.components.loaders.unshift(cljsLoader);
