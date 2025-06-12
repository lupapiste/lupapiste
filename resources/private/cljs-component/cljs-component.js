/**
 * Used for custom elements starting with <cljs*...
 * Inits the CLJS component by calling start-method with the elementId, where React component content will be injected.
 * Params given for cljs-* Knockout components will be passed as second parameter for CLJS component.
 * Thus CLJS component must implement start function, which is given two parameters: DOM element id and parameters passed to Knockout component.
 */
LUPAPISTE.CljsComponentModel = function(params) {
  "use strict";
  var self = this;
  // componentPath is the component namespace without the "lupapalvelu[.ui]" prefix
  self.componentPath = params.componentPath;

  // Insert the instance specific element ID into the model so that
  // Knockout can inject into the DOM for each instance
  self.elemId = params.elemId;

  // component.start should always return cleanupFn so we can unmount the React root
  self.cleanupFn = null;
  self.dispose = function() {
    if (self.cleanupFn) {
      self.cleanupFn();
    }
  };

  var component = (_.get(lupapalvelu.ui, self.componentPath) || _.get(lupapalvelu, self.componentPath));

  var devMode = LUPAPISTE.config.mode === "dev";

  // Component might not be found if e.g. if the javascript bundles don't contain
  // the correct cljs namespaces for this app
  if (component == null) {
    window.console.error("Component not found: " + self.componentPath);
  } else if (devMode) {
    var c = window.console;
    c.log("Loaded " + self.componentPath);
  }

  // Each cljs component is expected to define and export a "start" function
  // e.g. `lupapalvelu.ui.admin.ad-login.ad-login-settings/start`
  // Here we invoke that method to mount the component into the <div> created by the component loader

  // bind React component only after template has been injected
  _.defer(function() {
    self.cleanupFn = component.start(params.elemId, params);
  });
};
